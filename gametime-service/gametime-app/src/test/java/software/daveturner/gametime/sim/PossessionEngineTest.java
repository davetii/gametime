package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class PossessionEngineTest {

    private final SimConfig config = new SimConfig();
    private final ShotSelector shotSelector = new ShotSelector();
    private final ShotResolver shotResolver = new ShotResolver(config);
    private final TurnoverResolver turnoverResolver = new TurnoverResolver(config);
    private final FoulResolver foulResolver = new FoulResolver(config);
    private final ReboundResolver reboundResolver = new ReboundResolver(config);
    private final PossessionEngine engine = new PossessionEngine(
            shotSelector, shotResolver, turnoverResolver, foulResolver, reboundResolver, config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    // --- TeamContext wrappers: existing tests call the engine with player lists +
    // team ids; these wrap them in neutral-coach TeamContexts (×1.0 modifiers) so
    // the pre-§3.4 behavior is preserved. Coach-effect tests build TeamContexts
    // with non-neutral CoachModifiers directly.
    private GameData simulate(List<PlayerGameState> home, List<PlayerGameState> away,
                              String homeId, String awayId, int poss, RandomGenerator rng) {
        return engine.simulate(
                new TeamContext(homeId, home, CoachModifiers.neutral()),
                new TeamContext(awayId, away, CoachModifiers.neutral()),
                poss, rng);
    }

    private int resolvePossession(GameData data, List<PlayerGameState> offense,
                                  List<PlayerGameState> defense, String offId, String defId,
                                  int period, int seq, RandomGenerator rng) {
        return engine.resolvePossession(data,
                new TeamContext(offId, offense, CoachModifiers.neutral()),
                new TeamContext(defId, defense, CoachModifiers.neutral()),
                period, seq, rng);
    }

    private List<PlayerGameState> teamOf5(String teamId, double skill) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + "-p" + i, teamId, skill));
        }
        return players;
    }

    @Test
    void fullGameCompletesWithBoundedPossessionCount() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        assertTrue(data.getEvents().size() > 0);
        assertTrue(data.getPeriods() >= SimConfig.PERIODS);
        assertTrue(data.getHomeScore() >= 0);
        assertTrue(data.getAwayScore() >= 0);
    }

    @Test
    void sequenceIsMonotonicallyIncreasingAcrossGame() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        int prevSequence = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            assertTrue(e.sequence() > prevSequence,
                    "sequence must be monotonically increasing: " + prevSequence + " -> " + e.sequence());
            prevSequence = e.sequence();
        }
    }

    @Test
    void periodAdvancesCorrectly() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        int lastPeriod = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            assertTrue(e.period() >= lastPeriod);
            lastPeriod = e.period();
        }
        assertTrue(lastPeriod >= SimConfig.PERIODS);
    }

    @Test
    void gameProducesAllEventTypes() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        Set<PlayType> seen = new HashSet<>();
        for (GameData.EventRecord e : data.getEvents()) {
            seen.add(e.playType());
        }
        assertTrue(seen.contains(PlayType.SHOT), "Should produce SHOT events");
        assertTrue(seen.contains(PlayType.TURNOVER), "Should produce TURNOVER events");
        // FOUL and FREE_THROW may depend on seed but should appear with enough possessions
    }

    @Test
    void boxScorePointsReconcileWithShotAndFreeThrowEvents() {
        List<PlayerGameState> home = teamOf5("H", 10);
        List<PlayerGameState> away = teamOf5("A", 10);
        GameData data = simulate(home, away, "H", "A", 25, rng(42));

        int eventHomePoints = 0;
        int eventAwayPoints = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            int pts = pointsFromEvent(e);
            if (e.offTeamId().equals("H")) eventHomePoints += pts;
            else eventAwayPoints += pts;
        }

        int boxHome = home.stream().mapToInt(PlayerGameState::getPoints).sum();
        int boxAway = away.stream().mapToInt(PlayerGameState::getPoints).sum();

        assertEquals(eventHomePoints, boxHome, "Home box score must reconcile with events");
        assertEquals(eventAwayPoints, boxAway, "Away box score must reconcile with events");
        assertEquals(eventHomePoints, data.getHomeScore(), "Home GameData score must match");
        assertEquals(eventAwayPoints, data.getAwayScore(), "Away GameData score must match");
    }

    @Test
    void lopsidedMatchupBetterTeamWins() {
        int eliteWins = 0;
        for (long seed = 1; seed <= 20; seed++) {
            GameData data = simulate(teamOf5("H", 18), teamOf5("A", 3),
                    "H", "A", 25, rng(seed));
            if (data.getHomeScore() > data.getAwayScore()) eliteWins++;
        }
        assertTrue(eliteWins >= 15, "Elite team should win most games, won " + eliteWins + "/20");
    }

    @Test
    void believableFinalScore() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        assertTrue(data.getHomeScore() >= 50 && data.getHomeScore() <= 160,
                "Home score out of range: " + data.getHomeScore());
        assertTrue(data.getAwayScore() >= 50 && data.getAwayScore() <= 160,
                "Away score out of range: " + data.getAwayScore());
    }

    @Test
    void determinismSameSeedSameResult() {
        GameData data1 = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));
        GameData data2 = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        assertEquals(data1.getHomeScore(), data2.getHomeScore());
        assertEquals(data1.getAwayScore(), data2.getAwayScore());
        assertEquals(data1.getEvents().size(), data2.getEvents().size());

        for (int i = 0; i < data1.getEvents().size(); i++) {
            GameData.EventRecord e1 = data1.getEvents().get(i);
            GameData.EventRecord e2 = data2.getEvents().get(i);
            assertEquals(e1.playType(), e2.playType());
            assertEquals(e1.outcome(), e2.outcome());
            assertEquals(e1.sequence(), e2.sequence());
            assertEquals(e1.period(), e2.period());
        }
    }

    @Test
    void determinismDifferentSeedDifferentResult() {
        GameData data1 = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));
        GameData data2 = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(999));

        boolean anyDifference = data1.getHomeScore() != data2.getHomeScore()
                || data1.getAwayScore() != data2.getAwayScore()
                || data1.getEvents().size() != data2.getEvents().size();
        assertTrue(anyDifference, "Different seeds should (very likely) produce different results");
    }

    @Test
    void overtimePlaysWhenRegulationTied() {
        // Try many seeds to find one that produces OT, or verify structure is correct
        boolean foundOT = false;
        for (long seed = 1; seed <= 200; seed++) {
            GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                    "H", "A", 25, rng(seed));
            if (data.getPeriods() > SimConfig.PERIODS) {
                foundOT = true;
                assertTrue(data.getHomeScore() != data.getAwayScore(),
                        "OT game must not end tied");
                break;
            }
        }
        assertTrue(foundOT, "Should find at least one OT game in 200 seeds");
    }

    @Test
    void buildShotOutcomeFormatsCorrectly() {
        assertEquals("MADE_2PT_DRIVE", engine.buildShotOutcome(true, ShotType.DRIVE));
        assertEquals("MISSED_2PT_DRIVE", engine.buildShotOutcome(false, ShotType.DRIVE));
        assertEquals("MADE_2PT_PERIMETER", engine.buildShotOutcome(true, ShotType.PERIMETER));
        assertEquals("MISSED_2PT_PERIMETER", engine.buildShotOutcome(false, ShotType.PERIMETER));
        assertEquals("MADE_2PT_POST", engine.buildShotOutcome(true, ShotType.POST));
        assertEquals("MISSED_2PT_POST", engine.buildShotOutcome(false, ShotType.POST));
        assertEquals("MADE_3PT", engine.buildShotOutcome(true, ShotType.THREE));
        assertEquals("MISSED_3PT", engine.buildShotOutcome(false, ShotType.THREE));
    }

    @Test
    void foulEventsFollowedByTwoFreeThrows() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        List<GameData.EventRecord> events = data.getEvents();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).playType() == PlayType.FOUL) {
                assertTrue(i + 2 < events.size(), "FOUL must be followed by 2 FREE_THROWs");
                assertEquals(PlayType.FREE_THROW, events.get(i + 1).playType());
                assertEquals(PlayType.FREE_THROW, events.get(i + 2).playType());
            }
        }
    }

    @Test
    void gameProducesReboundEvents() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        boolean anyRebound = data.getEvents().stream()
                .anyMatch(e -> e.playType() == PlayType.REBOUND);
        assertTrue(anyRebound, "A full game should emit REBOUND events (§3.3)");
    }

    @Test
    void everyMissedShotIsFollowedByARebound() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        List<GameData.EventRecord> events = data.getEvents();
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() == PlayType.SHOT && e.outcome().startsWith("MISSED")) {
                assertTrue(i + 1 < events.size(),
                        "MISSED shot must be followed by a REBOUND");
                assertEquals(PlayType.REBOUND, events.get(i + 1).playType(),
                        "MISSED shot must be immediately followed by a REBOUND");
            }
        }
    }

    @Test
    void reboundOutcomesAreOnlyOffensiveOrDefensive() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        for (GameData.EventRecord e : data.getEvents()) {
            if (e.playType() == PlayType.REBOUND) {
                assertTrue(e.outcome().equals("OFFENSIVE") || e.outcome().equals("DEFENSIVE"),
                        "Unexpected rebound outcome: " + e.outcome());
            }
        }
    }

    @Test
    void defensiveReboundDominatesAndEndsPossession() {
        // Elite defensive rebounders vs hopeless offensive rebounders: nearly
        // every missed shot is grabbed defensively. The PROB_FLOOR (0.02) means
        // offensive rebounds aren't strictly impossible, but they're rare and
        // when a defensive rebound is grabbed the possession ends (one REBOUND).
        List<PlayerGameState> offense = new ArrayList<>();
        List<PlayerGameState> defense = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            // offenseRebound = 1 (penultimate param), everything else average
            offense.add(TestPlayerFactory.create("O" + i, "H",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 1.0, 10));
            // defenseRebound = 20
            defense.add(TestPlayerFactory.create("D" + i, "A",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20.0));
        }

        GameData data = new GameData();
        int possessions = 500;
        for (long seed = 1; seed <= possessions; seed++) {
            resolvePossession(data, offense, defense, "H", "A", 1, 1, rng(seed));
        }
        long offRebounds = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.REBOUND && e.outcome().equals("OFFENSIVE"))
                .count();
        long defRebounds = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.REBOUND && e.outcome().equals("DEFENSIVE"))
                .count();
        // Defensive rebounds should vastly outnumber offensive ones (floor only).
        assertTrue(defRebounds > offRebounds * 10,
                "Defensive rebounds should dominate: def=" + defRebounds + " off=" + offRebounds);
    }

    @Test
    void offensiveReboundProducesSecondChanceEvents() {
        // Elite offensive rebounders vs hopeless defensive rebounders: missed
        // shots should generate offensive rebounds + second-chance attempts.
        List<PlayerGameState> offense = new ArrayList<>();
        List<PlayerGameState> defense = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            // offenseRebound = 20
            offense.add(TestPlayerFactory.create("O" + i, "H",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20.0, 10));
            // defenseRebound = 1
            defense.add(TestPlayerFactory.create("D" + i, "A",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 1.0));
        }

        boolean anyOffensiveRebound = false;
        for (long seed = 1; seed <= 100 && !anyOffensiveRebound; seed++) {
            GameData data = new GameData();
            resolvePossession(data, offense, defense, "H", "A", 1, 1, rng(seed));
            anyOffensiveRebound = data.getEvents().stream()
                    .anyMatch(e -> e.playType() == PlayType.REBOUND
                            && e.outcome().equals("OFFENSIVE"));
            if (anyOffensiveRebound) {
                // A possession with an offensive rebound must contain >1 SHOT
                // attempt (the original miss + at least one second chance) OR end
                // in a turnover/foul on the second chance — in all cases, >2 events.
                assertTrue(data.getEvents().size() > 2,
                        "Offensive rebound should produce second-chance events");
            }
        }
        assertTrue(anyOffensiveRebound,
                "Elite offensive rebounders should grab offensive rebounds");
    }

    @Test
    void offensiveReboundsCappedPerPossession() {
        // Force the offense to always rebound by making them elite offensive
        // rebounders vs hopeless defenders, and verify a single possession never
        // emits more than MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION offensive rebounds.
        List<PlayerGameState> offense = new ArrayList<>();
        List<PlayerGameState> defense = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            offense.add(TestPlayerFactory.create("O" + i, "H",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20.0, 10));
            defense.add(TestPlayerFactory.create("D" + i, "A",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 1.0));
        }

        for (long seed = 1; seed <= 300; seed++) {
            GameData data = new GameData();
            resolvePossession(data, offense, defense, "H", "A", 1, 1, rng(seed));
            long offRebounds = data.getEvents().stream()
                    .filter(e -> e.playType() == PlayType.REBOUND
                            && e.outcome().equals("OFFENSIVE"))
                    .count();
            assertTrue(offRebounds <= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION,
                    "Offensive rebounds per possession must be capped, got " + offRebounds
                            + " (seed " + seed + ")");
        }
    }

    @Test
    void sequenceContinuousThroughReboundAndSecondChance() {
        // The whole-game monotonic-sequence test covers ordering, but assert it
        // explicitly through a rebound-heavy possession too.
        List<PlayerGameState> offense = new ArrayList<>();
        List<PlayerGameState> defense = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            offense.add(TestPlayerFactory.create("O" + i, "H",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20.0, 10));
            defense.add(TestPlayerFactory.create("D" + i, "A",
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 1.0));
        }

        GameData data = new GameData();
        int next = resolvePossession(data, offense, defense, "H", "A", 1, 1, rng(7));
        int prev = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            assertEquals(prev + 1, e.sequence(),
                    "sequence must be gap-free within a possession: " + prev + " -> " + e.sequence());
            prev = e.sequence();
        }
        assertEquals(prev + 1, next, "returned sequence must be one past the last event");
    }

    @Test
    void boxScoreReboundsReconcileWithReboundEvents() {
        List<PlayerGameState> home = teamOf5("H", 10);
        List<PlayerGameState> away = teamOf5("A", 10);
        GameData data = simulate(home, away, "H", "A", 25, rng(42));

        long offReboundEvents = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.REBOUND && e.outcome().equals("OFFENSIVE"))
                .count();
        long defReboundEvents = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.REBOUND && e.outcome().equals("DEFENSIVE"))
                .count();

        int boxOffRebounds = (home.stream().mapToInt(PlayerGameState::getOffensiveRebounds).sum())
                + (away.stream().mapToInt(PlayerGameState::getOffensiveRebounds).sum());
        int boxDefRebounds = (home.stream().mapToInt(PlayerGameState::getDefensiveRebounds).sum())
                + (away.stream().mapToInt(PlayerGameState::getDefensiveRebounds).sum());

        assertEquals(offReboundEvents, boxOffRebounds,
                "Offensive rebound counts must reconcile with OFFENSIVE rebound events");
        assertEquals(defReboundEvents, boxDefRebounds,
                "Defensive rebound counts must reconcile with DEFENSIVE rebound events");
    }

    // ===================== §3.4 coach / chemistry / assists =====================

    // A team of 5 with all-skill level + explicit chemistry skills (teamOffense,
    // teamDefense, passing, acumen) so assist/efficiency behavior is controllable.
    private List<PlayerGameState> teamOf5WithPassing(String teamId, double passing) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + "-p" + i, teamId,
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
                    10, 10, passing, 10));
        }
        return players;
    }

    private GameData simulateWithCoaches(List<PlayerGameState> home, CoachModifiers homeMods,
                                         List<PlayerGameState> away, CoachModifiers awayMods,
                                         int poss, RandomGenerator rng) {
        return engine.simulate(
                new TeamContext("H", home, homeMods),
                new TeamContext("A", away, awayMods),
                poss, rng);
    }

    @Test
    void madeShotsCanBeAssistedAndReconcile() {
        // Good passers → assists should appear, and assisted-SHOT events must equal
        // the box-score assist totals (Decision B1 reconciliation).
        List<PlayerGameState> home = teamOf5WithPassing("H", 16);
        List<PlayerGameState> away = teamOf5WithPassing("A", 16);
        GameData data = simulate(home, away, "H", "A", 25, rng(42));

        long assistedShotEvents = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.SHOT && e.assistPlayerId() != null)
                .count();
        int boxAssists = home.stream().mapToInt(PlayerGameState::getAssists).sum()
                + away.stream().mapToInt(PlayerGameState::getAssists).sum();

        assertTrue(assistedShotEvents > 0, "Good passers should produce assists");
        assertEquals(assistedShotEvents, boxAssists,
                "Assisted SHOT events must reconcile with box-score assists");
    }

    @Test
    void assistsOnlyAttachToMadeShots() {
        GameData data = simulate(teamOf5WithPassing("H", 14), teamOf5WithPassing("A", 14),
                "H", "A", 25, rng(7));
        for (GameData.EventRecord e : data.getEvents()) {
            if (e.assistPlayerId() != null) {
                assertEquals(PlayType.SHOT, e.playType(), "Only SHOT events carry an assister");
                assertTrue(e.outcome().startsWith("MADE"), "Only made shots are assisted");
            }
        }
    }

    @Test
    void assisterIsNeverTheShooter() {
        GameData data = simulate(teamOf5WithPassing("H", 18), teamOf5WithPassing("A", 18),
                "H", "A", 25, rng(99));
        for (GameData.EventRecord e : data.getEvents()) {
            if (e.assistPlayerId() != null) {
                assertNotEquals(e.primaryPlayerId(), e.assistPlayerId(),
                        "The shooter cannot assist their own basket");
            }
        }
    }

    @Test
    void betterPassingTeamsRecordMoreAssists() {
        GameData lowPass = simulate(teamOf5WithPassing("H", 3), teamOf5WithPassing("A", 3),
                "H", "A", 25, rng(42));
        GameData highPass = simulate(teamOf5WithPassing("H", 18), teamOf5WithPassing("A", 18),
                "H", "A", 25, rng(42));

        long lowAssists = lowPass.getEvents().stream()
                .filter(e -> e.playType() == PlayType.SHOT && e.assistPlayerId() != null).count();
        long highAssists = highPass.getEvents().stream()
                .filter(e -> e.playType() == PlayType.SHOT && e.assistPlayerId() != null).count();

        assertTrue(highAssists > lowAssists,
                "Better-passing teams should record more assists: high=" + highAssists
                        + " low=" + lowAssists);
    }

    @Test
    void resolveAssistReturnsNullForLonePlayerOffense() {
        // A one-player offense has no supporting cast → never assisted.
        List<PlayerGameState> solo = new ArrayList<>();
        solo.add(TestPlayerFactory.create("solo", "H", 10));
        PlayerGameState shooter = solo.get(0);
        for (long seed = 1; seed <= 50; seed++) {
            assertNull(engine.resolveAssist(solo, shooter, rng(seed)),
                    "No supporting cast ⇒ no assist");
        }
    }

    @Test
    void fasterCoachRunsMorePossessions() {
        CoachModifiers fast = CoachModifiers.from(coach(20, 10, 10), config);
        CoachModifiers slow = CoachModifiers.from(coach(1, 10, 10), config);

        GameData fastGame = simulateWithCoaches(teamOf5("H", 10), fast,
                teamOf5("A", 10), fast, 25, rng(42));
        GameData slowGame = simulateWithCoaches(teamOf5("H", 10), slow,
                teamOf5("A", 10), slow, 25, rng(42));

        long fastShots = fastGame.getEvents().stream()
                .filter(e -> e.playType() == PlayType.SHOT).count();
        long slowShots = slowGame.getEvents().stream()
                .filter(e -> e.playType() == PlayType.SHOT).count();
        assertTrue(fastShots > slowShots,
                "A fast coach should run more possessions (more shots): fast=" + fastShots
                        + " slow=" + slowShots);
    }

    @Test
    void aggressiveDefensiveSchemeForcesMoreTurnovers() {
        CoachModifiers aggressive = CoachModifiers.from(coach(10, 10, 20), config);
        CoachModifiers passive = CoachModifiers.from(coach(10, 10, 1), config);
        CoachModifiers neutral = CoachModifiers.neutral();

        // Home defense aggressive vs. home defense passive — compare turnovers the
        // AWAY offense commits (i.e. forced by the home defense).
        int aggressiveTOs = 0;
        int passiveTOs = 0;
        for (long seed = 1; seed <= 30; seed++) {
            GameData aggGame = simulateWithCoaches(teamOf5("H", 10), aggressive,
                    teamOf5("A", 10), neutral, 25, rng(seed));
            GameData pasGame = simulateWithCoaches(teamOf5("H", 10), passive,
                    teamOf5("A", 10), neutral, 25, rng(seed));
            aggressiveTOs += countAwayTurnovers(aggGame);
            passiveTOs += countAwayTurnovers(pasGame);
        }
        assertTrue(aggressiveTOs > passiveTOs,
                "An aggressive defense should force more turnovers: agg=" + aggressiveTOs
                        + " passive=" + passiveTOs);
    }

    private int countAwayTurnovers(GameData data) {
        return (int) data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.TURNOVER && e.offTeamId().equals("A"))
                .count();
    }

    private software.daveturner.gametime.model.Coach coach(Integer pace, Integer off, Integer def) {
        software.daveturner.gametime.model.Coach c = new software.daveturner.gametime.model.Coach();
        c.setPace(pace);
        c.setOffensiveScheme(off);
        c.setDefensiveScheme(def);
        return c;
    }

    private int pointsFromEvent(GameData.EventRecord e) {
        if (e.playType() == PlayType.SHOT && e.outcome().startsWith("MADE")) {
            return e.outcome().contains("3PT") ? 3 : 2;
        }
        if (e.playType() == PlayType.FREE_THROW && "MADE".equals(e.outcome())) {
            return 1;
        }
        return 0;
    }
}
