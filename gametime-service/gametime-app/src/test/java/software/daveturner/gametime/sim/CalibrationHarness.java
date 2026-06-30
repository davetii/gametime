package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.BoxScoreEntity;
import software.daveturner.gametime.entity.GameEventEntity;
import software.daveturner.gametime.entity.PlayType;
import software.daveturner.gametime.repo.BoxScoreRepo;
import software.daveturner.gametime.repo.GameEventRepo;

import java.util.ArrayList;
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
        }

        // Assist reconciliation sanity: assisted SHOT events == box-score assists.
        long assistedShots = events.stream()
                .filter(e -> e.getPlayType() == PlayType.SHOT && e.getAssistPlayerId() != null)
                .count();
        int boxAssists = boxScores.stream().mapToInt(b -> nz(b.getAssists())).sum();
        if (assistedShots != boxAssists) {
            agg.reconciliationMismatches++;
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** Mutable accumulator over all simulated team-games. */
    private static final class Agg {
        int gameCount;
        int teamGames;
        long points;
        long fga, fgm, tpa, tpm, assists, turnovers, offReb, defReb;
        long periods;
        int reconciliationMismatches;

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
            lines.add(String.format("Assist reconciliation:  %s",
                    reconciliationMismatches == 0 ? "OK (all games match)"
                            : reconciliationMismatches + " MISMATCH(es)"));

            System.out.println();
            System.out.println("================ §3.4 CALIBRATION REPORT ================");
            lines.forEach(System.out::println);
            System.out.println("========================================================");
            System.out.println();
        }

        private static double pct(long made, long attempted) {
            return attempted == 0 ? 0.0 : 100.0 * made / attempted;
        }
    }
}
