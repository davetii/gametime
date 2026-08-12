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
 * <p><b>Target benchmarks (modern NBA, per team per game — user-agreed 2026-06-30):</b>
 * <ul>
 *   <li>~112 points</li>
 *   <li>~47% FG</li>
 *   <li>~36% 3P</li>
 *   <li>~26 assists</li>
 *   <li>~14 turnovers</li>
 * </ul>
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

    // All 40 seeded teams carry exactly 5 starters (verified against roster.csv).
    private static final String[] TEAMS = {
            "NY", "PHI", "BRK", "BOS", "NC", "ATL", "MIA", "MI", "CHI", "IND",
            "MIN", "TOR", "BUF", "VA", "MIL", "PIT", "STL", "KC", "HOU", "SA",
            "DAL", "AL", "OKL", "DEN", "LA", "CA", "SD", "SF", "PHO", "POR",
            "SEA", "UT", "VAN", "LV"
    };

    private static final int POSSESSIONS_PER_PERIOD = 25;

    @Autowired
    GameSimulator simulator;

    @Autowired
    GameEventRepo gameEventRepo;

    @Autowired
    BoxScoreRepo boxScoreRepo;

    @Autowired
    GametimeService gametimeService;

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

        agg.print(games);
    }

    private void accumulate(String home, String away, long seed, Agg agg) {
        SimResult result = simulator.simulate(home, away, seed, POSSESSIONS_PER_PERIOD);

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
                if (pf >= SimConfig.FOUL_OUT_LIMIT) agg.foulOuts++;
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
            if (entry.getValue() >= SimConfig.BONUS_FOULS_PER_PERIOD) {
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
     * §3.12: classify a completed SHOOTING_FOUL by its free-throw run — 3 FTs means
     * the foul stopped a THREE, anything else a two-point attempt.
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
        long fga, fgm, tpa, tpm, assists, turnovers, offReb, defReb, blocks, oob;
        long periods;
        int reconciliationMismatches;

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
        final java.util.Map<String, Long> andOnesByShotType = new java.util.HashMap<>();

        // §3.11 (#029 D/E): the FT split BY SOURCE, read straight off the
        // self-describing outcome. This is the instrument for §3.11's recalibration
        // (how big is the and-1 lift?) AND the self-check on the tag itself (an
        // UNKNOWN bucket means an FT was emitted without a source).
        final java.util.Map<String, Long> freeThrowsBySource = new java.util.HashMap<>();
        final java.util.Map<String, Long> freeThrowsMadeBySource = new java.util.HashMap<>();

        void print(int games) {
            double tg = teamGames;
            List<String> lines = new ArrayList<>();
            lines.add(String.format("Games simulated:        %d (%d team-games)", gameCount, teamGames));
            lines.add(String.format("Avg periods/game:       %.2f", periods / (double) gameCount));
            // Targets are owned by docs/calibration.md — update BOTH together.
            // Points and FG% are flagged CONTESTED there (2026-08, §3.12): both were
            // set in §3.4 from unsourced estimates, current figures suggest points
            // ~114-117 and FG% ~47-48, and the one lever that moves them (shot
            // BASE_*) moves BOTH the same direction — so they cannot be reconciled
            // by a re-centering step. Read that file before trimming anything.
            lines.add(String.format("Points / team / game:   %.1f   (target ~112 — CONTESTED, see calibration.md)", points / tg));
            lines.add(String.format("FG%%:                    %.1f%%  (target ~47%% — CONTESTED, see calibration.md)", pct(fgm, fga)));
            lines.add(String.format("3P%%:                    %.1f%%  (target ~36%%)", pct(tpm, tpa)));
            lines.add(String.format("FGA / team / game:      %.1f", fga / tg));
            lines.add(String.format("3PA / team / game:      %.1f", tpa / tg));
            lines.add(String.format("Assists / team / game:  %.1f   (target ~26)", assists / tg));
            lines.add(String.format("Turnovers / team / game:%.1f   (target ~14)", turnovers / tg));
            lines.add(String.format("Off reb / team / game:  %.1f", offReb / tg));
            lines.add(String.format("Def reb / team / game:  %.1f", defReb / tg));
            lines.add(String.format("Blocks / team / game:   %.1f   (target ~5)", blocks / tg));
            lines.add(String.format("OOB / team / game:      %.1f   (§3.8, no target)", oob / tg));
            lines.add(String.format("Reconciliation (ast+blk):%s",
                    reconciliationMismatches == 0 ? " OK (all games match)"
                            : " " + reconciliationMismatches + " MISMATCH(es)"));

            System.out.println();
            System.out.println("======== §3.4 + §3.5 + §3.7 CALIBRATION REPORT =========");
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
                    SimConfig.BONUS_FOULS_PER_PERIOD);
            System.out.printf("  Team-periods in bonus:   %.1f%%  (%d of %d)%n",
                    teamPeriods == 0 ? 0.0 : 100.0 * teamPeriodsInBonus / teamPeriods,
                    teamPeriodsInBonus, teamPeriods);
            long totalFouls = foulsByOutcome.values().stream().mapToLong(Long::longValue).sum();
            for (java.util.Map.Entry<String, Long> e : new java.util.TreeMap<>(foulsByOutcome).entrySet()) {
                System.out.printf("  %-28s %5.1f%%  (%.2f / team / game)%n",
                        e.getKey(), totalFouls == 0 ? 0.0 : 100.0 * e.getValue() / totalFouls,
                        e.getValue() / tg);
            }
            System.out.printf("  FTA / team / game:       %.1f%n", freeThrows / tg);

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

            // §3.12 (decisions.md #030 E/G). Two new instruments plus the per-type
            // breakdown the multipliers are actually tuned against.
            //
            // BENCHMARKS ARE PLAUSIBILITY RANGES, NOT TARGETS — the §3.4 five
            // (points / FG% / 3P% / assists / turnovers) remain the only targets
            // (#030 G, and #017's don't-fabricate-a-constraint rule applied to
            // targets). Judge against them; do not calibrate to them.
            // docs/calibration.md carries all of these, targets and ballparks alike.
            System.out.println("--- All-shot-type contact fouls (§3.12) ---");
            long totalStopped = stoppedThrees + stoppedTwos;
            System.out.printf("  Stopped shots / team:    %.2f   (%.2f two-pt + %.2f three-pt)%n",
                    totalStopped / tg, stoppedTwos / tg, stoppedThrees / tg);
            // THE number the THREE multiplier is set from. Anchor on the RATE (~2% of
            // 3PA), NOT the count: this engine shoots ~20 3PA/team/game against the
            // NBA's ~35, so the same rate necessarily yields fewer trips than the
            // real-league ~0.7. "Fixing" 0.40 up to 0.7 would silently undo #030 G.
            System.out.printf("  3-FT trips / team / game:%.2f   (%.2f%% of 3PA — the RATE is the anchor,"
                            + " ~2%%; expect ~0.3-0.6 trips, NOT the NBA's 0.7)%n",
                    stoppedThrees / tg, tpa == 0 ? 0.0 : 100.0 * stoppedThrees / tpa);
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
            System.out.printf("  Fouls / team / game:     %.2f   (ALL foul events incl."
                            + " §3.14a technicals; flagrants REPLACE a foul event so add"
                            + " nothing here; plausible ~19-20; §3.11 measured 16.8)%n",
                    totalFouls / tg);
            // §3.12's genuinely NEW instrument (#030 G): the foul-out mechanism has
            // been live since §3.5 but its rate has NEVER been observed. The
            // DISTRIBUTION matters more than the count — it shows pressure building
            // below the threshold before it crosses it.
            System.out.printf("  Foul-outs / team / game: %.3f  (target ~0.39 — §3.13's landing,"
                            + " a SOFT target; see calibration.md)%n",
                    foulOuts / tg);
            System.out.printf("  Players at 4 / 5 / 6 fouls per team/game: %.2f / %.2f / %.2f"
                            + "   (of %.1f who played)%n",
                    playersWithFourFouls / tg, playersWithFiveFouls / tg, foulOuts / tg,
                    playersPlayed / tg);

            // §3.14a (decisions.md #032 J). A BALLPARK, not a TARGET — nothing in the
            // engine is tuned toward it: SimConfig.TECHNICAL_FOULS_PER_TEAM_GAME is
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
            // SimConfig.FLAGRANT_FOULS_PER_TEAM_GAME is set from the real-world figure
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

            System.out.println("========================================================");
            System.out.println();
        }

        private static double pct(long made, long attempted) {
            return attempted == 0 ? 0.0 : 100.0 * made / attempted;
        }
    }
}
