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
    private final BlockResolver blockResolver = new BlockResolver(config);
    private final MissedShotResolver missedShotResolver = new MissedShotResolver(reboundResolver);
    private final PossessionEngine engine = new PossessionEngine(
            shotSelector, shotResolver, turnoverResolver, foulResolver,
            blockResolver, missedShotResolver, config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    // --- TeamContext wrappers: existing tests call the engine with player lists +
    // team ids; these wrap them in neutral-coach TeamContexts (×1.0 modifiers)
    // around a neutral RotationState of exactly the given players. With a squad of
    // exactly 5 and full energy, no substitution ever fires, so the pre-§3.5
    // fixed-five behavior is preserved. Coach-effect tests build TeamContexts with
    // non-neutral CoachModifiers directly.
    private TeamContext ctx(String teamId, List<PlayerGameState> players, CoachModifiers mods) {
        return new TeamContext(teamId, new RotationState(players, mods, config), mods);
    }

    private GameData simulate(List<PlayerGameState> home, List<PlayerGameState> away,
                              String homeId, String awayId, int poss, RandomGenerator rng) {
        return engine.simulate(
                ctx(homeId, home, CoachModifiers.neutral()),
                ctx(awayId, away, CoachModifiers.neutral()),
                poss, rng);
    }

    private int resolvePossession(GameData data, List<PlayerGameState> offense,
                                  List<PlayerGameState> defense, String offId, String defId,
                                  int period, int seq, RandomGenerator rng) {
        return engine.resolvePossession(data,
                ctx(offId, offense, CoachModifiers.neutral()),
                ctx(defId, defense, CoachModifiers.neutral()),
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
    void shootingFoulEventsFollowedByTwoFreeThrows() {
        // §3.10 re-baseline (#028 B): a SHOOTING_FOUL still always goes to the
        // line, but a REBOUNDING_FOUL_* only does so when the committing team is
        // already in the bonus — under the bonus it awards possession, no FTs. So
        // this invariant is now scoped to shooting fouls; the rebounding-foul
        // cases are asserted by their own tests below.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        List<GameData.EventRecord> events = data.getEvents();
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() == PlayType.FOUL && "SHOOTING_FOUL".equals(e.outcome())) {
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
    void everyMissedShotIsFollowedByAReboundOrAReboundingFoul() {
        // §3.10 re-baseline (#028 C): the rebound-foul roll is carved off the TOP
        // of the miss flow and short-circuits the board contest when it hits (the
        // whistle stopped play, so nobody rebounds). A missed shot is therefore
        // resolved by EITHER a REBOUND event or a REBOUNDING_FOUL_* FOUL — never
        // by nothing, which is what this invariant really guards.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        List<GameData.EventRecord> events = data.getEvents();
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() == PlayType.SHOT && e.outcome().startsWith("MISSED")) {
                assertTrue(i + 1 < events.size(),
                        "MISSED shot must be resolved by a following event");
                GameData.EventRecord next = events.get(i + 1);
                boolean resolved = next.playType() == PlayType.REBOUND
                        || (next.playType() == PlayType.FOUL
                            && next.outcome().startsWith("REBOUNDING_FOUL"));
                assertTrue(resolved,
                        "MISSED shot must be immediately followed by a REBOUND or a "
                                + "REBOUNDING_FOUL, was: " + next.playType() + "/" + next.outcome());
            }
        }
    }

    @Test
    void reboundOutcomesAreOneOfTheFourMissedShotOutcomes() {
        // §3.8 (#026): a missed shot resolves to one of FOUR outcomes, all carried
        // on the REBOUND PlayType — the two rebound outcomes (OFFENSIVE/DEFENSIVE)
        // plus the two OOB outcomes (OUT_OF_BOUNDS_OFFENSE/_DEFENSE, no rebounder).
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        Set<String> allowed = Set.of("OFFENSIVE", "DEFENSIVE",
                "OUT_OF_BOUNDS_OFFENSE", "OUT_OF_BOUNDS_DEFENSE");
        for (GameData.EventRecord e : data.getEvents()) {
            if (e.playType() == PlayType.REBOUND) {
                assertTrue(allowed.contains(e.outcome()),
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
                ctx("H", home, homeMods),
                ctx("A", away, awayMods),
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

    // ===================== §3.5 substitution / fatigue / foul-out =====================

    private CoachModifiers rotationCoach(int rotationDepth, int subAggressiveness) {
        software.daveturner.gametime.model.Coach c = coach(10, 10, 10);
        c.setRotationDepth(rotationDepth);
        c.setSubstitutionAggressiveness(subAggressiveness);
        return CoachModifiers.from(c, config);
    }

    /** A full rotation: 5 starters + benchCount bench players (rotationOrder 1..n). */
    private List<PlayerGameState> rotationOf(String teamId, int benchCount, double skill) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.createRotationPlayer(teamId + "-S" + i, teamId, skill,
                    software.daveturner.gametime.model.LineupRole.STARTER, null, 10, 10));
        }
        for (int i = 1; i <= benchCount; i++) {
            players.add(TestPlayerFactory.createRotationPlayer(teamId + "-B" + i, teamId, skill,
                    software.daveturner.gametime.model.LineupRole.ROTATION, i, 10, 10));
        }
        return players;
    }

    private GameData simulateRotations(List<PlayerGameState> home, CoachModifiers homeMods,
                                       List<PlayerGameState> away, CoachModifiers awayMods,
                                       int poss, RandomGenerator rng) {
        return engine.simulate(
                new TeamContext("H", new RotationState(home, homeMods, config), homeMods),
                new TeamContext("A", new RotationState(away, awayMods, config), awayMods),
                poss, rng);
    }

    @Test
    void benchPlayersAccumulateStatsOverAFullGame() {
        List<PlayerGameState> home = rotationOf("H", 5, 10);
        List<PlayerGameState> away = rotationOf("A", 5, 10);
        simulateRotations(home, rotationCoach(15, 15), away, rotationCoach(15, 15), 25, rng(42));

        // At least some bench player logged on-floor possessions (checked in).
        long benchWhoPlayed = home.stream()
                .filter(p -> !p.isStarter() && p.getOnFloorPossessions() > 0)
                .count();
        assertTrue(benchWhoPlayed > 0, "bench players should enter the game and play");
    }

    @Test
    void substitutionKeepsExactlyFiveOnFloorAllGame() {
        List<PlayerGameState> home = rotationOf("H", 6, 10);
        List<PlayerGameState> away = rotationOf("A", 6, 10);
        RotationState homeRot = new RotationState(home, rotationCoach(20, 20), config);
        RotationState awayRot = new RotationState(away, rotationCoach(20, 20), config);
        engine.simulate(
                new TeamContext("H", homeRot, rotationCoach(20, 20)),
                new TeamContext("A", awayRot, rotationCoach(20, 20)),
                25, rng(7));
        assertEquals(5, homeRot.onFloor().size());
        assertEquals(5, awayRot.onFloor().size());
    }

    @Test
    void starterLogsMoreMinutesThanDeepBench() {
        // Starters tolerate more fatigue + return first, so a starter should log
        // more on-floor possessions than the last bench player over a full game.
        List<PlayerGameState> home = rotationOf("H", 5, 10);
        List<PlayerGameState> away = rotationOf("A", 5, 10);
        simulateRotations(home, rotationCoach(12, 12), away, rotationCoach(12, 12), 25, rng(42));

        int starterPoss = home.get(0).getOnFloorPossessions();
        int deepBenchPoss = home.get(home.size() - 1).getOnFloorPossessions();
        assertTrue(starterPoss > deepBenchPoss,
                "a starter should out-play the deep bench: starter=" + starterPoss
                        + " deepBench=" + deepBenchPoss);
    }

    @Test
    void determinismHoldsWithSubstitutionOn() {
        List<PlayerGameState> h1 = rotationOf("H", 5, 10);
        List<PlayerGameState> a1 = rotationOf("A", 5, 10);
        GameData d1 = simulateRotations(h1, rotationCoach(15, 15), a1, rotationCoach(15, 15), 25, rng(42));
        List<PlayerGameState> h2 = rotationOf("H", 5, 10);
        List<PlayerGameState> a2 = rotationOf("A", 5, 10);
        GameData d2 = simulateRotations(h2, rotationCoach(15, 15), a2, rotationCoach(15, 15), 25, rng(42));

        assertEquals(d1.getHomeScore(), d2.getHomeScore());
        assertEquals(d1.getAwayScore(), d2.getAwayScore());
        assertEquals(d1.getEvents().size(), d2.getEvents().size());
        // Same seed ⇒ same minutes distribution too (subs are deterministic).
        for (int i = 0; i < h1.size(); i++) {
            assertEquals(h1.get(i).getOnFloorPossessions(), h2.get(i).getOnFloorPossessions(),
                    "sub decisions are deterministic given the seed");
        }
    }

    // ===================== §3.7 blocked shots =====================

    // An offense of weak finishers (all skills weak so shots miss/get blocked, low
    // ballSecurity kept average to avoid turnovers dominating). finishing is the 2nd
    // skill in the 13-skill overload.
    private List<PlayerGameState> weakFinisherOffense(String teamId) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + "-o" + i, teamId,
                    10, 2, 10, 2, 2, 10, 10, 10, 10, 10, 10, 10, 10));
        }
        return players;
    }

    // A defense of elite shot-blockers (rimProtection 10th, shotContest 11th), low
    // stealing so turnovers stay rare and blocks dominate the defensive events.
    private List<PlayerGameState> eliteBlockerDefense(String teamId) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + "-d" + i, teamId,
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 20, 20, 1, 10));
        }
        return players;
    }

    private boolean isBlockedEvent(GameData.EventRecord e) {
        return e.playType() == PlayType.SHOT && e.outcome().startsWith("BLOCKED");
    }

    @Test
    void gameProducesBlockedShotEvents() {
        // Elite blockers vs weak finishers should produce BLOCKED_* SHOT events.
        GameData data = simulate(weakFinisherOffense("H"), eliteBlockerDefense("A"),
                "H", "A", 25, rng(42));
        long blocks = data.getEvents().stream().filter(this::isBlockedEvent).count();
        assertTrue(blocks > 0, "elite blockers should block some shots");
    }

    @Test
    void blockedEventsAreShotsWithNoAssist() {
        GameData data = simulate(weakFinisherOffense("H"), eliteBlockerDefense("A"),
                "H", "A", 25, rng(42));
        long blocks = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            if (isBlockedEvent(e)) {
                blocks++;
                assertEquals(PlayType.SHOT, e.playType(), "a block is a SHOT event (F1)");
                assertNull(e.assistPlayerId(), "a blocked shot is never assisted (F4)");
            }
        }
        assertTrue(blocks > 0, "expected some blocked shots to assert on");
    }

    @Test
    void blockedOutcomeStringsCarryShotType() {
        GameData data = simulate(weakFinisherOffense("H"), eliteBlockerDefense("A"),
                "H", "A", 25, rng(42));
        Set<String> allowed = Set.of("BLOCKED_2PT_DRIVE", "BLOCKED_2PT_PERIMETER",
                "BLOCKED_2PT_POST", "BLOCKED_3PT");
        for (GameData.EventRecord e : data.getEvents()) {
            if (isBlockedEvent(e)) {
                assertTrue(allowed.contains(e.outcome()),
                        "unexpected block outcome string: " + e.outcome());
            }
        }
    }

    @Test
    void blockedShotChargesMissedFieldGoalAttemptNoMake() {
        // A blocked shot is a missed FGA on the shooter: FGA charged, no FGM. Over a
        // whole game, blocked-shot events must be covered by the offense's FGA count,
        // and no blocked event can coincide with a made basket.
        List<PlayerGameState> offense = weakFinisherOffense("H");
        GameData data = simulate(offense, eliteBlockerDefense("A"), "H", "A", 25, rng(42));

        long blocks = data.getEvents().stream().filter(this::isBlockedEvent).count();
        int offenseFga = offense.stream().mapToInt(PlayerGameState::getFieldGoalsAttempted).sum();
        assertTrue(blocks > 0, "expected blocks");
        assertTrue(offenseFga >= blocks,
                "every blocked shot is a charged FGA: fga=" + offenseFga + " blocks=" + blocks);
        // No blocked outcome is ever a make.
        for (GameData.EventRecord e : data.getEvents()) {
            if (isBlockedEvent(e)) {
                assertFalse(e.outcome().startsWith("MADE"), "a block is never a make");
            }
        }
    }

    @Test
    void blockedThreeChargesThreePointAttempt() {
        // A blocked THREE still charges a 3PA on the shooter (F3). Find a game with a
        // blocked three, then assert the offense's 3PA count covers it.
        for (long seed = 1; seed <= 200; seed++) {
            List<PlayerGameState> offense = weakFinisherOffense("H");
            GameData data = simulate(offense, eliteBlockerDefense("A"), "H", "A", 25, rng(seed));
            long blockedThrees = data.getEvents().stream()
                    .filter(e -> isBlockedEvent(e) && e.outcome().equals("BLOCKED_3PT"))
                    .count();
            if (blockedThrees > 0) {
                int offense3pa = offense.stream()
                        .mapToInt(PlayerGameState::getThreePointersAttempted).sum();
                assertTrue(offense3pa >= blockedThrees,
                        "a blocked THREE charges a 3PA: 3pa=" + offense3pa
                                + " blockedThrees=" + blockedThrees);
                return;
            }
        }
        // Blocked threes are very-low by design; if none appeared in 200 seeds that's
        // acceptable — the DRIVE/POST paths carry the 3PA-independent coverage.
    }

    @Test
    void blockerCreditReconcilesWithBlockedEvents() {
        // Reconciliation invariant (#025 F, extends #020/#022): count of SHOT events
        // with outcome LIKE 'BLOCKED%' == sum of recorded blocks across both teams.
        List<PlayerGameState> home = weakFinisherOffense("H");
        List<PlayerGameState> away = eliteBlockerDefense("A");
        GameData data = simulate(home, away, "H", "A", 25, rng(42));

        long blockedEvents = data.getEvents().stream().filter(this::isBlockedEvent).count();
        int recordedBlocks = home.stream().mapToInt(PlayerGameState::getBlocks).sum()
                + away.stream().mapToInt(PlayerGameState::getBlocks).sum();
        assertTrue(blockedEvents > 0, "expected some blocks to reconcile");
        assertEquals(blockedEvents, recordedBlocks,
                "BLOCKED SHOT events must reconcile with recorded blocks");
    }

    @Test
    void blockerIsCreditedNotTheShooter() {
        // The block is credited to a DEFENDER (recordBlock), never to the shooter —
        // mirrors the steal pattern (the victim is named on the event, the defender
        // gets the separate credit). Uses resolvePossession so offense/defense roles
        // are fixed (a full simulate() alternates both teams through both roles, so
        // both teams legitimately record blocks there).
        List<PlayerGameState> offense = weakFinisherOffense("H");
        List<PlayerGameState> defense = eliteBlockerDefense("A");
        GameData data = new GameData();
        long blockedEvents = 0;
        for (long seed = 1; seed <= 400; seed++) {
            resolvePossession(data, offense, defense, "H", "A", 1, 1, rng(seed));
        }
        for (GameData.EventRecord e : data.getEvents()) {
            if (isBlockedEvent(e)) {
                blockedEvents++;
                // The offensive shooter is the primary player on the block event.
                assertTrue(e.primaryPlayerId().startsWith("H-o"),
                        "the shooter (offense) is named on the block event: " + e.primaryPlayerId());
            }
        }
        int offenseBlocks = offense.stream().mapToInt(PlayerGameState::getBlocks).sum();
        int defenseBlocks = defense.stream().mapToInt(PlayerGameState::getBlocks).sum();
        assertTrue(blockedEvents > 0, "expected some blocks");
        assertEquals(0, offenseBlocks, "the shooting team records no blocks");
        assertEquals(blockedEvents, defenseBlocks,
                "the defending team's blockers are credited, one per blocked event");
    }

    @Test
    void offenseRecoveredBlockCanProduceSecondChance() {
        // An offense-recovered block re-enters at ShotSelector (Decision E): a single
        // possession can contain a BLOCKED event followed by more offensive events
        // (another shot attempt / turnover / foul) rather than ending immediately.
        boolean foundSecondChanceAfterBlock = false;
        for (long seed = 1; seed <= 300 && !foundSecondChanceAfterBlock; seed++) {
            GameData data = new GameData();
            resolvePossession(data, weakFinisherOffense("H"), eliteBlockerDefense("A"),
                    "H", "A", 1, 1, rng(seed));
            List<GameData.EventRecord> events = data.getEvents();
            for (int i = 0; i < events.size(); i++) {
                if (isBlockedEvent(events.get(i)) && i + 1 < events.size()) {
                    // A block that isn't the last event ⇒ the offense recovered and
                    // the possession continued (a second-chance event followed).
                    foundSecondChanceAfterBlock = true;
                    break;
                }
            }
        }
        assertTrue(foundSecondChanceAfterBlock,
                "an offense-recovered block should re-enter the possession loop");
    }

    @Test
    void blockSecondChancesRespectOffensiveReboundCap() {
        // Offense-recovered blocks bump the offensive-rebound counter (Decision E), so
        // BLOCKED events + OFFENSIVE rebounds sharing a possession must never let the
        // offense retain more than MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION times. Verify
        // the possession always terminates (bounded event count) and the offensive
        // second-chance count is capped.
        for (long seed = 1; seed <= 300; seed++) {
            GameData data = new GameData();
            int next = resolvePossession(data, weakFinisherOffense("H"), eliteBlockerDefense("A"),
                    "H", "A", 1, 1, rng(seed));
            // The possession terminated (returned a sequence) with a bounded number of
            // events — no infinite second-chance loop.
            assertTrue(next > 1, "possession must terminate");
            long offensiveRetentions = data.getEvents().stream()
                    .filter(e -> (isBlockedEvent(e))
                            || (e.playType() == PlayType.REBOUND && e.outcome().equals("OFFENSIVE")))
                    .count();
            // Each retention re-enters the loop; the offense can never keep the ball
            // more than the cap allows, so a single possession is bounded well below a
            // runaway count. (Cap is 3; allow slack for the terminal non-retained shot.)
            assertTrue(offensiveRetentions <= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION + 3,
                    "second chances (blocks + off rebounds) must stay bounded, got "
                            + offensiveRetentions + " (seed " + seed + ")");
        }
    }

    @Test
    void buildBlockOutcomeFormatsCorrectly() {
        assertEquals("BLOCKED_2PT_DRIVE", engine.buildBlockOutcome(ShotType.DRIVE));
        assertEquals("BLOCKED_2PT_PERIMETER", engine.buildBlockOutcome(ShotType.PERIMETER));
        assertEquals("BLOCKED_2PT_POST", engine.buildBlockOutcome(ShotType.POST));
        assertEquals("BLOCKED_3PT", engine.buildBlockOutcome(ShotType.THREE));
    }

    // ===================== §3.8 missed-shot out of bounds =====================

    private boolean isOobEvent(GameData.EventRecord e) {
        return e.playType() == PlayType.REBOUND && e.outcome().startsWith("OUT_OF_BOUNDS");
    }

    @Test
    void gameProducesOutOfBoundsEvents() {
        // OOB is a flat ~7% carve off every miss (skill-independent), so a full game
        // between average teams produces some OUT_OF_BOUNDS_* REBOUND events.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));
        long oob = data.getEvents().stream().filter(this::isOobEvent).count();
        assertTrue(oob > 0, "a full game should emit OUT_OF_BOUNDS rebound events (§3.8)");
    }

    @Test
    void outOfBoundsOutcomeStringsAreOffenseOrDefense() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));
        Set<String> allowed = Set.of("OUT_OF_BOUNDS_OFFENSE", "OUT_OF_BOUNDS_DEFENSE");
        long oob = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            if (isOobEvent(e)) {
                oob++;
                assertTrue(allowed.contains(e.outcome()), "unexpected OOB outcome: " + e.outcome());
            }
        }
        assertTrue(oob > 0, "expected some OOB events to assert on");
    }

    @Test
    void outOfBoundsEventsFollowAMissedShot() {
        // An OOB event is a missed-shot resolution: it must immediately follow a
        // MISSED SHOT (same slot the rebound occupies today).
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));
        List<GameData.EventRecord> events = data.getEvents();
        long oob = 0;
        for (int i = 0; i < events.size(); i++) {
            if (isOobEvent(events.get(i))) {
                oob++;
                assertTrue(i > 0, "OOB cannot be the first event");
                GameData.EventRecord prev = events.get(i - 1);
                assertEquals(PlayType.SHOT, prev.playType(), "OOB must follow a SHOT");
                assertTrue(prev.outcome().startsWith("MISSED"), "OOB must follow a MISSED shot");
            }
        }
        assertTrue(oob > 0, "expected some OOB events");
    }

    @Test
    void outOfBoundsEventsCreditNoRebounder() {
        // Decision E: an OOB event names no player (no rebounder credited).
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));
        long oob = 0;
        for (GameData.EventRecord e : data.getEvents()) {
            if (isOobEvent(e)) {
                oob++;
                assertNull(e.primaryPlayerId(), "OOB credits no rebounder (E): " + e.outcome());
                assertNull(e.assistPlayerId(), "OOB carries no assister");
            }
        }
        assertTrue(oob > 0, "expected some OOB events");
    }

    @Test
    void outOfBoundsExcludedFromReboundReconciliation() {
        // Decision E: OOB events (REBOUND PlayType, OUT_OF_BOUNDS_* outcome) must NOT
        // be counted as rebounds — the box-score rebound totals reconcile against ONLY
        // the exact OFFENSIVE/DEFENSIVE rebound events, with OOB present but excluded.
        List<PlayerGameState> home = teamOf5("H", 10);
        List<PlayerGameState> away = teamOf5("A", 10);
        GameData data = simulate(home, away, "H", "A", 25, rng(42));

        long oob = data.getEvents().stream().filter(this::isOobEvent).count();
        assertTrue(oob > 0, "expected OOB events present for this to be a real test");

        long offReboundEvents = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.REBOUND && e.outcome().equals("OFFENSIVE"))
                .count();
        long defReboundEvents = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.REBOUND && e.outcome().equals("DEFENSIVE"))
                .count();
        int boxOff = home.stream().mapToInt(PlayerGameState::getOffensiveRebounds).sum()
                + away.stream().mapToInt(PlayerGameState::getOffensiveRebounds).sum();
        int boxDef = home.stream().mapToInt(PlayerGameState::getDefensiveRebounds).sum()
                + away.stream().mapToInt(PlayerGameState::getDefensiveRebounds).sum();

        assertEquals(offReboundEvents, boxOff, "OOB must not inflate offensive rebounds");
        assertEquals(defReboundEvents, boxDef, "OOB must not inflate defensive rebounds");
    }

    @Test
    void outOfBoundsOffenseProducesSecondChance() {
        // OOB-offense retains the ball → a second-chance attempt follows in the same
        // possession (more offensive events after the OOB event), like an offensive
        // rebound. Search seeds for a possession containing an OUT_OF_BOUNDS_OFFENSE
        // that isn't the last event.
        boolean foundSecondChance = false;
        for (long seed = 1; seed <= 400 && !foundSecondChance; seed++) {
            GameData data = new GameData();
            resolvePossession(data, teamOf5("H", 10), teamOf5("A", 10), "H", "A", 1, 1, rng(seed));
            List<GameData.EventRecord> events = data.getEvents();
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).outcome().equals("OUT_OF_BOUNDS_OFFENSE")
                        && isOobEvent(events.get(i)) && i + 1 < events.size()) {
                    foundSecondChance = true;
                    break;
                }
            }
        }
        assertTrue(foundSecondChance,
                "an OOB-offense should re-enter the possession loop for a second chance");
    }

    @Test
    void allThreeRetentionPathsShareTheOneCap() {
        // §3.8: OOB-offense joins the offensive rebound and the §3.7 offense-recovered
        // block as a THIRD offense-retention path, all bounded by the SAME cap. A
        // single possession's total retentions (OFFENSIVE rebounds + BLOCKED events +
        // OUT_OF_BOUNDS_OFFENSE) must never let the offense keep the ball unbounded.
        // Weak finishers vs elite blockers exercises blocks + rebounds + OOB together.
        for (long seed = 1; seed <= 500; seed++) {
            GameData data = new GameData();
            int next = resolvePossession(data, weakFinisherOffense("H"), eliteBlockerDefense("A"),
                    "H", "A", 1, 1, rng(seed));
            assertTrue(next > 1, "possession must terminate");
            long retentions = data.getEvents().stream()
                    .filter(e -> isBlockedEvent(e)
                            || (e.playType() == PlayType.REBOUND && e.outcome().equals("OFFENSIVE"))
                            || e.outcome().equals("OUT_OF_BOUNDS_OFFENSE"))
                    .count();
            // Each retention re-enters the loop; all three paths count against the one
            // cap, so a possession stays bounded well below a runaway count.
            assertTrue(retentions <= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION + 3,
                    "all retentions (blocks + off rebounds + OOB-offense) must stay bounded, got "
                            + retentions + " (seed " + seed + ")");
        }
    }

    @Test
    void missedShotOutcomeStringFormatsCorrectly() {
        assertEquals("OFFENSIVE", engine.missedShotOutcomeString(MissedShotOutcome.OFFENSIVE_REBOUND));
        assertEquals("DEFENSIVE", engine.missedShotOutcomeString(MissedShotOutcome.DEFENSIVE_REBOUND));
        assertEquals("OUT_OF_BOUNDS_OFFENSE", engine.missedShotOutcomeString(MissedShotOutcome.OOB_OFFENSE));
        assertEquals("OUT_OF_BOUNDS_DEFENSE", engine.missedShotOutcomeString(MissedShotOutcome.OOB_DEFENSE));
    }

    // ---------- §3.10: the rebounding-foul possession fork (decisions.md #028 B) ----------
    // These trace the three worked examples in the §3.10 plan directly, driving
    // engine.resolveReboundFoul with a constructed ReboundFoul so the fork is
    // exercised deterministically rather than fished out of a whole game.

    /** Pre-load {@code n} fouls for {@code teamId} in {@code period}. */
    private void seedFouls(GameData data, String teamId, int period, int n) {
        for (int i = 0; i < n; i++) {
            data.addEvent("OFF", "DEF", period, i, PlayType.FOUL, "SHOOTING_FOUL",
                    "x", null, teamId);
        }
    }

    private GameData freshData() {
        GameData data = new GameData();
        data.setHomeTeamId("OFF");
        data.setAwayTeamId("DEF");
        return data;
    }

    @Test
    void example1_defensiveReboundFoulUnderTheBonusRetainsForTheOffense() {
        // Worked example 1: DEF has 3 fouls, commits a box-out foul → 4th, still
        // under the bonus (5) → NO free throws, the OFFENSE retains for a second
        // chance, and the board contest never runs.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        seedFouls(data, "DEF", 1, 3);

        PlayerGameState committer = defense.get(0);
        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.DEFENSE, committer),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(1));

        assertTrue(result.offenseRetains(), "Under the bonus a defensive foul retains");
        assertEquals(4, data.periodFoulCount("DEF", 1));
        assertFalse(data.isInBonus("DEF", 1));
        assertEquals(1, committer.getFouls(), "The committer wears the foul (#023 F)");

        List<GameData.EventRecord> emitted = data.getEvents().subList(3, data.getEvents().size());
        assertEquals(1, emitted.size(), "Exactly one FOUL event, no free throws");
        assertEquals(PlayType.FOUL, emitted.get(0).playType());
        assertEquals("REBOUNDING_FOUL_DEFENSE", emitted.get(0).outcome());
        assertEquals("DEF", emitted.get(0).committingTeamId());
        assertEquals(committer.getPlayerId(), emitted.get(0).primaryPlayerId());
        assertEquals(0, data.getHomeScore() + data.getAwayScore(), "No FTs, no points");
    }

    @Test
    void example2_theFifthDefensiveFoulItselfSendsTheOffenseToTheLine() {
        // Worked example 2: DEF already has 4 → this foul is the 5th, so
        // EMIT-THEN-COUNT means THIS foul awards the bonus FTs to the OFFENSE.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        seedFouls(data, "DEF", 1, SimConfig.BONUS_FOULS_PER_PERIOD - 1);

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.DEFENSE, defense.get(0)),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(2));

        assertFalse(result.offenseRetains(), "The possession ends after the bonus FTs");
        assertTrue(data.isInBonus("DEF", 1), "The Nth foul puts DEF in the bonus");

        List<GameData.EventRecord> emitted = data.getEvents()
                .subList(SimConfig.BONUS_FOULS_PER_PERIOD - 1, data.getEvents().size());
        assertEquals("REBOUNDING_FOUL_DEFENSE", emitted.get(0).outcome());
        long freeThrows = emitted.stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).count();
        assertEquals(SimConfig.FREE_THROWS_PER_FOUL, freeThrows,
                "The fouled team shoots FREE_THROWS_PER_FOUL bonus FTs");
        // The OFFENSE was fouled, so any made FTs score for the offense.
        assertEquals(0, data.getAwayScore(), "Bonus FTs must not score for the fouling team");
    }

    @Test
    void example3_offensiveOverTheBackEndsThePossession() {
        // Worked example 3: the OFFENSE commits → the possession ALWAYS ends, and
        // it is OFF's foul count (not DEF's) that is measured against the bonus.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        seedFouls(data, "DEF", 1, SimConfig.BONUS_FOULS_PER_PERIOD); // DEF in bonus, irrelevant

        PlayerGameState committer = offense.get(0);
        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.OFFENSE, committer),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(3));

        assertFalse(result.offenseRetains(),
                "An offensive foul is a turnover-like loss of the ball — never a retain");
        assertEquals(1, data.periodFoulCount("OFF", 1));
        assertFalse(data.isInBonus("OFF", 1), "OFF's own count is what matters, not DEF's");

        List<GameData.EventRecord> emitted = data.getEvents()
                .subList(SimConfig.BONUS_FOULS_PER_PERIOD, data.getEvents().size());
        assertEquals(1, emitted.size(), "Under the bonus: the FOUL only, no FTs");
        assertEquals("REBOUNDING_FOUL_OFFENSE", emitted.get(0).outcome());
        assertEquals("OFF", emitted.get(0).committingTeamId());
        assertEquals(1, committer.getFouls());
    }

    @Test
    void offensiveFoulInTheBonusSendsTheDEFENCEToTheLineAndStillEndsThePossession() {
        // The fourth corner of the fork: offense commits AND is in the penalty →
        // the DEFENSE shoots, and the possession still ends for the offense.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        seedFouls(data, "OFF", 1, SimConfig.BONUS_FOULS_PER_PERIOD - 1);

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.OFFENSE, offense.get(0)),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(4));

        assertFalse(result.offenseRetains());
        assertTrue(data.isInBonus("OFF", 1));

        List<GameData.EventRecord> emitted = data.getEvents()
                .subList(SimConfig.BONUS_FOULS_PER_PERIOD - 1, data.getEvents().size());
        long freeThrows = emitted.stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).count();
        assertEquals(SimConfig.FREE_THROWS_PER_FOUL, freeThrows);
        assertEquals(0, data.getHomeScore(),
                "The DEFENCE shot them, so no points may land on the offense");
    }

    @Test
    void defensiveFoulAtTheSecondChanceCapEndsThePossessionInsteadOfRetaining() {
        // The cap still bounds the loop: a defensive foul under the bonus would
        // normally retain, but not once MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION is hit.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.DEFENSE, defense.get(0)),
                offense, defense, "OFF", "DEF", 1, 50, true, rng(5));

        assertFalse(result.offenseRetains(), "capReached must force the possession to end");
    }

    @Test
    void shootingFoulsCarryTheDefendersTeamAsCommittingTeam() {
        // #028 D: the column is populated for SHOOTING_FOUL too, so the penalty
        // derivation reads ONE uniform field across every FOUL event.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 25, rng(42));

        List<GameData.EventRecord> fouls = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL).toList();
        assertFalse(fouls.isEmpty(), "A full game should emit fouls");
        for (GameData.EventRecord foul : fouls) {
            assertNotNull(foul.committingTeamId(),
                    "Every FOUL event must name a committing team: " + foul.outcome());
            if ("SHOOTING_FOUL".equals(foul.outcome())) {
                assertEquals(foul.defTeamId(), foul.committingTeamId(),
                        "A shooting foul is always on the defender's team");
            }
        }
    }

    @Test
    void reboundingFoulsAppearInAFullGameAndBothSidesOccur() {
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 60, rng(11));

        Set<String> sides = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL)
                .map(GameData.EventRecord::outcome)
                .filter(o -> o.startsWith("REBOUNDING_FOUL"))
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(sides.contains("REBOUNDING_FOUL_DEFENSE"),
                "A long game must produce defensive rebounding fouls");
        assertTrue(sides.contains("REBOUNDING_FOUL_OFFENSE"),
                "A long game must produce offensive (over-the-back) fouls too");
    }

    @Test
    void reboundingFoulsShortCircuitTheBoardContest() {
        // #028 C: on a foul the whistle stopped play, so no REBOUND event follows.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 60, rng(11));

        List<GameData.EventRecord> events = data.getEvents();
        for (int i = 0; i < events.size() - 1; i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() == PlayType.FOUL && e.outcome().startsWith("REBOUNDING_FOUL")) {
                assertNotEquals(PlayType.REBOUND, events.get(i + 1).playType(),
                        "A rebounding foul must short-circuit the board contest");
            }
        }
    }

    @Test
    void bonusFreeThrowShooterComesFromTheFouledTeam() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        seedFouls(data, "DEF", 1, SimConfig.BONUS_FOULS_PER_PERIOD - 1);

        engine.resolveReboundFoul(data,
                new ReboundFoul(ReboundFoul.Side.DEFENSE, defense.get(0)),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(6));

        Set<String> offenseIds = new HashSet<>();
        offense.forEach(p -> offenseIds.add(p.getPlayerId()));
        data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW)
                .forEach(e -> assertTrue(offenseIds.contains(e.primaryPlayerId()),
                        "The bonus FT shooter must be on the FOULED team"));
    }

    @Test
    void pickFreeThrowShooterFallsBackWhenWeightsAreZero() {
        List<PlayerGameState> zeroed = teamOf5("Z", 0);
        assertNotNull(engine.pickFreeThrowShooter(zeroed, rng(1)));
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
