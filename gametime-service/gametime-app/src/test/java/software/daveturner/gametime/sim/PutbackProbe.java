package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.GameEventEntity;
import software.daveturner.gametime.entity.PlayType;
import software.daveturner.gametime.repo.GameEventRepo;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * §3.22 DESIGN-PASS PROBE — THROWAWAY (#043 F's discipline: delete at close-out, do not
 * promote to a harness row). Walks the event log and, for every OFFENSIVE rebound that
 * names a rebounder, classifies the NEXT event of the possession: who was picked to shoot
 * (the rebounder or a teammate), what they did, and whether a resulting make was assisted.
 *
 * <p>Run: {@code SPRING_PROFILES_ACTIVE=local,baseline mvn -q -pl gametime-app test
 * -Dtest=PutbackProbe -DputbackProbe=true -DcalibrationSeed=1000 -DfailIfNoTests=false}
 */
@SpringBootTest
@Transactional
class PutbackProbe {

    private static final String[] TEAMS = {
            "NY", "PHI", "BRK", "BOS", "NC", "ATL", "MIA", "MI", "CHI", "IND",
            "MIN", "TOR", "BUF", "VA", "MIL", "PIT", "STL", "KC", "HOU", "SA",
            "DAL", "AL", "OKL", "DEN", "LA", "CA", "SD", "SF", "PHO", "POR",
            "SEA", "UT", "VAN", "LV"
    };

    @Autowired SimConfig config;
    @Autowired GameSimulator simulator;
    @Autowired GameEventRepo gameEventRepo;
    @Autowired org.springframework.core.env.Environment environment;

    // counters
    long teamGames;
    long offRebWithRebounder;
    Map<String, Long> bySource = new TreeMap<>();      // what preceded the offensive board
    Map<String, Long> nextKind = new TreeMap<>();      // the next event's play type / outcome family
    long shooterIdentified, shooterIsRebounder;
    // next-event SHOT by the rebounder vs by a teammate
    long rbShots, rbMade, rbMadeAssisted, rbThrees;
    long tmShots, tmMade, tmMadeAssisted, tmThrees;
    Map<String, Long> rbType = new TreeMap<>();
    Map<String, Long> tmType = new TreeMap<>();
    // league-wide, for the denominators
    long allMade, allMadeAssisted, allShots;
    // the rebounder shooting LATER in the same possession (not the immediate next event)
    long rbLaterShots;

