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
        long seed = 1_000L;
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

            System.out.println("========================================================");
            System.out.println();
        }

        private static double pct(long made, long attempted) {
            return attempted == 0 ? 0.0 : 100.0 * made / attempted;
        }
    }
}
