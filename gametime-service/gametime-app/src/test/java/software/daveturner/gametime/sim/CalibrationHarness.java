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
import java.util.List;

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
                    && e.getOutcome().startsWith("MADE")
                    && (e.getOutcome().endsWith("DRIVE") || e.getOutcome().endsWith("POST"))) {
                agg.madeContactShots++;
            } else if (e.getPlayType() == PlayType.FOUL) {
                agg.foulsByOutcome.merge(String.valueOf(e.getOutcome()), 1L, Long::sum);
                if (e.getCommittingTeamId() != null) {
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
        // §3.11: made DRIVE/POST — the denominator the and-1 rate is a share OF
        // (the A2 gate: only a made contact shot can draw one).
        long madeContactShots;
        long teamPeriodFouls;
        int teamPeriods, teamPeriodsInBonus;

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
            lines.add(String.format("Points / team / game:   %.1f   (target ~112)", points / tg));
            lines.add(String.format("FG%%:                    %.1f%%  (target ~47%%)", pct(fgm, fga)));
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
            System.out.printf("  Made contact FG / team:  %.1f   (the and-1 denominator: made DRIVE/POST)%n",
                    madeContactShots / tg);
            long andOnes = foulsByOutcome.getOrDefault("AND_ONE", 0L);
            System.out.printf("  And-1s / team / game:    %.2f   (%.1f%% of made contact FG)%n",
                    andOnes / tg,
                    madeContactShots == 0 ? 0.0 : 100.0 * andOnes / madeContactShots);
            for (java.util.Map.Entry<String, Long> e
                    : new java.util.TreeMap<>(freeThrowsBySource).entrySet()) {
                long made = freeThrowsMadeBySource.getOrDefault(e.getKey(), 0L);
                System.out.printf("  FTA %-12s %5.1f%%  (%.2f / team / game; %.2f pts/team)%n",
                        e.getKey() + ":", freeThrows == 0 ? 0.0 : 100.0 * e.getValue() / freeThrows,
                        e.getValue() / tg, made / tg);
            }

            System.out.println("========================================================");
            System.out.println();
        }

        private static double pct(long made, long attempted) {
            return attempted == 0 ? 0.0 : 100.0 * made / attempted;
        }
    }
}