    @Test
    @EnabledIfSystemProperty(named = "putbackProbe", matches = "true")
    void decomposeSecondChanceShots() {
        long seed = Long.getLong("calibrationSeed", 1_000L);
        int rounds = 6;
        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i + 1 < TEAMS.length; i += 2) {
                String home = TEAMS[(i + round) % TEAMS.length];
                String away = TEAMS[(i + 1 + round) % TEAMS.length];
                if (home.equals(away)) continue;
                SimResult result = simulator.simulate(home, away, seed++,
                        config.defaultPossessionsPerPeriod());
                walk(gameEventRepo.findByGameIdOrderBySequenceAsc(result.getGameId()));
                teamGames += 2;
            }
        }
        print();
    }

    private void walk(List<GameEventEntity> events) {
        for (int i = 0; i < events.size(); i++) {
            GameEventEntity e = events.get(i);
            if (e.getPlayType() == PlayType.SHOT) {
                allShots++;
                if (e.getOutcome().startsWith("MADE")) {
                    allMade++;
                    if (e.getAssistPlayerId() != null) allMadeAssisted++;
                }
            }
            if (e.getPlayType() != PlayType.REBOUND || !"OFFENSIVE".equals(e.getOutcome())
                    || e.getPrimaryPlayerId() == null) continue;
            offRebWithRebounder++;
            String rebounder = e.getPrimaryPlayerId();
            String offense = e.getOffenseTeamId();
            GameEventEntity prev = i > 0 ? events.get(i - 1) : null;
            bump(bySource, prev == null ? "none" : prev.getPlayType() + "/" + family(prev.getOutcome()));
            if (i + 1 >= events.size()) { bump(nextKind, "end-of-log"); continue; }
            GameEventEntity next = events.get(i + 1);
            if (!offense.equals(next.getOffenseTeamId())) { bump(nextKind, "possession-changed"); continue; }
            bump(nextKind, next.getPlayType() + "/" + family(next.getOutcome()));
            String shooter = switch (next.getPlayType()) {
                case SHOT, TURNOVER -> next.getPrimaryPlayerId();
                case FOUL -> next.getOpponentPlayerId(); // shooting / non-shooting foul: the fouled shooter
                default -> null;
            };
            if (shooter == null) continue;
            shooterIdentified++;
            boolean isRb = shooter.equals(rebounder);
            if (isRb) shooterIsRebounder++;
            if (next.getPlayType() == PlayType.SHOT) {
                boolean made = next.getOutcome().startsWith("MADE");
                boolean assisted = next.getAssistPlayerId() != null;
                boolean three = next.getOutcome().endsWith("3PT");
                String type = next.getOutcome().replaceFirst("^(MADE|MISSED|BLOCKED)_", "");
                if (isRb) {
                    rbShots++; if (made) rbMade++; if (made && assisted) rbMadeAssisted++; if (three) rbThrees++;
                    bump(rbType, type);
                } else {
                    tmShots++; if (made) tmMade++; if (made && assisted) tmMadeAssisted++; if (three) tmThrees++;
                    bump(tmType, type);
                }
            }
            // does the rebounder shoot LATER in this possession (after a non-shot next event)?
            if (!isRb) {
                for (int j = i + 2; j < events.size() && offense.equals(events.get(j).getOffenseTeamId()); j++) {
                    GameEventEntity later = events.get(j);
                    if (later.getPlayType() == PlayType.REBOUND) break; // a new board resets the question
                    if (later.getPlayType() == PlayType.SHOT && rebounder.equals(later.getPrimaryPlayerId())) {
                        rbLaterShots++; break;
                    }
                }
            }
        }
    }

    private static String family(String outcome) {
        if (outcome == null) return "null";
        if (outcome.startsWith("MADE")) return "MADE";
        if (outcome.startsWith("MISSED")) return "MISSED";
        if (outcome.startsWith("BLOCKED")) return "BLOCKED";
        return outcome;
    }

    private static void bump(Map<String, Long> m, String k) { m.merge(k, 1L, Long::sum); }

    private void print() {
        double tg = teamGames;
        System.out.println("=== PutbackProbe ===");
        System.out.println("Profiles: " + String.join(",", environment.getActiveProfiles()));
        System.out.printf("team-games: %d%n", teamGames);
        System.out.printf("OFFENSIVE rebounds with a rebounder / team-game: %.3f%n", offRebWithRebounder / tg);
        bySource.forEach((k, v) -> System.out.printf("  preceded by %-22s %.3f%n", k, v / tg));
        System.out.println("next event after the board / team-game:");
        nextKind.forEach((k, v) -> System.out.printf("  %-28s %.3f%n", k, v / tg));
        System.out.printf("shooter identified on next event: %.3f / team-game; rebounder was the shooter: %.3f (%.1f%%)%n",
                shooterIdentified / tg, shooterIsRebounder / tg, 100.0 * shooterIsRebounder / Math.max(1, shooterIdentified));
        System.out.printf("rebounder shoots LATER in the possession (after a non-shot next event): %.3f / team-game%n", rbLaterShots / tg);
        System.out.printf("next-event SHOT by REBOUNDER: %.3f/tg, FG%% %.1f, 3PA share %.1f%%, made %.3f/tg, assisted share of makes %.1f%% (%.3f/tg)%n",
                rbShots / tg, 100.0 * rbMade / Math.max(1, rbShots), 100.0 * rbThrees / Math.max(1, rbShots),
                rbMade / tg, 100.0 * rbMadeAssisted / Math.max(1, rbMade), rbMadeAssisted / tg);
        rbType.forEach((k, v) -> System.out.printf("    rebounder type %-14s %.1f%%%n", k, 100.0 * v / Math.max(1, rbShots)));
        System.out.printf("next-event SHOT by TEAMMATE:  %.3f/tg, FG%% %.1f, 3PA share %.1f%%, made %.3f/tg, assisted share of makes %.1f%% (%.3f/tg)%n",
                tmShots / tg, 100.0 * tmMade / Math.max(1, tmShots), 100.0 * tmThrees / Math.max(1, tmShots),
                tmMade / tg, 100.0 * tmMadeAssisted / Math.max(1, tmMade), tmMadeAssisted / tg);
        tmType.forEach((k, v) -> System.out.printf("    teammate type  %-14s %.1f%%%n", k, 100.0 * v / Math.max(1, tmShots)));
        System.out.printf("league: shots %.2f/tg, made %.2f/tg, assisted share of ALL makes %.1f%% (%.2f/tg)%n",
                allShots / tg, allMade / tg, 100.0 * allMadeAssisted / Math.max(1, allMade), allMadeAssisted / tg);
    }
}
