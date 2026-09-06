package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.BoxScoreEntity;
import software.daveturner.gametime.entity.GameEventEntity;
import software.daveturner.gametime.entity.PlayType;
import software.daveturner.gametime.model.RosterEntry;
import software.daveturner.gametime.model.Team;
import software.daveturner.gametime.repo.BoxScoreRepo;
import software.daveturner.gametime.repo.GameEventRepo;
import software.daveturner.gametime.service.GametimeService;

import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * §3.4 calibration harness (decisions.md #022, Decision D = D1). Simulates N games
 * over the H2-seeded league and prints aggregate distributions so the {@code
 * SimConfig} constants can be tuned toward agreed real-basketball benchmarks.
 *
 * <p><b>Disabled by default</b> — it is an on-demand observation instrument, not a
 * pass/fail unit test, so it stays off the normal build + coverage gate. Run it
 * explicitly to observe distributions:
 * <pre>
 *   JAVA_HOME=.../21 mvn -f gametime-service/pom.xml test -pl gametime-app \
 *     -Dtest=CalibrationHarness -Dcalibration=true -DfailIfNoTests=false
 * </pre>
 * Without {@code -Dcalibration=true} the method is skipped, so it stays off the
 * normal build + coverage gate ({@code @EnabledIfSystemProperty}).
 *
 * <p><b>Target benchmarks — SOURCED 2026-08</b> (Basketball-Reference league averages,
 * per game, 2025-26 regular season; decisions.md #036). Per team per game:
 * <ul>
 *   <li>115.6 points</li>
 *   <li>47.1% FG</li>
 *   <li>36.0% 3P</li>
 *   <li>26.7 assists</li>
 *   <li>14.5 turnovers</li>
 * </ul>
 * These supersede the unsourced 2026-06-30 estimates (~112 / ~47 / ~36 / ~26 / ~14).
 * <b>docs/calibration.md is the source of truth — update it and these strings together.</b>
 * "Calibrated" means the aggregates below land near these, observed here — not by eye.
 *
 * <p><b>§3.5 additions (decisions.md #023, Decision E):</b> the report also shows a
 * <b>per-slot minutes distribution</b> (each team-game's box scores sorted by
 * minutes descending, averaged by slot — so slot 1 is the biggest-minutes player)
 * and a <b>period-by-period FG%</b> so the fatigue effect is visible (FG% should
 * sag in later periods as legs tire). §3.5 minutes targets (user-agreed): top
 * starter ~34–36, no one over ~42, benches scaled by rotationDepth. The §3.4
 * aggregates above must still hold with fatigue on.
 */
@SpringBootTest
@Transactional
class CalibrationHarness {

    // Injected, NOT SimConfig.baseline(): the report must describe the config the
    // engine actually ran, which is whatever the active profile list bound.
    @Autowired
    SimConfig config;

    // All 40 seeded teams carry exactly 5 starters (verified against roster.csv).
    private static final String[] TEAMS = {
            "NY", "PHI", "BRK", "BOS", "NC", "ATL", "MIA", "MI", "CHI", "IND",
            "MIN", "TOR", "BUF", "VA", "MIL", "PIT", "STL", "KC", "HOU", "SA",
            "DAL", "AL", "OKL", "DEN", "LA", "CA", "SD", "SF", "PHO", "POR",
            "SEA", "UT", "VAN", "LV"
    };

    // Pace comes from the active profile. A hardcoded literal here would shadow
    // sim.default-possessions-per-period and silently neutralize an era profile's
    // biggest lever, while every other value bound correctly.

    @Autowired
    GameSimulator simulator;

    @Autowired
    GameEventRepo gameEventRepo;

    @Autowired
    BoxScoreRepo boxScoreRepo;

    @Autowired
    GametimeService gametimeService;

    // The environment, not System.getProperty: both the command line and
    // application.properties feed it, and only it knows which won.
    @Autowired
    org.springframework.core.env.Environment environment;

    @Test
    @EnabledIfSystemProperty(named = "calibration", matches = "true")
    void reportLeagueAggregates() {
        // Pair adjacent teams into matchups, run several rounds (rotating the
        // pairing + seed) to reach ~100 games over distinct matchups.
        int rounds = 6;
        // §3.11 (#029 E): the seed base is overridable (-DcalibrationSeed=NNNN) so
        // the same configuration can be observed across SEVERAL independent runs
        // and tuned to the MEAN. A single run carries per-seed noise big enough to
        // bait an over-correction — the explicit discipline this pass adopted.
        long seed = Long.getLong("calibrationSeed", 1_000L);
        Agg agg = new Agg();
        int games = 0;

        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i + 1 < TEAMS.length; i += 2) {
                String home = TEAMS[(i + round) % TEAMS.length];
                String away = TEAMS[(i + 1 + round) % TEAMS.length];
                if (home.equals(away)) continue;
                accumulate(home, away, seed++, agg);
                games++;
            }
        }

        String activeProfiles = String.join(",", environment.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = "(none active — Spring defaults)";
        }
        agg.print(games, config, activeProfiles);
    }

    private void accumulate(String home, String away, long seed, Agg agg) {
        SimResult result = simulator.simulate(home, away, seed,
                config.defaultPossessionsPerPeriod());

        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());

        // Two team-games per game (home + away). Points come from the final score.
        agg.teamGames += 2;
        agg.points += result.getHomeScore() + result.getAwayScore();
        agg.periods += result.getPeriods();
        agg.gameCount++;

        for (BoxScoreEntity bs : boxScores) {
            agg.fga += nz(bs.getFieldGoalsAttempted());
            agg.fgm += nz(bs.getFieldGoalsMade());
            agg.tpa += nz(bs.getThreePointersAttempted());
            agg.tpm += nz(bs.getThreePointersMade());
            agg.assists += nz(bs.getAssists());
            agg.turnovers += nz(bs.getTurnovers());
            agg.offReb += nz(bs.getOffensiveRebounds());
            agg.defReb += nz(bs.getDefensiveRebounds());
            agg.blocks += nz(bs.getBlocks());
            agg.steals += nz(bs.getSteals());

            // §3.12 (#030 G): the FOUL-OUT instrument — genuinely new. The mechanism
            // has been live since §3.5 (FOUL_OUT_LIMIT = 6, hasFouledOut(), the
            // rotation forces the player off), but its RATE has never been observed
            // in any calibration run, so "are foul-outs realistic?" has had no
            // answer. §3.12 is the pass that moves it: widening contact to all shot
            // types raises fouls across all five defenders.
            //
            // The DISTRIBUTION matters more than the count — it shows pressure
            // building BELOW the threshold before it becomes visible above it. Only
            // players who actually appeared are counted (a DNP would otherwise pile
            // up a meaningless zero bucket).
            int pf = nz(bs.getFouls());
            if (nz(bs.getMinutes()) > 0) {
                agg.playersPlayed++;
                if (pf >= config.foulOutLimit()) agg.foulOuts++;
                if (pf == 4) agg.playersWithFourFouls++;
                if (pf == 5) agg.playersWithFiveFouls++;
            }
        }

        // §3.5: per-slot minutes distribution — for each team-game, sort that
        // team's box scores by minutes descending so slot 0 is the biggest-minutes
        // player, slot 1 next, etc. Accumulated by slot across all team-games.
        accumulateMinutesBySlot(home, boxScores, agg);
        accumulateMinutesBySlot(away, boxScores, agg);

        // §3.5: period-by-period FG% (from the event log) so fatigue's sag shows.
        for (GameEventEntity e : events) {
            if (e.getPlayType() == PlayType.SHOT) {
                int p = e.getPeriod();
                if (p >= 1 && p <= Agg.MAX_TRACKED_PERIODS) {
                    agg.periodFga[p - 1]++;
                    if (e.getOutcome() != null && e.getOutcome().startsWith("MADE")) {
                        agg.periodFgm[p - 1]++;
                    }
                }
            }
        }

        // Assist reconciliation sanity: assisted SHOT events == box-score assists.
        long assistedShots = events.stream()
                .filter(e -> e.getPlayType() == PlayType.SHOT && e.getAssistPlayerId() != null)
                .count();
        int boxAssists = boxScores.stream().mapToInt(b -> nz(b.getAssists())).sum();
        if (assistedShots != boxAssists) {
            agg.reconciliationMismatches++;
        }

        // §3.7 block reconciliation: SHOT events with a BLOCKED outcome == box-score
        // blocks (#025 F — events are the source of truth, same shape as assists).
        long blockedShots = events.stream()
                .filter(e -> e.getPlayType() == PlayType.SHOT
                        && e.getOutcome() != null && e.getOutcome().startsWith("BLOCKED"))
                .count();
        int boxBlocks = boxScores.stream().mapToInt(b -> nz(b.getBlocks())).sum();
        if (blockedShots != boxBlocks) {
            agg.reconciliationMismatches++;
        }

        // §3.19: FT-SOURCE reconciliation. The per-source FT split (#029 D) is read
        // off an outcome SUFFIX, so an untagged or mis-tagged free throw lands in the
        // UNKNOWN bucket and silently distorts the FT-source percentages §3.20 reads.
        // Nothing asserted that bucket was empty. The check is the same shape as the
        // two above — events are the source of truth — with two conditions: the
        // per-source counts must SUM to the total FREE_THROW events, and the UNKNOWN
        // bucket must be empty. A sum-only check would pass with every FT tagged
        // UNKNOWN, which is precisely the failure it exists to catch.
        long freeThrowEvents = 0;
        long taggedFreeThrows = 0;
        long unknownFreeThrows = 0;
        for (GameEventEntity e : events) {
            if (e.getPlayType() != PlayType.FREE_THROW) continue;
            freeThrowEvents++;
            if ("UNKNOWN".equals(freeThrowSource(e.getOutcome()))) {
                unknownFreeThrows++;
            } else {
                taggedFreeThrows++;
            }
        }
        if (unknownFreeThrows > 0 || taggedFreeThrows != freeThrowEvents) {
            agg.freeThrowSourceMismatches++;
        }

        // §3.19: POINTS reconciliation. Points is a headline §3.20 target and nothing
        // verified the harness computed it consistently — agg.points comes off the
        // final SCORE, while every other row is derived from the event log. Sum 2/3
        // per made SHOT and 1 per made FREE_THROW from the events and check that
        // against both the box-score total and the final score. All three must agree:
        // if they ever diverge, a target row is being read off a different quantity
        // than the play-by-play describes.
        long eventPoints = 0;
        for (GameEventEntity e : events) {
            String outcome = e.getOutcome();
            if (outcome == null || !outcome.startsWith("MADE")) continue;
            if (e.getPlayType() == PlayType.SHOT) {
                eventPoints += ShotType.THREE.name().equals(shotTypeSuffix(outcome))
                        ? ShotType.THREE.getPoints() : 2;
            } else if (e.getPlayType() == PlayType.FREE_THROW) {
                eventPoints++;
            }
        }
        int boxPoints = boxScores.stream().mapToInt(b -> nz(b.getPoints())).sum();
        int finalScore = result.getHomeScore() + result.getAwayScore();
        if (eventPoints != boxPoints || boxPoints != finalScore) {
            agg.pointsMismatches++;
        }

        // §3.21 (decisions.md #043 F): REBOUND-POOL reconciliation — every REBOUND event
        // that names a player is a box-score rebound, and every box-score rebound has an
        // event that names them. Same shape as the three checks above: events are the
        // source of truth.
        //
        // ⚠ The OUT_OF_BOUNDS_* outcomes are EXCLUDED BY DESIGN, and that is the whole
        // subtlety. They are REBOUND events with a null primary player because nobody
        // secured the ball — a possession change with no rebound, which is what a "team
        // rebound" actually means. ⚠ A team rebound is NEVER a bucket for rebounds whose
        // owner the engine failed to identify: EVERY ACTUAL REBOUND HAS AN OWNER, and
        // this line is what now proves it. Filtering on the null primary rather than on
        // the outcome string keeps the check honest if a future outcome is added.
        long ownedRebounds = events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && e.getPrimaryPlayerId() != null)
                .count();
        int boxRebounds = boxScores.stream()
                .mapToInt(b -> nz(b.getOffensiveRebounds()) + nz(b.getDefensiveRebounds()))
                .sum();
        if (ownedRebounds != boxRebounds) {
            agg.reboundPoolMismatches++;
        }

        // §3.8 (decisions.md #026 D): count OUT_OF_BOUNDS_* REBOUND events so the OOB
        // rate is VISIBLE (no hard target) — sail-out is a genuinely new outcome that
        // removes a rebound chance, so its rate must be watchable to confirm the
        // §3.4/§3.5 aggregates still hold with OOB on. Split over the two team-games
        // of this game, so the printed "/ team / game" divides by teamGames uniformly.
        agg.oob += events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && e.getOutcome() != null && e.getOutcome().startsWith("OUT_OF_BOUNDS"))
                .count();

        // §3.9 (decisions.md #027 E): tally each TURNOVER event by its outcome
        // string so the per-cause MIX is VISIBLE. The turnover COUNT is free by
        // construction (the gate is unchanged, Decision A) — this line does NOT
        // gate anything; it exists so a wildly-off share (e.g. OVER_AND_BACK at
        // 10%) is caught, and to sanity-check that STOLEN stays dominant.
        for (GameEventEntity e : events) {
            if (e.getPlayType() == PlayType.TURNOVER && e.getOutcome() != null) {
                agg.turnoverCauses.merge(e.getOutcome(), 1L, Long::sum);
            }
        }

        // §3.10 (decisions.md #028 E): §3.10 is NOT free — bonus FTs add points that
        // do not exist today — so the new FT source must be VISIBLE before any
        // "points re-centered" claim (#026 D "build the instrument"). Three things
        // are tracked: total fouls per team per period (is the penalty threshold
        // even reachable?), the offense/defense split of rebounding fouls (does the
        // defense-lean hold?), and how many FREE_THROW events came from a bonus
        // rebounding foul rather than a shooting foul (the actual scoring lift).
        accumulateFoulsAndFreeThrows(events, agg);
        accumulateEjections(events, agg);
    }

    /**
     * §3.14a (#032 F/J): count EJECTIONS — players who drew {@link
     * SimConfig#TECHNICAL_EJECTION_LIMIT} technicals in this game.
     *
     * <p>Derived from the event log rather than from the box score, because the
     * {@code technicalFouls} counter is deliberately not surfaced on {@code BoxScore}
     * (no consumer yet — #014/#017). The events are the source of truth anyway (#020).
     *
     * <p><b>Expect this to read 0.00, and that is a CORRECT result rather than a
     * failure.</b> At ~0.35 technicals per team-game spread over five players, two on
     * the same player in one game is on the order of one occurrence every several
     * simulated seasons. The rule's correctness rests on the forced-counter unit
     * tests, not on this line.
     *
     * <p><b>§3.14b (#034 F/H): flagrant-2 ejections join THIS line rather than starting
     * a third</b> — an ejection is an ejection, and the cause is recoverable from the
     * event log. Unlike the technical kind these actually fire: ~0.024 per team-game,
     * roughly double §3.14a's measured 0.014, so the seam §3.14a instrumented for them
     * is now genuinely exercised. (A flagrant-2 ejects on the FIRST one, so its
     * threshold is {@link SimConfig#FLAGRANT_EJECTION_LIMIT} = 1 — no per-player tally
     * is needed, each event is an ejection.)
     */
    private void accumulateEjections(List<GameEventEntity> events, Agg agg) {
        Map<String, Integer> technicalsByPlayer = new HashMap<>();
        for (GameEventEntity e : events) {
            if (e.getPlayType() != PlayType.FOUL || e.getPrimaryPlayerId() == null) {
                continue;
            }
            if (GameData.TECHNICAL_FOUL_OUTCOME.equals(e.getOutcome())) {
                technicalsByPlayer.merge(e.getPrimaryPlayerId(), 1, Integer::sum);
            } else if (PossessionEngine.FLAGRANT_FOUL_2_OUTCOME.equals(e.getOutcome())) {
                agg.ejections++;
            }
        }
        for (int count : technicalsByPlayer.values()) {
            if (count >= SimConfig.TECHNICAL_EJECTION_LIMIT) {
                agg.ejections++;
            }
        }
    }

    /**
     * §3.10 + §3.11: walk the event log once, tallying fouls by kind, the
     * per-team-period foul counts, and the FT split BY SOURCE.
     *
     * <p>§3.11 (#029 D) replaced the backward join this used to do — "a FREE_THROW
     * belongs to whichever FOUL most recently preceded it" — with a direct read of
     * the free throw's own outcome, now that every FT is self-describing
     * ({@code MADE_SHOOTING} / {@code MISSED_BONUS} / {@code MADE_AND_ONE}). That is
     * the point of the tag: the source is a fact on the event, not something
     * reconstructed by replaying the sequence. It also makes this line a self-check
     * on the tagging itself — an untagged or mis-tagged FT shows up as an UNKNOWN
     * bucket rather than being silently mis-attributed.
     */
    private void accumulateFoulsAndFreeThrows(List<GameEventEntity> events, Agg agg) {
        // (teamId, period) → fouls committed, so the per-period team-foul average
        // can be reported and the bonus reachability eyeballed.
        java.util.Map<String, Integer> foulsByTeamPeriod = new java.util.HashMap<>();

        for (GameEventEntity e : events) {
            if (e.getPlayType() == PlayType.SHOT && e.getOutcome() != null
                    && e.getOutcome().startsWith("MADE")) {
                // §3.12 (#030 D): the and-1 DENOMINATOR, widened from made
                // DRIVE/POST to ALL made FG. §3.11 gated the and-1 roll on
                // isContactType, so "% of made contact FG" was the right frame; #030
                // A1 deleted that gate, making the NUMERATOR count and-1s on all
                // four shot types. Leaving the old two-type denominator would
                // silently INFLATE the printed percentage — an instrument wrong in
                // the direction of the change it is measuring, which is worse than
                // no instrument at all.
                //
                // Consequence for cross-phase comparison: §3.11's shipped 6.4% was
                // measured against the narrower denominator and is NOT comparable to
                // the percentage printed now. Compare and-1s PER TEAM PER GAME
                // (§3.11 landed 1.67), which is denominator-independent.
                agg.madeFieldGoals++;
            } else if (e.getPlayType() == PlayType.FOUL) {
                agg.foulsByOutcome.merge(String.valueOf(e.getOutcome()), 1L, Long::sum);
                // §3.14a (#032 E/J): a TECHNICAL_FOUL carries committingTeamId like
                // every other FOUL event (#028 D), but it does NOT put a team in the
                // penalty — so it must be excluded from the per-team-period tally
                // here exactly as GameData.isInBonus excludes it. Counting it would
                // make the harness's own bonus-rate line disagree with the engine's
                // penalty derivation, i.e. an instrument wrong in the direction of
                // the change it is measuring.
                boolean technical = GameData.TECHNICAL_FOUL_OUTCOME.equals(e.getOutcome());
                if (technical) {
                    agg.technicalFouls++;
                }
                // §3.14b (#034 H/I): flagrants get their OWN tally, separate from
                // §3.14a's technicals — the two mechanics share nothing but the word
                // "foul" (#032 A) and cannot be judged at the same confidence.
                //
                // ⚠ NOTE WHAT IS *NOT* HERE, DELIBERATELY: a flagrant IS a personal
                // foul (#034 I), so unlike the technical above it is NOT excluded from
                // the per-team-period tally below. That non-change matters because the
                // bonus exclusion lives in TWO independent derivations — this tally and
                // GameData.isInBonus — and §3.14a's execution found that a foul type
                // handled in one but not the other makes the instrument silently
                // disagree with the engine. Both are unchanged here, and a test pins it.
                if (PossessionEngine.FLAGRANT_FOUL_1_OUTCOME.equals(e.getOutcome())
                        || PossessionEngine.FLAGRANT_FOUL_2_OUTCOME.equals(e.getOutcome())) {
                    agg.flagrantFouls++;
                    if (PossessionEngine.FLAGRANT_FOUL_2_OUTCOME.equals(e.getOutcome())) {
                        agg.flagrantTwos++;
                    }
                }
                if (e.getCommittingTeamId() != null && !technical) {
                    foulsByTeamPeriod.merge(
                            e.getCommittingTeamId() + "#" + e.getPeriod(), 1, Integer::sum);
                }
            } else if (e.getPlayType() == PlayType.FREE_THROW) {
                agg.freeThrows++;
                String source = freeThrowSource(e.getOutcome());
                agg.freeThrowsBySource.merge(source, 1L, Long::sum);
                if (e.getOutcome() != null && e.getOutcome().startsWith("MADE")) {
                    agg.freeThrowsMadeBySource.merge(source, 1L, Long::sum);
                }
            }
        }

        for (java.util.Map.Entry<String, Integer> entry : foulsByTeamPeriod.entrySet()) {
            agg.teamPeriodFouls += entry.getValue();
            agg.teamPeriods++;
            if (entry.getValue() >= config.bonusFoulsPerPeriod()) {
                agg.teamPeriodsInBonus++;
            }
        }

        accumulateFoulsByShotType(events, agg);
    }

    /**
     * §3.12 (#030 E/G): break the two foul channels down BY SHOT TYPE. Without this
     * the aggregate hides WHICH multiplier is wrong, and the per-type figures in
     * #030 are an analytic estimate over an ASSUMED shot mix, not a measurement.
     *
     * <p>The two channels need different derivations, because a stopped shot and a
     * made-and-fouled shot leave different traces:
     * <ul>
     *   <li><b>And-1s</b> ride a made shot, so the {@code AND_ONE} FOUL event is
     *       immediately preceded by its {@code MADE_<TYPE>} SHOT event — read the
     *       type off that.</li>
     *   <li><b>Stopped shots emit NO SHOT event at all</b> (the foul branch returns
     *       before {@code recordFieldGoalAttempt}), so there is nothing to read
     *       backward from. Instead the shot type is recovered from the <b>number of
     *       SHOOTING free throws</b> the foul awarded: a run of 3 is unambiguously a
     *       fouled THREE (#030 C), a run of 2 is one of the other three types. That
     *       is exactly the "count fouled-threes by their 3-FT trips" derivation the
     *       plan calls for — no new event field needed.</li>
     * </ul>
     *
     * <p>The 3-FT trip count is the number §3.12's THREE multiplier is set from, so
     * it gets its own line rather than being folded into an aggregate.
     */
    private void accumulateFoulsByShotType(List<GameEventEntity> events, Agg agg) {
        String lastMadeShotType = null;
        String pendingFoul = null;   // "SHOOTING" while its FT run is being counted
        int pendingFreeThrows = 0;

        for (GameEventEntity e : events) {
            String outcome = String.valueOf(e.getOutcome());

            if (e.getPlayType() == PlayType.SHOT) {
                if (outcome.startsWith("MADE")) {
                    lastMadeShotType = shotTypeSuffix(outcome);
                }
                // §3.17 (#040 C): every SHOT event is one CHARGED attempt and every
                // outcome vocabulary (MADE / MISSED / BLOCKED) carries the type suffix.
                agg.chargedByShotType.merge(shotTypeSuffix(outcome), 1L, Long::sum);
                flushStoppedShot(pendingFoul, pendingFreeThrows, agg);
                pendingFoul = null;
                pendingFreeThrows = 0;
            } else if (e.getPlayType() == PlayType.FOUL) {
                flushStoppedShot(pendingFoul, pendingFreeThrows, agg);
                pendingFoul = null;
                pendingFreeThrows = 0;

                if ("AND_ONE".equals(outcome) && lastMadeShotType != null) {
                    agg.andOnesByShotType.merge(lastMadeShotType, 1L, Long::sum);
                } else if ("SHOOTING_FOUL".equals(outcome)) {
                    pendingFoul = "SHOOTING";
                }
            } else if (e.getPlayType() == PlayType.FREE_THROW) {
                if (pendingFoul != null && "SHOOTING".equals(freeThrowSource(outcome))) {
                    pendingFreeThrows++;
                }
            }
        }
        flushStoppedShot(pendingFoul, pendingFreeThrows, agg);
    }

    /**
     * §3.12: classify a completed {@code SHOOTING_FOUL} by its free-throw run — 3 FTs
     * means the foul stopped a THREE, anything else a two-point attempt.
     *
     * <p><b>§3.17 Step 0 (decisions.md #040 G) — THE INSTRUMENT IS FIXED, BUT NOT THE
     * WAY THE PLAN EXPECTED, AND THE DIFFERENCE IS WORTH READING.</b> #040 G called for
     * "read the {@link ShotType} off the event". <b>That is not possible from the event
     * log</b>: a stopped shot emits <i>no</i> SHOT event (the foul branch returns before
     * {@code recordFieldGoalAttempt}), and neither {@code SHOOTING_FOUL} nor
     * {@code NON_SHOOTING_FOUL} carries a type suffix the way {@code MADE_*} /
     * {@code MISSED_*} / {@code BLOCKED_*} do. Adding one would be an <i>engine</i>
     * change to the permanent play-by-play vocabulary — outside a step #040 G scoped as
     * test-only, and outside the single rename #040 M authorises.
     *
     * <p><b>The correction applied instead is EXACT, not an estimate</b>, and it is
     * available because of how §3.16 built the composition roll:
     * {@link FoulResolver#isNonShootingFoul} is a <b>flat, shot-type-independent</b>
     * draw at {@code sim.non-shooting-foul-share} (#039 E deliberately refused to
     * skill-weight or type-weight it). So the fouls that stay {@code SHOOTING_FOUL} are
     * an <b>unbiased sample</b> of all stopped shots, taken at rate
     * {@code (1 - share)} — and the true count is the observed count divided by
     * {@code (1 - share)}. This method keeps counting the OBSERVED (visible) fouls; the
     * report applies the divisor once, at the point of print, so the raw tally stays
     * readable next to the corrected one.
     *
     * <p>The arithmetic that identifies the artifact is the same one that now undoes it:
     * at the shipped 0.50 share the instrument saw 1.50% of 3PA against a true ~3.0%,
     * and 1.50 = 3.0 × (1 − 0.50) to two decimals.
     *
     * <p>⚠ <b>Do NOT re-tune {@code sim.foul-mult-three} against these rows in §3.17</b>
     * (#030 G's fence, restated by #040 G). The rate is anchored on ~2% of 3PA and is
     * scale-free, so it stays correct as 3PA doubles.
     */
    private void flushStoppedShot(String pendingFoul, int freeThrowCount, Agg agg) {
        if (pendingFoul == null || freeThrowCount == 0) return;
        if (freeThrowCount >= 3) {
            agg.stoppedThrees++;
        } else {
            agg.stoppedTwos++;
        }
    }

    /**
     * The ShotType behind a shot outcome. The vocabulary is {@code MADE_2PT_DRIVE}
     * / {@code MADE_2PT_PERIMETER} / {@code MADE_2PT_POST} / {@code MADE_3PT} — note
     * the three-pointer carries NO type suffix, so it must be matched on the 3PT
     * marker rather than by splitting off a trailing word.
     */
    private String shotTypeSuffix(String outcome) {
        if (outcome.contains("3PT")) return ShotType.THREE.name();
        for (ShotType t : ShotType.values()) {
            if (outcome.endsWith("_" + t.name())) return t.name();
        }
        return "UNKNOWN";
    }

    /**
     * §3.11 (#029 D): the source half of a self-describing FT outcome
     * ({@code MADE_AND_ONE} → {@code AND_ONE}). Anything that does not parse is
     * bucketed as {@code UNKNOWN} rather than guessed at, so a missed tag is
     * VISIBLE on the harness line instead of quietly folding into a real source.
     */
    private String freeThrowSource(String outcome) {
        if (outcome == null) return "UNKNOWN";
        int split = outcome.indexOf('_');
        return split < 0 ? "UNKNOWN" : outcome.substring(split + 1);
    }

    /** Sort one team's box scores by minutes desc and add to the per-slot totals. */
    private void accumulateMinutesBySlot(String teamId, List<BoxScoreEntity> boxScores, Agg agg) {
        Set<String> teamPlayers = teamPlayerIds(teamId);
        List<BoxScoreEntity> teamBox = new ArrayList<>();
        for (BoxScoreEntity bs : boxScores) {
            if (teamPlayers.contains(bs.getPlayerId())) {
                teamBox.add(bs);
            }
        }
        teamBox.sort(Comparator.comparingInt((BoxScoreEntity b) -> nz(b.getMinutes())).reversed());
        for (int slot = 0; slot < teamBox.size() && slot < Agg.MAX_TRACKED_SLOTS; slot++) {
            agg.minutesBySlot[slot] += nz(teamBox.get(slot).getMinutes());
            agg.slotCounts[slot]++;
        }
    }

    private Set<String> teamPlayerIds(String teamId) {
        Team team = gametimeService.getTeam(teamId).orElseThrow();
        Set<String> ids = new HashSet<>();
        for (RosterEntry e : team.getPlayers()) {
            ids.add(e.getPlayer().getId());
        }
        return ids;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** Mutable accumulator over all simulated team-games. */
    private static final class Agg {
        static final int MAX_TRACKED_SLOTS = 15;   // deepest roster we report
        static final int MAX_TRACKED_PERIODS = 4;  // regulation periods for FG% sag

        int gameCount;
        int teamGames;
        long points;
        long fga, fgm, tpa, tpm, assists, turnovers, offReb, defReb, blocks, steals, oob;
        long periods;
        int reconciliationMismatches;
        // §3.19: the two checks that finish the harness's self-verification. Kept as
        // their OWN counters rather than folded into the line above so a failure names
        // WHICH instrument broke — the whole point is that a wrong reading does not
        // announce itself, and "1 MISMATCH(es)" on a shared counter would not say
        // whether the FT tags or the points arithmetic drifted.
        int freeThrowSourceMismatches;
        int pointsMismatches;
        // §3.21 (#043 F): the rebound-pool invariant. THE DURABLE INSTRUMENT THIS PASS
        // LEAVES — a rebound whose owner the engine fails to identify now fails the
        // harness, which is exactly the class of gap §3.21 existed to close and which
        // nothing was watching for. Its own counter, for the reason above.
        int reboundPoolMismatches;

        // §3.5 per-slot minutes (slot 0 = biggest-minutes player per team-game).
        final long[] minutesBySlot = new long[MAX_TRACKED_SLOTS];
        final int[] slotCounts = new int[MAX_TRACKED_SLOTS];
        // §3.5 period-by-period FG (regulation only).
        final long[] periodFga = new long[MAX_TRACKED_PERIODS];
        final long[] periodFgm = new long[MAX_TRACKED_PERIODS];
        // §3.9 per-cause turnover tally (outcome string → count), for the mix line.
        final java.util.Map<String, Long> turnoverCauses = new java.util.HashMap<>();

        // §3.10 (#028 E): fouls by kind, per-team-period foul load, and the
        // bonus-FT share — the instrument for the recalibration pass.
        final java.util.Map<String, Long> foulsByOutcome = new java.util.HashMap<>();
        long freeThrows;
        // §3.12 (#030 D): ALL made FG — the and-1 denominator, widened from §3.11's
        // made DRIVE/POST now that every shot type can draw one. §3.11's 6.4% was a
        // share of the NARROWER denominator and is not comparable; compare on
        // and-1s per team per game (1.67) instead.
        long madeFieldGoals;
        long teamPeriodFouls;
        int teamPeriods, teamPeriodsInBonus;

        // §3.12 (#030 G): the foul-out instrument — never measured before this pass.
        // Counted over players who actually appeared (minutes > 0).
        long playersPlayed, foulOuts, playersWithFourFouls, playersWithFiveFouls;

        // §3.14a (#032 J): the technicals instrument. THIS LINE IS LOAD-BEARING, not
        // decorative. §3.14a's points cost (+0.26/team/game) sits far BELOW the ±1.5
        // per-seed noise band, so the §3.4 aggregates physically cannot distinguish
        // "the technical roll is correct" from "the technical roll never fires" —
        // absence of movement is consistent with both. This count is the only thing
        // that tells them apart, which is why it was built before the rate was
        // confirmed rather than after.
        //
        // JUDGE IT AT 5 SEEDS ONLY (#032 J, computed before the rate was chosen): at
        // 102 games a 0.7/game league rate yields ~71 events, relative sd 11.8%; at 5
        // seeds ~357 events, 5.3%. A single-seed reading CANNOT resolve it.
        long technicalFouls;
        // Expect ~0.02 as of §3.14b — flagrant-2 ejections now join this line (#034 H).
        // The technical kind still contributes ~0.00; see accumulateEjections.
        long ejections;

        // §3.14b (#034 H): the flagrants instrument. LOAD-BEARING for the same reason
        // the technicals line is — §3.14b's points cost (+0.43/team/game, deliberately
        // over-estimated) sits far below the ±1.5 per-seed noise band, so the §3.4
        // aggregates physically cannot distinguish "the flagrant roll is correct" from
        // "the flagrant roll never fires". This count is the only thing that tells them
        // apart.
        //
        // ⚠ JUDGE AT 5 SEEDS ONLY, and even then coarsely (#034 H, computed before the
        // rate was chosen): at 102 games ~33 events, relative sd 17.4%; at 5 seeds ~166
        // events, 7.8%. That is MATERIALLY coarser than the technicals line (11.8% /
        // 5.3%) — THE COARSEST ROW IN calibration.md. A single-seed reading is useless,
        // and even the 5-seed mean can only confirm the right order of magnitude, not a
        // 10% tuning move.
        long flagrantFouls;
        // ~15% of the above (#034 E). Printed separately because it is the ejection
        // driver; expect ~5 events per 102-game run, i.e. unresolvable on its own.
        long flagrantTwos;

        // §3.12 (#030 E): the two foul channels broken down by shot type, so the
        // aggregate cannot hide WHICH multiplier is wrong. Stopped shots emit no
        // SHOT event, so they are classified by their FT run (3 ⇒ a fouled THREE).
        long stoppedThrees, stoppedTwos;

        /**
         * §3.17 (decisions.md #040 C/K): CHARGED field-goal attempts by {@link ShotType},
         * read off the SHOT event's outcome suffix ({@code MADE_* / MISSED_* / BLOCKED_*}
         * all carry it). This is the shot-mix line #040's follow-up list wanted and
         * §3.17 needed: without it the four {@code sim.shot-share-*} values are tuned
         * blind against 3PA alone, and a landing cannot be attributed to the share table
         * rather than to something else moving.
         *
         * <p>⚠ <b>Charged attempts are NOT draws.</b> A stopped shot charges no FGA, and
         * {@code sim.foul-mult-three} is 0.133 against DRIVE's 1.0, so the four types are
         * stopped at very different rates (#040 E). The report prints the CHARGED mix
         * (which is what 3PA/FGA is a share of) and, alongside it, the DRAW mix with the
         * corrected stopped-shot counts added back — the number the share table actually
         * sets. Only the two-point types share the stopped-two tally, so the draw mix
         * splits it across them in their charged proportion rather than inventing a
         * per-type figure the log cannot support.
         */
        final java.util.Map<String, Long> chargedByShotType = new java.util.HashMap<>();
        final java.util.Map<String, Long> andOnesByShotType = new java.util.HashMap<>();

        // §3.11 (#029 D/E): the FT split BY SOURCE, read straight off the
        // self-describing outcome. This is the instrument for §3.11's recalibration
        // (how big is the and-1 lift?) AND the self-check on the tag itself (an
        // UNKNOWN bucket means an FT was emitted without a source).
        final java.util.Map<String, Long> freeThrowsBySource = new java.util.HashMap<>();
        final java.util.Map<String, Long> freeThrowsMadeBySource = new java.util.HashMap<>();

        // Config is passed in: Agg is static, and a baseline() field would misreport
        // every era run.
        void print(int games, SimConfig config, String activeProfiles) {
            double tg = teamGames;
            List<String> lines = new ArrayList<>();
            lines.add(String.format("Games simulated:        %d (%d team-games)", gameCount, teamGames));
            lines.add(String.format("Avg periods/game:       %.2f", periods / (double) gameCount));
            // Targets are owned by docs/calibration.md — update BOTH together.
            // SOURCED 2026-08 from Basketball-Reference league averages, 2025-26
            // (decisions.md #036). FG% is NOT contested: real 47.1% vs the engine's
            // 46.9% is inside the +/-0.14 sd of the 5-seed mean, so the target was
            // wrong and no engine work is owed. Points retargeted ~112 -> 115.6; the
            // remaining +2.7 is largely three cancelling composition errors (2-pt
            // +10.9, 3-pt -18.0, FT +7.1). FTA and 3PA own phases: §3.16 and §3.17.
            // Targets are baseline-only: on an era profile they are suppressed in
            // favour of a delta. A delta is not a target, and nothing is tuned
            // against a non-baseline profile.
            boolean baselineRun = isBaselineOnly(activeProfiles);
            lines.add(numbered("Points / team / game:  ", points / tg, "%.1f",
                    "target 115.6 — sourced 2025-26, see calibration.md",
                    BASELINE_POINTS, baselineRun));
            lines.add(numbered("FG%:                   ", pct(fgm, fga), "%.1f%%",
                    "target 47.1% \u2014 sourced 2025-26. \u2705 \u00a73.20 LANDED IT"
                            + " (43.5 -> 47.0) via base-drive/base-post/base-perimeter; 2P% went"
                            + " 48.8 -> 55.0. base-three did NOT move (#040 F). \u26a0 The 2P"
                            + " bases pass through at ~0.89, NOT 1.0 (#042 impl note) \u2014 the"
                            + " base-to-realized wedge is MULTIPLICATIVE, so aim above the target"
                            + " and size the next step off the MEASURED pass-through",
                    BASELINE_FG_PCT, baselineRun));
            lines.add(numbered("3P%:                   ", pct(tpm, tpa), "%.1f%%",
                    "target 36.0% — sourced 2025-26", BASELINE_3P_PCT, baselineRun));
            lines.add(String.format("FGA / team / game:      %.1f   (target 89.1 — SOURCED"
                    + " 2025-26. \u2705 \u00a73.20 LANDED IT at 89.22 via sim.base-no-basket-"
                    + "foul 0.15 -> 0.1687, then \u00a73.21 re-landed it after the rebound-pool "
                    + "fix at 0.1753, then \u00a73.22 again after the putback at 0.178."
                    + " \u26a0 NOT via non-shooting-foul-share \u2014 that"
                    + " knob does not move FGA AT ALL (both foul branches return before an"
                    + " attempt is charged), disproving #042 B. \u26a0 IT IS BOUGHT AGAINST THE"
                    + " FOULS ROW: each extra foul costs 1.49 FGA, so the two are"
                    + " OVER-DETERMINED through one lever and landing fouls 19.9 would drop FGA"
                    + " to ~87.8. #039 C's dead-possession concession is NOT reopened."
                    + " See #042's implementation note, D6.)",
                    fga / tg));
            lines.add(String.format("3PA / team / game:      %.1f   (target 37.0 — SOURCED"
                    + " 2025-26. \u2705 \u00a73.17 LANDED IT: 19.96 -> 37.26 at 5 seeds, via the"
                    + " sim.shot-share-* table (#040 C/K). \u00a73.20 HELD IT at 37.20, bumping"
                    + " shot-share-three 1.23 -> 1.256 to offset the attempts its turnover rise"
                    + " removed \u2014 only that one value moves, #040 C)", tpa / tg));
            lines.add(numbered("Assists / team / game: ", assists / tg, "%.1f",
                    "target 26.7 — sourced 2025-26", BASELINE_ASSISTS, baselineRun));
            lines.add(numbered("Turnovers / team / game:", turnovers / tg, "%.1f",
                    "target 14.5 — sourced 2025-26", BASELINE_TURNOVERS, baselineRun));
            // §3.21 (#043 B): both rows carry a target now, and both are REPORTED
            // RESIDUALS. The note is on the OFF row so it is read once, not twice.
            lines.add(String.format("Off reb / team / game:  %.2f   (target ~11.3 — SOURCED"
                    + " 2025-26. ⚠ REPORTED RESIDUAL, NOT A MIS-TUNE, and"
                    + " sim.base-offensive-rebound CANNOT close it: THREE slices with THREE"
                    + " different offensive shares feed these two rows — the ordinary board"
                    + " 0.262 (the only knob), a blocked shot recovered in bounds 0.400 (the"
                    + " FLAT block-* weights, not a contest), and the free-throw board ~0.17"
                    + " (FREE_THROW_REBOUND_LEAN, a rule). The knob reaches only the first."
                    + " §3.21 closed the POOL 37.80 -> 43.72 against a real 43.70; what"
                    + " remains is the split. Do NOT re-weight block-* to chase it either."
                    + " See calibration.md, The rebound pool)", offReb / tg));
            lines.add(String.format("Def reb / team / game:  %.2f   (target ~32.4 — SOURCED"
                    + " 2025-26. Residual — see the Off reb note above)", defReb / tg));
            lines.add(String.format("Blocks / team / game:   %.1f   (target 4.8 — sourced"
                    + " 2025-26. \u26a0 \u00a73.17 PREDICTED THIS WOULD FALL TO ~2.6 AND IT"
                    + " DID NOT — it barely moved. PROB_FLOOR (0.02) is 4x base-block-three"
                    + " (0.005), so a three's block chance is FLOORED, not based. Shifting"
                    + " attempts to threes moves them 0.056 -> 0.02, not -> 0.005. A \u00a73.19"
                    + " finding: the four base-block-* cannot be reasoned about without the"
                    + " floor. See decisions.md #040's implementation note)", blocks / tg));
            // §3.18: the steals row. calibration.md carried steals as 8.4 observed
            // against an engine ~7.67 BY DERIVATION ONLY — never measured. This row is
            // the measurement (test-only, no engine change; a standing backlog chore).
            lines.add(String.format("Steals / team / game:   %.1f   (target 8.4 — sourced 2025-26."
                    + " \u26a0 DERIVED, NOT TUNED (#042 I): steals = turnovers x STOLEN share."
                    + " \u00a73.20 landed TO on 14.5 and steals followed to 8.10, reproducing"
                    + " the prediction to 0.05 with the share untouched. Do NOT chase 8.4 by"
                    + " moving to-weight-stolen \u2014 the nine cause weights are frozen, #027 A)", steals / tg));
            lines.add(String.format("OOB / team / game:      %.1f   (§3.8, no target)", oob / tg));
            lines.add(String.format("Reconciliation (ast+blk):%s",
                    reconciliationMismatches == 0 ? " OK (all games match)"
                            : " " + reconciliationMismatches + " MISMATCH(es)"));
            // §3.19: the two self-checks added to finish the mechanism. Same
            // OK / N MISMATCH(es) form, one line each so the failing instrument is
            // named. FT sources feed the per-source split below; points is a headline
            // §3.20 target read off the score rather than off the event log.
            lines.add(String.format("Reconciliation (ft-src):%s",
                    freeThrowSourceMismatches == 0 ? " OK (all FTs tagged, no UNKNOWN)"
                            : " " + freeThrowSourceMismatches + " MISMATCH(es)"));
            lines.add(String.format("Reconciliation (points): %s",
                    pointsMismatches == 0 ? "OK (events = box score = final)"
                            : pointsMismatches + " MISMATCH(es)"));
            // §3.21 (#043 F): the fourth line. OUT_OF_BOUNDS_* REBOUND events carry no
            // player and are excluded — they are possession changes where no rebound
            // happened, not rebounds with a missing owner.
            lines.add(String.format("Reconciliation (reb pool):%s",
                    reboundPoolMismatches == 0
                            ? " OK (owned REBOUND events = box score)"
                            : " " + reboundPoolMismatches + " MISMATCH(es)"));

            System.out.println();
            System.out.println("======== §3.4 + §3.5 + §3.7 CALIBRATION REPORT =========");
            System.out.printf("Profiles:               %s%s%n",
                    activeProfiles,
                    isBaselineOnly(activeProfiles) ? ""
                            : "   NON-BASELINE - targets suppressed");
            lines.forEach(System.out::println);

            // §3.5 minutes distribution (per team-game, biggest-minutes slot first).
            System.out.println("--- Minutes by rotation slot (avg per team-game) ---");
            System.out.println("    (§3.5 targets: top ~34–36, no one over ~42)");
            for (int slot = 0; slot < MAX_TRACKED_SLOTS; slot++) {
                if (slotCounts[slot] == 0) continue;
                System.out.printf("  slot %-2d: %5.1f min  (played in %d team-games)%n",
                        slot + 1, minutesBySlot[slot] / (double) slotCounts[slot], slotCounts[slot]);
            }

            // §3.5 period-by-period FG% (fatigue should sag it slightly late).
            System.out.println("--- FG% by period (fatigue sag check) ---");
            for (int p = 0; p < MAX_TRACKED_PERIODS; p++) {
                System.out.printf("  period %d: %.1f%%  (%d FGA)%n",
                        p + 1, pct(periodFgm[p], periodFga[p]), periodFga[p]);
            }

            // §3.9 (decisions.md #027 E) per-cause turnover mix. The COUNT is free by
            // construction; this shows the new DISTRIBUTION so no share is absurd and
            // STOLEN stays dominant (~55–60%). No per-cause target.
            long totalTO = turnoverCauses.values().stream().mapToLong(Long::longValue).sum();
            System.out.println("--- Turnover cause mix (§3.9, share of turnovers, no target) ---");
            for (TurnoverCause cause : TurnoverCause.values()) {
                long n = turnoverCauses.getOrDefault(cause.outcome(), 0L);
                System.out.printf("  %-30s %5.1f%%  (%d)%n",
                        cause.outcome(), totalTO == 0 ? 0.0 : 100.0 * n / totalTO, n);
            }

            // §3.10 (decisions.md #028 E) team fouls / bonus FTs. Unlike §3.8/§3.9
            // this pass is NOT free — bonus FTs are points that did not exist
            // before — so these lines bound the recalibration: the team-fouls/period
            // must be plausible (a handful, not 0 and not 20) for the penalty to
            // mean anything, and the bonus-FT share is the size of the scoring lift.
            System.out.println("--- Team fouls / bonus (§3.10) ---");
            System.out.printf("  Fouls / team / period:   %.2f   (plausible: a handful; bonus at %d)%n",
                    teamPeriods == 0 ? 0.0 : teamPeriodFouls / (double) teamPeriods,
                    config.bonusFoulsPerPeriod());
            System.out.printf("  Team-periods in bonus:   %.1f%%  (%d of %d)%n",
                    teamPeriods == 0 ? 0.0 : 100.0 * teamPeriodsInBonus / teamPeriods,
                    teamPeriodsInBonus, teamPeriods);
            long totalFouls = foulsByOutcome.values().stream().mapToLong(Long::longValue).sum();
            for (java.util.Map.Entry<String, Long> e : new java.util.TreeMap<>(foulsByOutcome).entrySet()) {
                System.out.printf("  %-28s %5.1f%%  (%.2f / team / game)%n",
                        e.getKey(), totalFouls == 0 ? 0.0 : 100.0 * e.getValue() / totalFouls,
                        e.getValue() / tg);
            }
            System.out.printf("  FTA / team / game:       %.1f   (target ~23.5 — SOURCED"
                            + " 2025-26. \u2705 \u00a73.20 LANDED IT at 23.08 via sim.non-"
                            + "shooting-foul-share 0.50 -> 0.3766, then \u00a73.21 -> 0.4123 to pull back "
                            + "the FTA its foul-rate rise added. \u26a0 TUNE THAT SHARE"
                            + " AGAINST THIS LINE, never against points. The response is DEAD"
                            + " LINEAR at -20.46 FTA per unit share, but the two FT SOURCES"
                            + " move in OPPOSITE directions (SHOOTING up, BONUS down), so net"
                            + " FTA rises by LESS than the SHOOTING leg \u2014 read the split"
                            + " below before adjusting. \u26a0 It does NOT move FGA: both foul"
                            + " branches return before an attempt is charged, #042 impl note)%n",
                    freeThrows / tg);

            // §3.11 (decisions.md #029 E) and-1 rate + FT-source split. §3.11 is a
            // PURE-ADDITIVE lift — an FT tacked onto a shot that already scored,
            // with no offsetting removal — so this block is what bounds its
            // recalibration: the and-1 conversion rate (how often a made contact
            // shot draws one) sizes the new source, and the per-source FT split
            // shows the lift against the two sources that already existed. The
            // split is read off the self-describing outcome (#029 D), so an
            // UNKNOWN row here means an FT was emitted without its source tag.
            System.out.println("--- And-1 / FT sources (§3.11) ---");
            System.out.printf("  Made FG / team:          %.1f   (the and-1 denominator)%n",
                    madeFieldGoals / tg);
            long andOnes = foulsByOutcome.getOrDefault("AND_ONE", 0L);
            // §3.12 (#030 D): the denominator is now ALL made FG, so this percentage
            // is NOT comparable to §3.11's shipped 6.4% (measured against made
            // DRIVE/POST only). Compare across the change on and-1s/team/game (1.67).
            System.out.printf("  And-1s / team / game:    %.2f   (%.1f%% of made FG; §3.11 shipped 1.67)%n",
                    andOnes / tg,
                    madeFieldGoals == 0 ? 0.0 : 100.0 * andOnes / madeFieldGoals);
            for (java.util.Map.Entry<String, Long> e
                    : new java.util.TreeMap<>(freeThrowsBySource).entrySet()) {
                long made = freeThrowsMadeBySource.getOrDefault(e.getKey(), 0L);
                System.out.printf("  FTA %-12s %5.1f%%  (%.2f / team / game; %.2f pts/team)%n",
                        e.getKey() + ":", freeThrows == 0 ? 0.0 : 100.0 * e.getValue() / freeThrows,
                        e.getValue() / tg, made / tg);
            }

            // §3.20 (#042 C): FT% — a row that was MEASURED BY NOTHING until this pass.
            // It reconciles off the per-source lines above (sum of made / sum of
            // attempted) and had been running 82.6% against a real ~78%, donating ~1.1
            // points/team/game that no target was watching. ⚠ THE GENERAL LESSON IS
            // WORTH MORE THAN THE ROW: an ABSENT row cannot be audited by
            // calibration.md's exit condition, which only asks that every row PRESENT
            // be sourced or deliberately observed/ballpark.
            long ftMadeAll = freeThrowsMadeBySource.values().stream()
                    .mapToLong(Long::longValue).sum();
            System.out.printf("  FT%%:                     %5.1f%%  (target ~78.0%% — SOURCED"
                            + " 2025-26. Tuned by sim.ft-base, which is 0.705 and is NOT the"
                            + " landing: realized FT%% is ftBase + 0.20 x (freeThrows - 10)/10"
                            + " and the roster's mean freeThrows is ~13.8. ⚠ A GLOBAL knob"
                            + " correcting a POPULATION effect — the roster's FT-skill"
                            + " distribution is the real cause, parked in ideas.md)%n",
                    freeThrows == 0 ? 0.0 : 100.0 * ftMadeAll / freeThrows);

            // §3.12 (decisions.md #030 E/G). Two new instruments plus the per-type
            // breakdown the multipliers are actually tuned against.
            //
            // BENCHMARKS ARE PLAUSIBILITY RANGES, NOT TARGETS — the §3.4 five
            // (points / FG% / 3P% / assists / turnovers) remain the only targets
            // (#030 G, and #017's don't-fabricate-a-constraint rule applied to
            // targets). Judge against them; do not calibrate to them.
            // docs/calibration.md carries all of these, targets and ballparks alike.
            System.out.println("--- All-shot-type contact fouls (§3.12) ---");
            // §3.17 Step 0 (#040 G): the visible SHOOTING_FOULs are an UNBIASED SAMPLE
            // of all stopped shots, taken at rate (1 - sim.non-shooting-foul-share),
            // because §3.16's composition roll is flat and type-independent (#039 E).
            // Dividing by the visible fraction recovers the true count exactly. The
            // divisor is read from the ACTIVE config, so an era profile that moves the
            // share keeps the instrument honest. See flushStoppedShot.
            double visibleFraction = 1.0 - config.nonShootingFoulShare();
            double stoppedThreesTrue = visibleFraction <= 0
                    ? Double.NaN : stoppedThrees / visibleFraction;
            double stoppedTwosTrue = visibleFraction <= 0
                    ? Double.NaN : stoppedTwos / visibleFraction;
            double totalStoppedTrue = stoppedThreesTrue + stoppedTwosTrue;
            long totalStopped = stoppedThrees + stoppedTwos;
            System.out.printf("  Stopped shots / team:    %.2f   (%.2f two-pt + %.2f three-pt)"
                            + "   [CORRECTED for the %.0f%% invisible to the FT run —"
                            + " raw visible %.2f (%.2f + %.2f)]%n",
                    totalStoppedTrue / tg, stoppedTwosTrue / tg, stoppedThreesTrue / tg,
                    100.0 * config.nonShootingFoulShare(),
                    totalStopped / tg, stoppedTwos / tg, stoppedThrees / tg);
            // THE number the THREE multiplier is set from. Anchor on the RATE (~2% of
            // 3PA), NOT the count: this engine shoots ~20 3PA/team/game against the
            // NBA's ~35, so the same rate necessarily yields fewer trips than the
            // real-league ~0.7. "Fixing" 0.40 up to 0.7 would silently undo #030 G.
            System.out.printf("  3-FT trips / team / game:%.2f   (%.2f%% of 3PA — the RATE is the"
                            + " anchor, ~2%%. CORRECTED for \u00a73.16's invisible share"
                            + " (\u00a73.17 Step 0, #040 G): the FT run cannot see a stopped shot"
                            + " whose foul became NON_SHOOTING_FOUL, so the visible tally is"
                            + " divided by (1 - non-shooting-foul-share). Raw visible: %.2f"
                            + " (%.2f%% of 3PA). \u26a0 Do NOT re-tune foul-mult-three against"
                            + " this \u2014 scale-free by construction, #030 G / #040 G)%n",
                    stoppedThreesTrue / tg,
                    tpa == 0 ? 0.0 : 100.0 * stoppedThreesTrue / tpa,
                    stoppedThrees / tg,
                    tpa == 0 ? 0.0 : 100.0 * stoppedThrees / tpa);
            // §3.17 (decisions.md #040 C/K): THE SHOT-MIX LINE. #040's follow-up list
            // called this a backlog chore; §3.17 needs it, because the four
            // sim.shot-share-* values cannot be attributed from 3PA alone. Two mixes,
            // and the distinction is #040 E's central finding:
            //   CHARGED — the share of FGA, which is what the 3PA/FGA target reads.
            //   DRAW    — the share the share table actually sets, i.e. charged plus
            //             the stopped shots that charged no FGA. Threes are stopped
            //             7.5x less often than drives (foul-mult-three 0.133 vs 1.0),
            //             so the three DRAW share is necessarily LOWER than its CHARGED
            //             share. That gap is exactly why the share table's three value
            //             must exceed the target share of attempts.
            // The stopped-TWO tally is not resolvable per type from the event log (a
            // stopped shot emits no SHOT event), so it is apportioned across the three
            // two-point types in their CHARGED proportion. That is an approximation and
            // is labelled as one; the THREE row, which is the one being tuned, is exact.
            long chargedTotal = chargedByShotType.values().stream()
                    .mapToLong(Long::longValue).sum();
            long chargedTwos = chargedTotal
                    - chargedByShotType.getOrDefault(ShotType.THREE.name(), 0L);
            double drawTotal = chargedTotal + totalStoppedTrue;
            System.out.println("--- Shot-type mix (§3.17, #040 C — CHARGED vs DRAW) ---");
            for (ShotType t : ShotType.values()) {
                long charged = chargedByShotType.getOrDefault(t.name(), 0L);
                double stoppedForType = t == ShotType.THREE
                        ? stoppedThreesTrue
                        : (chargedTwos == 0 ? 0.0 : stoppedTwosTrue * charged / chargedTwos);
                double draws = charged + stoppedForType;
                System.out.printf("  %-10s charged %5.1f%%  (%5.2f / team / game)"
                                + "   draw %5.1f%%  (%5.2f)%n",
                        t.name(),
                        chargedTotal == 0 ? 0.0 : 100.0 * charged / chargedTotal,
                        charged / tg,
                        drawTotal == 0 ? 0.0 : 100.0 * draws / drawTotal,
                        draws / tg);
            }
            System.out.printf("  (THREE charged share is the 3PA/FGA number: target 41.5%%"
                            + " of attempts \u2014 3PA 37.0 of FGA 89.1, sourced 2025-26."
                            + " The two-point DRAW rows are apportioned, not measured.)%n");

            System.out.print("  And-1s by shot type:    ");
            for (ShotType t : ShotType.values()) {
                System.out.printf(" %s %.2f", t.name().charAt(0) + t.name().substring(1, 3).toLowerCase(),
                        andOnesByShotType.getOrDefault(t.name(), 0L) / tg);
            }
            System.out.println("   (per team / game)");
            // ⚠ THIS IS A TALLY OVER ALL FOUL EVENTS, SO IT IS NOT COMPARABLE ACROSS
            // PHASES WITHOUT SUBTRACTING. §3.13 measured 19.0 here on personal fouls
            // alone; §3.14a added technicals (~0.37/team/game), taking the same engine
            // to ~19.4; §3.14b now adds flagrants (~0.16) on top.
            //
            // The two additions differ in kind, and the difference matters when reading
            // this line:
            //  - a TECHNICAL is a new event that is NOT a personal foul (#032 E), so it
            //    inflates this total without touching getFouls();
            //  - a FLAGRANT *IS* a personal foul (#034 I) and rides a foul that already
            //    happened — it REPLACES that foul's event rather than adding one, so it
            //    does NOT inflate this total at all. It is counted in the flagrants
            //    line below purely for visibility.
            // So to compare against §3.11/§3.13's figures, subtract the technicals line
            // only. getFouls() itself remains untouched by §3.14a, which the box-score
            // reconciliation in GameSimulatorIntegrationTest pins.
            System.out.printf("  Fouls / team / game:     %.2f   (target ~19.9 — SOURCED"
                            + " 2025-26. ALL foul events incl. §3.14a technicals;"
                            + " flagrants REPLACE a foul event so add nothing here."
                            + " §3.16's charge fix added ~1.2 — charges are personal"
                            + " fouls (#039 G); its NON_SHOOTING_FOUL re-partition adds NOTHING,"
                            + " by construction. \u26a0 \u00a73.17 took it 20.53 -> 18.18 WITHOUT"
                            + " TOUCHING A FOUL CONSTANT — the shot mix moved draws from"
                            + " foul-mult 1.0 to 0.133. \u00a73.20 took it 18.10 -> 18.84 via"
                            + " sim.base-no-basket-foul, closing 40%% of the gap. \u26a0 IT"
                            + " CANNOT CLOSE FURTHER WITHOUT UN-LANDING FGA \u2014 each extra"
                            + " foul costs 1.49 FGA and FGA is now in band, so the pair is"
                            + " OVER-DETERMINED through one lever (#042 D6). A design pass must"
                            + " pick which yields. PERSONAL_FOULS_PER_TEAM_GAME re-measured to"
                            + " 18.52, #034 G)%n",
                    totalFouls / tg);
            // §3.12's genuinely NEW instrument (#030 G): the foul-out mechanism has
            // been live since §3.5 but its rate has NEVER been observed. The
            // DISTRIBUTION matters more than the count — it shows pressure building
            // below the threshold before it crosses it.
            System.out.printf("  Foul-outs / team / game: %.3f  (BALLPARK ~0.1-0.25 real;"
                            + " \u26a0 NOT A TARGET since \u00a73.20 (#042 J) \u2014 the old"
                            + " ~0.39 was a LANDING promoted to a target, i.e. circular, and"
                            + " real basketball sits BELOW where the engine does. Judge by the"
                            + " 4/5/6 distribution below, not this count. See calibration.md."
                            + " \u00a73.16 raised it to ~0.52: charges now count, #039 G. \u00a73.17's lower foul rate took it"
                            + " back DOWN to ~0.31 — a by-product of the shot mix, not a"
                            + " re-tune; the \u00a73.13 sit curve is untouched and measured"
                            + " saturated, #031)%n",
                    foulOuts / tg);
            System.out.printf("  Players at 4 / 5 / 6 fouls per team/game: %.2f / %.2f / %.2f"
                            + "   (of %.1f who played)%n",
                    playersWithFourFouls / tg, playersWithFiveFouls / tg, foulOuts / tg,
                    playersPlayed / tg);

            // §3.14a (decisions.md #032 J). A BALLPARK, not a TARGET — nothing in the
            // engine is tuned toward it: config.technicalFoulsPerTeamGame() is
            // set from the real-world figure directly, so this line is a CORRECTNESS
            // CHECK that the roll fires at the rate configured, not a calibration
            // objective. calibration.md carries the row and owns the number.
            //
            // Two things that look wrong and are not:
            //  (1) A few percent BELOW the configured 0.35 is EXPECTED, not drift.
            //      The per-check probability divides by a NOMINAL possession count
            //      while the real count is pace-scaled and OT-extended (#032 B2) —
            //      the direction depends on the seeds' pace mix. Do NOT back-solve
            //      the constant against this line; that is chasing noise.
            //  (2) Ejections read 0.00 (#032 F). Two technicals on one player in one
            //      game is ~one occurrence every several simulated seasons. The rule
            //      is right; the event is rare. It is asserted by forced-counter unit
            //      tests, not here.
            System.out.println("--- Technical fouls (§3.14a) ---");
            System.out.printf("  Technicals / team / game:%.3f  (ballpark ~0.3-0.4 per team /"
                            + " ~0.6-0.8 league-wide; NOT a target — see calibration.md."
                            + " JUDGE AT 5 SEEDS ONLY)%n",
                    technicalFouls / tg);
            System.out.printf("  Ejections / team / game: %.3f  (BOTH causes since §3.14b:"
                            + " 2 technicals (~0.00) + any flagrant-2 (~0.02); #032 F / #034 F)%n",
                    ejections / tg);

            // §3.14b (decisions.md #034 H). ITS OWN SECTION, deliberately not shared
            // with §3.14a's technicals above: the two mechanics share nothing but the
            // word "foul" (#032 A), and they cannot even be judged at the same
            // confidence — sharing a line would make neither readable.
            //
            // A BALLPARK, not a TARGET — nothing is tuned toward it:
            // config.flagrantFoulsPerTeamGame() is set from the real-world figure
            // directly, so this line is a CORRECTNESS CHECK that the roll fires at the
            // rate configured, not a calibration objective. calibration.md owns the row.
            //
            // Three things that look wrong and are not:
            //  (1) THE COARSEST LINE IN THE HARNESS. 17.4% relative sd at 102 games,
            //      7.8% at 5 seeds. A single-seed reading CANNOT resolve it; do not
            //      tune against one run, and do not read a 10% move as signal.
            //  (2) The flagrant-2 sub-line is ~5 events per run. It is printed because
            //      it drives the ejections above, NOT because it can be tuned.
            //  (3) The rate is coupled to the EMERGENT foul rate (#034 G): flagrants
            //      are derived by dividing by SimConfig.PERSONAL_FOULS_PER_TEAM_GAME, a
            //      MEASURED figure. §3.16 — or any pass that moves the foul rate —
            //      moves this line without touching the flagrant constant.
            System.out.println("--- Flagrant fouls (§3.14b) ---");
            System.out.printf("  Flagrants / team / game: %.3f  (ballpark ~0.13-0.20 per team"
                            + " / ~0.25-0.40 league-wide; NOT a target — see calibration.md."
                            + " 5 SEEDS ONLY — the coarsest row there)%n",
                    flagrantFouls / tg);
            System.out.printf("  Flagrant-2s / team/game: %.3f  (~15%% of the above — the"
                            + " ejection driver; ~5 events per run, NOT independently tunable)%n",
                    flagrantTwos / tg);

            printEffectiveConfig(config, activeProfiles);
            System.out.println("========================================================");
            System.out.println();
        }


        // The shipped baseline landing (5-seed mean), held fixed so era deltas have a
        // stable reference. A reference point, not a target - the targets are in
        // docs/calibration.md and apply to baseline only.
        private static final double BASELINE_POINTS = 118.3;
        private static final double BASELINE_FG_PCT = 46.9;
        private static final double BASELINE_3P_PCT = 36.7;
        private static final double BASELINE_ASSISTS = 27.1;
        private static final double BASELINE_TURNOVERS = 13.6;

        /**
         * True on the plain baseline profile - the only one any target applies to.
         */
        private static boolean isBaselineOnly(String activeProfiles) {
            for (String profile : activeProfiles.split(",")) {
                String name = profile.trim();
                if (!name.isEmpty() && !name.equals("baseline")
                        && !name.equals("test") && !name.equals("local")) {
                    return false;
                }
            }
            return true;
        }

        /**
         * One report line: the target on a baseline run, a delta on an era run.
         */
        private static String numbered(String label, double value, String valueFormat,
                                       String target, double baselineValue,
                                       boolean baselineRun) {
            String v = String.format(valueFormat, value);
            String note = baselineRun
                    ? "(" + target + ")"
                    : String.format("(baseline %.1f, %+.1f)", baselineValue,
                            value - baselineValue);
            return String.format("%-24s%-7s %s", label, v, note);
        }

        /**
         * Every tunable constant and the value this run actually used.
         *
         * <p>This is the only thing that catches a mis-ordered profile list: putting
         * baseline last lets it win, so an era file silently does nothing and the
         * aggregates just look like baseline. It also catches a caller passing its
         * own copy of a tunable value.
         */
        private static void printEffectiveConfig(SimConfig config, String activeProfiles) {
            System.out.println("--- Effective sim config ---");
            System.out.printf("  active profiles: %s%n", activeProfiles);
            java.util.List<java.lang.reflect.Field> fields =
                    new java.util.ArrayList<>();
            for (java.lang.reflect.Field f : SimConfig.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    fields.add(f);
                }
            }
            fields.sort(java.util.Comparator.comparing(java.lang.reflect.Field::getName));
            for (java.lang.reflect.Field f : fields) {
                f.setAccessible(true);
                try {
                    Object v = f.get(config);
                    String shown = (v instanceof double[] arr)
                            ? java.util.Arrays.toString(arr) : String.valueOf(v);
                    System.out.printf("  %-42s %s%n", kebab(f.getName()), shown);
                } catch (IllegalAccessException e) {
                    System.out.printf("  %-42s (unreadable)%n", kebab(f.getName()));
                }
            }
        }

        /** camelCase field name to the kebab-case property key it binds from. */
        private static String kebab(String name) {
            StringBuilder sb = new StringBuilder("sim.");
            for (char c : name.toCharArray()) {
                if (Character.isUpperCase(c)) {
                    sb.append('-').append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private static double pct(long made, long attempted) {
            return attempted == 0 ? 0.0 : 100.0 * made / attempted;
        }
    }
}
