package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class PossessionEngineTest {

    private final SimConfig config = SimConfig.baseline();
    private final ShotSelector shotSelector = new ShotSelector();
    private final ShotResolver shotResolver = new ShotResolver(config);
    private final TurnoverResolver turnoverResolver = new TurnoverResolver(config);
    private final FoulResolver foulResolver = new FoulResolver(config);
    private final ReboundResolver reboundResolver = new ReboundResolver(config);
    private final BlockResolver blockResolver = new BlockResolver(config);
    private final MissedShotResolver missedShotResolver = new MissedShotResolver(reboundResolver, config);
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
        // A BONUS free throw is the one event whose points do NOT score for the
        // possession's offense (see scoringTeamId): when the OFFENSE committed the
        // rebounding foul, the DEFENSE shoots. The FREE_THROW event itself carries
        // no committing team, so track it from the FOUL that precedes it.
        String lastFoulCommitter = null;
        for (GameData.EventRecord e : data.getEvents()) {
            if (e.playType() == PlayType.FOUL) {
                lastFoulCommitter = e.committingTeamId();
            }
            int pts = pointsFromEvent(e);
            if (scoringTeamId(e, lastFoulCommitter).equals("H")) eventHomePoints += pts;
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
                // §3.12 re-baseline (#030 C): the count is no longer a flat 2 — a
                // foul that stopped a THREE awards 3. Assert "2 or 3, all tagged
                // SHOOTING" rather than a fixed count; the exact 2-vs-3 rule is
                // pinned by shotTypeDecidesTheStoppedShotFreeThrowCount below.
                assertTrue(i + 2 < events.size(), "FOUL must be followed by >= 2 FREE_THROWs");
                assertEquals(PlayType.FREE_THROW, events.get(i + 1).playType());
                assertEquals(PlayType.FREE_THROW, events.get(i + 2).playType());
                int fts = 0;
                for (int j = i + 1; j < events.size()
                        && events.get(j).playType() == PlayType.FREE_THROW; j++) {
                    assertTrue(events.get(j).outcome().endsWith("_SHOOTING"),
                            "A stopped-shot FT carries the SHOOTING source: "
                                    + events.get(j).outcome());
                    fts++;
                }
                assertTrue(fts == 2 || fts == 3,
                        "A shooting foul awards 2 FTs, or 3 on a stopped three: " + fts);
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
        // emits more than MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION offensive rebounds.
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
            assertTrue(offRebounds <= config.maxOffensiveRetentionsPerPossession(),
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
        // Same seed ⇒ same minutes distribution too. As of §3.13 the rotation step
        // CONSUMES RNG (the foul-trouble sit roll, decisions.md #031 revising #023 C),
        // so this is reproducibility from the seed rather than from the step being
        // RNG-free — which is exactly why the draw is unconditional and fixed-point.
        for (int i = 0; i < h1.size(); i++) {
            assertEquals(h1.get(i).getOnFloorPossessions(), h2.get(i).getOnFloorPossessions(),
                    "sub decisions are reproducible given the seed");
        }
    }

    @Test
    void rotationStepConsumesTheSameRngRegardlessOfFoulTrouble() {
        // §3.13 / decisions.md #031, Step 4: the foul-trouble draw is taken
        // UNCONDITIONALLY at a fixed point in advancePossession(), so the seed stream
        // advances identically whether or not anyone is in foul trouble. If it forked
        // on rotation state the shift would be unreproducible.
        List<PlayerGameState> squad = rotationOf("H", 5, 10);
        RotationState clean = new RotationState(squad, CoachModifiers.neutral(), config);
        List<PlayerGameState> troubledSquad = rotationOf("H", 5, 10);
        for (int f = 0; f < 4; f++) {
            troubledSquad.get(0).recordFoul();
        }
        RotationState troubled =
                new RotationState(troubledSquad, CoachModifiers.neutral(), config);

        RandomGenerator cleanRng = rng(7);
        RandomGenerator troubledRng = rng(7);
        for (int i = 0; i < 50; i++) {
            clean.advancePossession(cleanRng);
            troubled.advancePossession(troubledRng);
        }
        assertEquals(cleanRng.nextLong(), troubledRng.nextLong(),
                "both streams must be at the same position after the same number of"
                        + " rotation steps, regardless of foul state");
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
        // offense retain more than MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION times. Verify
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
            assertTrue(offensiveRetentions <= config.maxOffensiveRetentionsPerPossession() + 3,
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
            assertTrue(retentions <= config.maxOffensiveRetentionsPerPossession() + 3,
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
        assertFalse(data.isInBonus("DEF", 1, config));
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
        seedFouls(data, "DEF", 1, config.bonusFoulsPerPeriod() - 1);

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.DEFENSE, defense.get(0)),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(2));

        assertFalse(result.offenseRetains(), "The possession ends after the bonus FTs");
        assertTrue(data.isInBonus("DEF", 1, config), "The Nth foul puts DEF in the bonus");

        List<GameData.EventRecord> emitted = data.getEvents()
                .subList(config.bonusFoulsPerPeriod() - 1, data.getEvents().size());
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
        seedFouls(data, "DEF", 1, config.bonusFoulsPerPeriod()); // DEF in bonus, irrelevant

        PlayerGameState committer = offense.get(0);
        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.OFFENSE, committer),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(3));

        assertFalse(result.offenseRetains(),
                "An offensive foul is a turnover-like loss of the ball — never a retain");
        assertEquals(1, data.periodFoulCount("OFF", 1));
        assertFalse(data.isInBonus("OFF", 1, config), "OFF's own count is what matters, not DEF's");

        List<GameData.EventRecord> emitted = data.getEvents()
                .subList(config.bonusFoulsPerPeriod(), data.getEvents().size());
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
        seedFouls(data, "OFF", 1, config.bonusFoulsPerPeriod() - 1);

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, new ReboundFoul(ReboundFoul.Side.OFFENSE, offense.get(0)),
                offense, defense, "OFF", "DEF", 1, 50, false, rng(4));

        assertFalse(result.offenseRetains());
        assertTrue(data.isInBonus("OFF", 1, config));

        List<GameData.EventRecord> emitted = data.getEvents()
                .subList(config.bonusFoulsPerPeriod() - 1, data.getEvents().size());
        long freeThrows = emitted.stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).count();
        assertEquals(SimConfig.FREE_THROWS_PER_FOUL, freeThrows);
        assertEquals(0, data.getHomeScore(),
                "The DEFENCE shot them, so no points may land on the offense");
    }

    @Test
    void defensiveFoulAtTheSecondChanceCapEndsThePossessionInsteadOfRetaining() {
        // The cap still bounds the loop: a defensive foul under the bonus would
        // normally retain, but not once MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION is hit.
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
        seedFouls(data, "DEF", 1, config.bonusFoulsPerPeriod() - 1);

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

    // --- §3.11 and-1 (decisions.md #029 A1/A2/A3/B/D) -----------------------

    @Test
    void andOneEmitsAFoulOnTheDefenderAndExactlyOneFreeThrow() {
        // #029 B: the make is already scored; the and-1 only ADDS one FT. The foul
        // is one-sided (always the defender), so committingTeamId is defTeamId —
        // #028 D's column reused with no new plumbing.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        PlayerGameState shooter = offense.get(0);
        PlayerGameState defender = defense.get(0);

        int next = engine.awardAndOne(data, shooter, defender, "OFF", "DEF", 1, 50, rng(1));

        List<GameData.EventRecord> events = data.getEvents();
        assertEquals(1 + SimConfig.AND_ONE_FREE_THROWS, events.size(),
                "An and-1 is exactly one FOUL plus one FREE_THROW");
        assertEquals(52, next, "sequence advances past the FOUL and the single FT");

        GameData.EventRecord foul = events.get(0);
        assertEquals(PlayType.FOUL, foul.playType());
        assertEquals("AND_ONE", foul.outcome());
        assertEquals(defender.getPlayerId(), foul.primaryPlayerId(),
                "The DEFENDER commits an and-1 foul");
        assertEquals("DEF", foul.committingTeamId(),
                "One-sided: the committing team is always the defense");
        assertEquals(1, defender.getFouls(), "The defender wears the foul (#023 F)");

        GameData.EventRecord ft = events.get(1);
        assertEquals(PlayType.FREE_THROW, ft.playType());
        assertEquals(shooter.getPlayerId(), ft.primaryPlayerId(),
                "The SHOOTER shoots the and-1 free throw");
        assertTrue(ft.outcome().endsWith("_AND_ONE"),
                "The FT must be tagged with its source (#029 D): " + ft.outcome());
        assertEquals(1, shooter.getFreeThrowsAttempted());
    }

    @Test
    void andOneAwardsExactlyOneFreeThrowEvenInTheBonus() {
        // #029 B (a guardrail, not an emergent property): an and-1 is ALWAYS one
        // FT by rule, penalty status irrelevant — it must never consult the bonus.
        GameData data = freshData();
        seedFouls(data, "DEF", 1, config.bonusFoulsPerPeriod() + 2);
        assertTrue(data.isInBonus("DEF", 1, config), "precondition: DEF is well into the penalty");

        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        int before = data.getEvents().size();
        engine.awardAndOne(data, offense.get(0), defense.get(0), "OFF", "DEF", 1, 50, rng(2));

        long freeThrows = data.getEvents().subList(before, data.getEvents().size()).stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).count();
        assertEquals(1, freeThrows,
                "An and-1 is one FT in the bonus exactly as it is under it");
    }

    @Test
    void andOneFreeThrowScoresForTheOFFENSE() {
        // Unlike a rebounding foul (where the FOULED team may be the defense), an
        // and-1 always scores for the shooting team.
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        // Drive many attempts so at least one FT falls.
        for (int i = 0; i < 40; i++) {
            engine.awardAndOne(data, offense.get(0), defense.get(0), "OFF", "DEF",
                    1, 50 + i * 2, rng(100 + i));
        }
        assertTrue(data.getHomeScore() > 0, "Made and-1 FTs must score for the offense");
        assertEquals(0, data.getAwayScore(), "and-1 FTs must never score for the defense");
    }

    @Test
    void andOneNeverFiresOnAMissedShot() {
        // §3.12 RE-BASELINED (#030 A1), not deleted: this used to also assert the
        // shot type was DRIVE/POST (#029 A2's scope fence). §3.12 LIFTED that fence
        // — every made shot rolls the and-1 now, a three included — so that clause
        // is gone and the surviving invariant is the one that still holds: an and-1
        // only ever rides a MADE shot. (That a three CAN draw one is asserted
        // positively by everyShotTypeCanNowDrawAnAndOne in FoulResolverTest.)
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 80, rng(31));

        List<GameData.EventRecord> events = data.getEvents();
        int andOnes = 0;
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() != PlayType.FOUL || !"AND_ONE".equals(e.outcome())) continue;
            andOnes++;
            assertTrue(i > 0, "An AND_ONE can never be the first event");
            GameData.EventRecord prev = events.get(i - 1);
            assertEquals(PlayType.SHOT, prev.playType(),
                    "An and-1 must ride the SHOT event it was drawn on");
            assertTrue(prev.outcome().startsWith("MADE"),
                    "An and-1 only ever follows a MADE shot: " + prev.outcome());
        }
        assertTrue(andOnes > 0, "A long game must produce some and-1s");
    }

    // --- §3.12 all-shot-type contact fouls (decisions.md #030) ---------------

    @Test
    void aStoppedThreeAwardsThreeFreeThrowsAndAStoppedTwoAwardsTwo() {
        // #030 C — the latent bug §3.12 activates and fixes, asserted END-TO-END
        // through the engine rather than only on the enum.
        //
        // A stopped shot emits NO SHOT event (the foul branch returns before
        // recordFieldGoalAttempt), so the shot type is not readable backward from
        // the log. It IS recoverable from the free-throw RUN LENGTH, which is the
        // same derivation the CalibrationHarness uses: a run of 3 is unambiguously
        // a fouled THREE. What this pins is that BOTH lengths occur and NOTHING
        // else does — before §3.12 a 3-run was impossible.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 200, rng(31));

        List<GameData.EventRecord> events = data.getEvents();
        int twoShotTrips = 0;
        int threeShotTrips = 0;
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() != PlayType.FOUL || !"SHOOTING_FOUL".equals(e.outcome())) continue;

            int fts = 0;
            for (int j = i + 1; j < events.size()
                    && events.get(j).playType() == PlayType.FREE_THROW; j++) {
                fts++;
            }
            if (fts == 2) twoShotTrips++;
            else if (fts == 3) threeShotTrips++;
            else fail("A stopped shot awards exactly 2 or 3 FTs, got " + fts);
        }
        assertTrue(twoShotTrips > 0, "A long game must stop some two-point attempts");
        assertTrue(threeShotTrips > 0,
                "A long game must stop some THREES — impossible before §3.12");
        // And the fouled three stays the RARE case (#030 G: ~2% of 3PA).
        assertTrue(threeShotTrips < twoShotTrips,
                "Fouled threes must stay rarer than fouled twos: "
                        + threeShotTrips + " vs " + twoShotTrips);
    }

    @Test
    void anAndOneOnAMadeThreeAwardsExactlyOneFreeThrow() {
        // #030 C's explicit guard against the parallel-graduation wrong turn: only
        // the STOPPED-shot count graduates by shot type. A made 3 + foul is 3
        // points and ONE free throw, not three. The `count` parameter makes the
        // mistake a one-character edit, and it would silently inflate scoring on
        // top of §3.12's real lift — so it is pinned, not just documented.
        //
        // An and-1 on a three is the RAREST event this pass introduces (~0.10 per
        // team per game on the harness — a three must both go in AND draw a foul at
        // 13.3% of the drive rate), so this needs a genuinely long run to observe a
        // few. Several possession-batches rather than one huge game, so the
        // rotation/fatigue state stays realistic.
        int andOnesOnThrees = 0;
        for (long seed = 31; seed < 39; seed++) {
            GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                    "H", "A", 400, rng(seed));

            List<GameData.EventRecord> events = data.getEvents();
            for (int i = 0; i < events.size(); i++) {
                GameData.EventRecord e = events.get(i);
                if (e.playType() != PlayType.FOUL || !"AND_ONE".equals(e.outcome())) continue;
                if (!events.get(i - 1).outcome().contains("3PT")) continue;

                andOnesOnThrees++;
                int fts = 0;
                for (int j = i + 1; j < events.size()
                        && events.get(j).playType() == PlayType.FREE_THROW; j++) {
                    fts++;
                }
                assertEquals(1, fts,
                        "An and-1 on a made THREE awards exactly ONE free throw (#030 C) "
                                + "— NOT three; only the stopped-shot count graduates");
            }
        }
        assertTrue(andOnesOnThrees > 0,
                "This run must produce some and-1s on threes — impossible before §3.12");
    }

    @Test
    void aSingleShotNeverEmitsBothAShootingFoulAndAnAndOne() {
        // #030 B's mutual-exclusivity invariant. The two rolls share ONE multiplier
        // table, which is the thing most likely to be misread as "a shot can be
        // fouled twice". It cannot: the pre-shot branch RETURNS, so a stopped shot
        // never reaches the make/miss roll and thus never reaches the and-1 roll.
        //
        // That is currently guaranteed only by the `return` — a refactor could
        // silently break it — and now that both rolls fire on all four shot types
        // the coincidence is far likelier than it was in §3.11. Hence a test.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 200, rng(31));

        // Anchor on the SHOT event, because ONE ATTEMPT IS ONE SHOT. A stopped shot
        // emits no SHOT event at all (the foul branch returns before
        // recordFieldGoalAttempt), so a SHOOTING_FOUL can never attach to a SHOT —
        // which is precisely what makes the two mutually exclusive. An AND_ONE, by
        // contrast, always rides the SHOT immediately preceding it.
        //
        // (Do NOT try to detect this by walking consecutive FOUL events: an AND_ONE
        // ending one possession and a SHOOTING_FOUL opening the next are adjacent in
        // the log but belong to different attempts — the offTeamId flips between
        // them. That naive walk produces a false positive.)
        List<GameData.EventRecord> events = data.getEvents();
        int andOnesChecked = 0;
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() != PlayType.FOUL || !"AND_ONE".equals(e.outcome())) continue;

            andOnesChecked++;
            // The and-1's shot: immediately before, a MADE SHOT in the same possession.
            GameData.EventRecord shot = events.get(i - 1);
            assertEquals(PlayType.SHOT, shot.playType(),
                    "An and-1 must ride its SHOT event");
            assertEquals(e.offTeamId(), shot.offTeamId(),
                    "An and-1 belongs to the same possession as its shot");

            // Nothing between that shot and this foul, and the foul run that follows
            // is exactly this and-1 — no SHOOTING_FOUL may join the same attempt.
            int j = i + 1;
            while (j < events.size() && events.get(j).playType() == PlayType.FREE_THROW) {
                assertTrue(events.get(j).outcome().endsWith("_AND_ONE"),
                        "Only AND_ONE-tagged FTs may follow an and-1: "
                                + events.get(j).outcome());
                j++;
            }
            if (j < events.size() && events.get(j).playType() == PlayType.FOUL
                    && "SHOOTING_FOUL".equals(events.get(j).outcome())) {
                assertNotEquals(e.offTeamId(), events.get(j).offTeamId(),
                        "A SHOOTING_FOUL following an and-1 in the SAME possession "
                                + "would mean one attempt drew both fouls (#030 B), "
                                + "at event " + i);
            }
        }
        assertTrue(andOnesChecked > 0, "A long game must produce and-1s to check");
    }

    @Test
    void andOneIsFollowedByExactlyOneAndOneTaggedFreeThrow() {
        // #029 B/D end-to-end through the real possession loop: the count is 1 and
        // the source tag lands on the emitted event.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 80, rng(31));

        List<GameData.EventRecord> events = data.getEvents();
        int checked = 0;
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() != PlayType.FOUL || !"AND_ONE".equals(e.outcome())) continue;
            checked++;
            assertTrue(i + 1 < events.size(), "An AND_ONE must be followed by its FT");
            assertEquals(PlayType.FREE_THROW, events.get(i + 1).playType());
            assertTrue(events.get(i + 1).outcome().endsWith("_AND_ONE"),
                    "The FT after an and-1 must carry the AND_ONE source");
            // Exactly ONE: the event after the FT is never another and-1 FT.
            if (i + 2 < events.size()) {
                GameData.EventRecord after = events.get(i + 2);
                assertFalse(after.playType() == PlayType.FREE_THROW
                                && after.outcome().endsWith("_AND_ONE"),
                        "An and-1 awards exactly ONE free throw (#029 B)");
            }
        }
        assertTrue(checked > 0, "Expected at least one and-1 to inspect");
    }

    @Test
    void aMadeAndOneCanStillCarryAnAssist() {
        // #029 A3 (Step 4's coexistence guard): the and-1 roll sits BESIDE the
        // assist roll and the two do not interact — the assist is already stamped
        // on the SHOT event before the and-1 rolls, so a made basket can be both
        // assisted AND an and-1.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 90, rng(77));

        List<GameData.EventRecord> events = data.getEvents();
        boolean foundAssistedAndOne = false;
        for (int i = 1; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() == PlayType.FOUL && "AND_ONE".equals(e.outcome())
                    && events.get(i - 1).assistPlayerId() != null) {
                foundAssistedAndOne = true;
                break;
            }
        }
        assertTrue(foundAssistedAndOne,
                "An and-1 must still be able to be assisted (#029 A3)");
    }

    @Test
    void andOneEventsDoNotDisturbAssistReconciliation() {
        // #029 A3's correctness obligation: a FOUL/FREE_THROW landing between the
        // SHOT and the next possession must not break the assist invariant
        // (assisted SHOT events == box-score assists).
        List<PlayerGameState> home = teamOf5("H", 10);
        List<PlayerGameState> away = teamOf5("A", 10);
        GameData data = simulate(home, away, "H", "A", 80, rng(31));

        long assistedShots = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.SHOT && e.assistPlayerId() != null)
                .count();
        int recordedAssists = 0;
        for (PlayerGameState p : home) recordedAssists += p.getAssists();
        for (PlayerGameState p : away) recordedAssists += p.getAssists();
        assertEquals(assistedShots, recordedAssists,
                "Assist reconciliation must hold with and-1 events in the log");
    }

    @Test
    void everyFreeThrowIsSelfDescribing() {
        // #029 D: at three FT sources the backward join to the preceding FOUL is
        // retired — every FREE_THROW says where it came from, and made/missed
        // stays readable from the prefix (the spelling constraint).
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 80, rng(31));

        Set<String> sources = new HashSet<>();
        for (GameData.EventRecord e : data.getEvents()) {
            if (e.playType() != PlayType.FREE_THROW) continue;
            String outcome = e.outcome();
            assertTrue(outcome.startsWith("MADE_") || outcome.startsWith("MISSED_"),
                    "An FT outcome must stay readable as made/missed: " + outcome);
            sources.add(outcome.substring(outcome.indexOf('_') + 1));
        }
        assertTrue(sources.contains(FreeThrowSource.SHOOTING.name()),
                "A long game must produce shooting-foul FTs");
        assertTrue(sources.contains(FreeThrowSource.AND_ONE.name()),
                "A long game must produce and-1 FTs");
        // §3.14a (#032 G) added a FOURTH source: TECHNICAL. Enumerated off the enum
        // rather than re-listing the names, so a future source cannot make this
        // assertion silently stale the way the hardcoded triple just did.
        Set<String> known = Arrays.stream(FreeThrowSource.values())
                .map(FreeThrowSource::name)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(known.containsAll(sources),
                "Every FT source must be a known FreeThrowSource: " + sources
                        + " (known: " + known + ")");
    }

    @Test
    void freeThrowSourceStampsBothMadeAndMissedOutcomes() {
        assertEquals("MADE_AND_ONE", FreeThrowSource.AND_ONE.outcome(true));
        assertEquals("MISSED_AND_ONE", FreeThrowSource.AND_ONE.outcome(false));
        assertEquals("MADE_SHOOTING", FreeThrowSource.SHOOTING.outcome(true));
        assertEquals("MISSED_SHOOTING", FreeThrowSource.SHOOTING.outcome(false));
        assertEquals("MADE_BONUS", FreeThrowSource.BONUS.outcome(true));
        assertEquals("MISSED_BONUS", FreeThrowSource.BONUS.outcome(false));
        // The FT source strings must not collide with the FOUL outcomes (#029 D's
        // spelling constraint) — those live on a different PlayType, but the tags
        // still must stay distinguishable in a mixed log.
        for (FreeThrowSource source : FreeThrowSource.values()) {
            assertNotEquals("SHOOTING_FOUL", source.outcome(true));
            assertFalse(source.outcome(true).startsWith("REBOUNDING_FOUL"));
        }
    }

    @Test
    void awardFreeThrowsHonoursTheRequestedCount() {
        // #029 D: the count is per-situation, no longer a hard-coded constant —
        // the seam §3.12 reuses to award 3 on a fouled three.
        GameData data = freshData();
        PlayerGameState shooter = teamOf5("OFF", 10).get(0);
        engine.awardFreeThrows(data, shooter, "OFF", "OFF", "DEF", 1, 50,
                3, FreeThrowSource.SHOOTING, rng(9));
        assertEquals(3, data.getEvents().size(), "The count parameter drives the loop");
        assertEquals(3, shooter.getFreeThrowsAttempted());
    }

    @Test
    void shootingFoulAndBonusFreeThrowsStillAwardTwoWithTheirOwnSource() {
        // The re-baseline half of #029 D: the two pre-existing FT sources keep
        // FREE_THROWS_PER_FOUL (2) and now carry their own tags.
        GameData data = simulate(teamOf5("H", 10), teamOf5("A", 10),
                "H", "A", 80, rng(31));

        List<GameData.EventRecord> events = data.getEvents();
        int checked = 0;
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            if (e.playType() != PlayType.FOUL || !"SHOOTING_FOUL".equals(e.outcome())) continue;
            checked++;
            assertTrue(i + 2 < events.size(), "A shooting foul must be followed by 2 FTs");
            for (int ft = 1; ft <= SimConfig.FREE_THROWS_PER_FOUL; ft++) {
                assertEquals(PlayType.FREE_THROW, events.get(i + ft).playType());
                assertTrue(events.get(i + ft).outcome().endsWith("_SHOOTING"),
                        "A shooting foul's FTs carry the SHOOTING source: "
                                + events.get(i + ft).outcome());
            }
        }
        assertTrue(checked > 0, "Expected at least one shooting foul to inspect");
    }

    @Test
    void andOneIsDeterministicForASeed() {
        GameData first = simulate(teamOf5("H", 10), teamOf5("A", 10), "H", "A", 60, rng(5150));
        GameData second = simulate(teamOf5("H", 10), teamOf5("A", 10), "H", "A", 60, rng(5150));
        assertEquals(countAndOnes(first), countAndOnes(second),
                "The and-1 roll consumes the seed at a fixed point — same seed, same count");
        assertEquals(first.getHomeScore(), second.getHomeScore());
        assertEquals(first.getAwayScore(), second.getAwayScore());
    }

    private long countAndOnes(GameData data) {
        return data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL && "AND_ONE".equals(e.outcome()))
                .count();
    }

    /**
     * Which team an event's points score FOR — almost always the offense, with one
     * exception: a §3.10 BONUS free throw awarded when the OFFENSE committed a
     * rebounding foul in the penalty. There the DEFENSE shoots and scores, while
     * the event still carries the possession's offense/defense orientation, so
     * bucketing purely on {@code offTeamId} mis-attributes those points.
     *
     * <p>Latent since §3.10 and exposed (not caused) by §3.12: the engine has always
     * scored these correctly — {@code awardFreeThrows} takes an explicit
     * {@code shootingTeamId} — but this HELPER could not tell, and no seed had
     * reached the offense-commits-in-penalty case until §3.12's RNG shift moved the
     * draws. The fouled (scoring) team is the one that did NOT commit the foul;
     * {@code committingTeamId} (#028 D) is on the FOUL event, not on the
     * FREE_THROW, hence the caller threading the preceding foul's committer in.
     *
     * <p><b>§3.14b: the exception turned out to have THREE sources, not one, so the
     * rule is now stated generally rather than enumerated per-suffix.</b> Two more
     * free-throw kinds can be shot by the team that is NOT on offense:
     * <ul>
     *   <li>{@code _TECHNICAL} (§3.14a) — the OFFENDED team shoots, and it is emitted
     *       between possessions, so whenever the offending team is the one on offense
     *       the shooter is a defender. <b>Latent since §3.14a exactly as the bonus case
     *       was latent since §3.10</b>: no seed had reached it until §3.14b's RNG shift
     *       moved the draws, which is how it surfaced here.</li>
     *   <li>{@code _FLAGRANT} (§3.14b) — an offense-committed rebounding foul that
     *       turns flagrant sends the DEFENSE to the line (#034 C's flipping case).</li>
     * </ul>
     *
     * <p>Enumerating suffixes would mean revisiting this helper for every future source
     * that can be shot by the non-offense — three times in three phases is the signal to
     * stop. <b>The actual rule is simply "the free throws score for whoever did NOT
     * commit the foul"</b>, which is right for every source: on a {@code _SHOOTING} or
     * {@code _AND_ONE} the committer is the defense, so it returns the offense, exactly
     * as the old {@code offTeamId} default did.
     */
    private String scoringTeamId(GameData.EventRecord e, String lastFoulCommitter) {
        if (e.playType() == PlayType.FREE_THROW && lastFoulCommitter != null) {
            return lastFoulCommitter.equals(e.offTeamId()) ? e.defTeamId() : e.offTeamId();
        }
        return e.offTeamId();
    }

    private int pointsFromEvent(GameData.EventRecord e) {
        if (e.playType() == PlayType.SHOT && e.outcome().startsWith("MADE")) {
            return e.outcome().contains("3PT") ? 3 : 2;
        }
        // §3.11 (#029 D): FT outcomes are self-describing (MADE_SHOOTING /
        // MADE_BONUS / MADE_AND_ONE), so this is a prefix read, not an exact match —
        // the MADE/MISSED prefix leads precisely so made-vs-missed stays readable.
        if (e.playType() == PlayType.FREE_THROW && e.outcome().startsWith("MADE")) {
            return 1;
        }
        return 0;
    }

    // ---------- §3.14a: the technical FT shooter rule (decisions.md #032 G) ----------

    /** A five whose free-throw skills ascend, so the "best" pick is unambiguous. */
    private List<PlayerGameState> fiveWithFreeThrowSkills(String teamId, double... fts) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 0; i < fts.length; i++) {
            players.add(TestPlayerFactory.create(teamId + "-p" + i, teamId,
                    10.0, 10.0, 10.0, 10.0, 10.0, 10.0, /*freeThrows*/ fts[i],
                    /*foulDrawing*/ 20.0 - fts[i], 10.0, 10.0, 10.0, 10.0, 10.0));
        }
        return players;
    }

    /**
     * #032 G: a technical FT goes to the best free-throw shooter on the floor,
     * chosen DETERMINISTICALLY — the offended team picks, nobody was fouled.
     */
    @Test
    void theTechnicalFreeThrowShooterIsTheBestFreeThrowShooterOnTheFloor() {
        List<PlayerGameState> five = fiveWithFreeThrowSkills("H", 4.0, 9.0, 18.0, 7.0, 2.0);
        assertEquals("H-p2", engine.pickTechnicalFreeThrowShooter(five).getPlayerId());
    }

    /**
     * #032 G: it consumes NO RNG and is stable across calls — the same player shoots
     * every technical FT for his team until the lineup changes. That looks like a
     * stuck selection in a box score and is exactly the real rule.
     */
    @Test
    void theTechnicalFreeThrowShooterPickIsDeterministicAndConsumesNoRng() {
        List<PlayerGameState> five = fiveWithFreeThrowSkills("H", 4.0, 9.0, 18.0, 7.0, 2.0);
        PlayerGameState first = engine.pickTechnicalFreeThrowShooter(five);
        for (int i = 0; i < 20; i++) {
            assertSame(first, engine.pickTechnicalFreeThrowShooter(five),
                    "The pick must not vary — no RNG is involved (#032 G)");
        }
    }

    /**
     * #032 G, the merge this pass explicitly REFUSED: the bonus-FT draw still weights
     * by foulDrawing and must NOT have become a best-shooter pick. The fixture's
     * foulDrawing runs opposite to freeThrows, so if the two rules had been merged
     * the bonus draw could never reach the low-freeThrows/high-foulDrawing players.
     */
    @Test
    void pickFreeThrowShooterIsUnchangedAndStillFoulDrawingWeighted() {
        List<PlayerGameState> five = fiveWithFreeThrowSkills("H", 4.0, 9.0, 18.0, 7.0, 2.0);
        Set<String> picked = new HashSet<>();
        RandomGenerator rng = rng(99L);
        for (int i = 0; i < 400; i++) {
            picked.add(engine.pickFreeThrowShooter(five, rng).getPlayerId());
        }
        assertTrue(picked.size() > 1,
                "The bonus draw is a WEIGHTED DRAW, not a deterministic max");
        assertTrue(picked.contains("H-p4"),
                "The worst free-throw shooter (but highest foulDrawing) must still be "
                        + "reachable — the bonus rule models being FOULED, not shooting skill");
    }

    // ---------- §3.14a: the technical event + free throw (#032 D/E) ----------

    /**
     * #032 D/E: the emitted event's shape — a FOUL PlayType with the TECHNICAL_FOUL
     * outcome, the committer named, committingTeamId populated (#028 D), and exactly
     * ONE free throw scoring for the OTHER team.
     */
    @Test
    void awardTechnicalFoulEmitsTheFoulAndExactlyOneFreeThrowForTheOtherTeam() {
        List<PlayerGameState> home = teamOf5("H", 10.0);
        List<PlayerGameState> away = fiveWithFreeThrowSkills("A", 4.0, 9.0, 18.0, 7.0, 2.0);
        TeamContext homeCtx = ctx("H", home, CoachModifiers.neutral());
        TeamContext awayCtx = ctx("A", away, CoachModifiers.neutral());
        GameData data = new GameData();
        data.setHomeTeamId("H");
        data.setAwayTeamId("A");
        PlayerGameState committer = home.get(0);

        int next = engine.awardTechnicalFoul(data, committer, homeCtx, awayCtx,
                "H", "A", 1, 1, rng(5L));

        List<GameData.EventRecord> fouls = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL).toList();
        assertEquals(1, fouls.size());
        assertEquals(GameData.TECHNICAL_FOUL_OUTCOME, fouls.get(0).outcome());
        assertEquals(committer.getPlayerId(), fouls.get(0).primaryPlayerId());
        assertEquals("H", fouls.get(0).committingTeamId(),
                "committingTeamId is populated like every other FOUL event (#028 D)");

        List<GameData.EventRecord> fts = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).toList();
        assertEquals(SimConfig.TECHNICAL_FREE_THROWS, fts.size(),
                "A technical is exactly ONE free throw");
        assertEquals("A-p2", fts.get(0).primaryPlayerId(),
                "The OTHER team's best free-throw shooter takes it");
        assertTrue(fts.get(0).outcome().endsWith(FreeThrowSource.TECHNICAL.name()),
                "The FT is self-describing as a TECHNICAL (#029 D)");
        assertEquals(3, next, "sequence advances past the foul and its one FT");
    }

    /**
     * #032 E: the emitted technical must not put its own team in the penalty, even
     * repeated past the bonus threshold. The engine-level counterpart of
     * GameDataTest's unit assertion.
     */
    @Test
    void repeatedTechnicalsNeverPutTheCommittingTeamInTheBonus() {
        List<PlayerGameState> home = teamOf5("H", 10.0);
        List<PlayerGameState> away = teamOf5("A", 10.0);
        TeamContext homeCtx = ctx("H", home, CoachModifiers.neutral());
        TeamContext awayCtx = ctx("A", away, CoachModifiers.neutral());
        GameData data = new GameData();
        data.setHomeTeamId("H");
        data.setAwayTeamId("A");

        int seq = 1;
        for (int i = 0; i < config.bonusFoulsPerPeriod() + 2; i++) {
            seq = engine.awardTechnicalFoul(data, home.get(0), homeCtx, awayCtx,
                    "H", "A", 1, seq, rng(7L));
        }
        assertFalse(data.isInBonus("H", 1, config),
                "Technicals must never reach the penalty tally (#032 E)");
        assertEquals(0, data.periodFoulCount("H", 1));
    }

    /**
     * #032 D: the possession is UNCHANGED. Over a full simulation, no technical event
     * may ever sit between a possession's shot and its resolution in a way that
     * changes the count — the simplest observable form of that is that technicals add
     * FOUL/FREE_THROW events only, and every FT they add reconciles into the score.
     */
    @Test
    void technicalFreeThrowsReconcileIntoTheScore() {
        List<PlayerGameState> home = teamOf5("H", 12.0);
        List<PlayerGameState> away = teamOf5("A", 12.0);
        GameData data = simulate(home, away, "H", "A", 200, rng(31L));

        long technicals = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL)
                .filter(e -> GameData.TECHNICAL_FOUL_OUTCOME.equals(e.outcome()))
                .count();
        assertTrue(technicals > 0, "A 200-possession game must produce some technicals");

        long technicalFts = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW)
                .filter(e -> e.outcome().endsWith(FreeThrowSource.TECHNICAL.name()))
                .count();
        assertEquals(technicals * SimConfig.TECHNICAL_FREE_THROWS, technicalFts,
                "Exactly one technical FT per technical foul");

        int eventPoints = data.getEvents().stream().mapToInt(this::pointsFromEvent).sum();
        assertEquals(data.getHomeScore() + data.getAwayScore(), eventPoints,
                "FT/points reconciliation stays automatic — awardFreeThrows is reused verbatim");
    }

    /**
     * #032 E, at the engine level: across a whole simulation the box-score-facing
     * personal foul counter must never absorb a technical. This is the leak the
     * inverted stop condition (#032 I) is watching for.
     */
    @Test
    void technicalsNeverLeakIntoThePersonalFoulCounters() {
        List<PlayerGameState> home = teamOf5("H", 12.0);
        List<PlayerGameState> away = teamOf5("A", 12.0);
        GameData data = simulate(home, away, "H", "A", 200, rng(31L));

        long personalFoulEvents = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL)
                .filter(e -> !GameData.TECHNICAL_FOUL_OUTCOME.equals(e.outcome()))
                .count();
        int recordedFouls = 0;
        int recordedTechnicals = 0;
        for (PlayerGameState p : home) {
            recordedFouls += p.getFouls();
            recordedTechnicals += p.getTechnicalFouls();
        }
        for (PlayerGameState p : away) {
            recordedFouls += p.getFouls();
            recordedTechnicals += p.getTechnicalFouls();
        }
        assertEquals(personalFoulEvents, recordedFouls,
                "getFouls() counts PERSONAL fouls and nothing else (#032 E)");
        assertTrue(recordedTechnicals > 0, "…and technicals are counted, just separately");
    }

    // ---------- §3.14b: flagrant fouls (decisions.md #034) ----------

    /**
     * A {@link RandomGenerator} whose {@code nextDouble()} returns a scripted sequence,
     * then a fixed tail value. The only way to drive the flagrant fork deterministically:
     * the rate is ~0.0084, so no practical seed search reaches a flagrant AND then the
     * specific sub-case (cap reached, offense-committed, flagrant-2) a test needs.
     *
     * <p>Every other method delegates to a real seeded generator, so the machinery the
     * engine runs between the scripted draws (skill-weighted picks, etc.) still behaves.
     */
    private static final class ScriptedRng implements RandomGenerator {
        private final double[] script;
        private final double tail;
        private final RandomGenerator delegate =
                RandomGeneratorFactory.of("L64X128MixRandom").create(12345L);
        private int i;

        ScriptedRng(double tail, double... script) {
            this.script = script;
            this.tail = tail;
        }

        @Override public double nextDouble() {
            return i < script.length ? script[i++] : tail;
        }
        @Override public long nextLong() { return delegate.nextLong(); }
        @Override public int nextInt() { return delegate.nextInt(); }
        @Override public int nextInt(int bound) { return delegate.nextInt(bound); }
    }

    /**
     * #034 C, the replace-don't-add guard at the STOPPED-SHOT site — the single likeliest
     * bug in this pass, in its most counter-intuitive form.
     *
     * <p>A stopped THREE normally awards <b>3</b> free throws (#030 C). When that same
     * foul turns flagrant the award is <b>2</b>, flat — <i>fewer</i> than the ordinary
     * foul it upgraded. Correct by rule, and exactly the shape a future reader would
     * "fix" into 3, or into 3 + 2 = 5.
     */
    @Test
    void flagrantOnAStoppedThreeAwardsTwoFreeThrowsNotThreeAndNotFive() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        engine.awardFlagrant(data, defense.get(0), offense.get(0), "OFF", "OFF", "DEF",
                1, 50, new ScriptedRng(0.99, /*not a flagrant-2*/ 0.99));

        long freeThrows = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).count();
        assertEquals(2, freeThrows,
                "A flagrant is a FLAT 2 FTs — it REPLACES the underlying award (3 on a "
                        + "stopped THREE), it does not add to it (#034 C)");
        assertEquals(SimConfig.FLAGRANT_FREE_THROWS, freeThrows);
    }

    /**
     * #034 C at the AND-1 site: 2 free throws, not the and-1's 1, and not 1 + 2 = 3.
     */
    @Test
    void flagrantAndOneAwardsTwoFreeThrowsNotOneAndNotThree() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        engine.awardFlagrant(data, defense.get(0), offense.get(0), "OFF", "OFF", "DEF",
                1, 50, new ScriptedRng(0.99, 0.99));

        long freeThrows = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FREE_THROW).count();
        assertEquals(2, freeThrows,
                "A flagrant and-1 is 2 FTs, replacing AND_ONE_FREE_THROWS (1) — not 1+2");
        assertNotEquals(SimConfig.AND_ONE_FREE_THROWS, freeThrows);
    }

    /**
     * #034 C/D/E/I: the emitted shape of a flagrant — the grade on the outcome, the
     * committing team, the FOULED player shooting, and the FT source tag.
     */
    @Test
    void flagrantEmitsTheGradedFoulAndTwoSourceTaggedFreeThrowsForTheFouledPlayer() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        PlayerGameState committer = defense.get(0);
        PlayerGameState fouled = offense.get(2);

        int next = engine.awardFlagrant(data, committer, fouled, "OFF", "OFF", "DEF",
                1, 50, new ScriptedRng(0.99, 0.99));

        List<GameData.EventRecord> events = data.getEvents();
        assertEquals(3, events.size(), "one FOUL + two FREE_THROWs");
        assertEquals(53, next, "sequence advances past the FOUL and both FTs");

        GameData.EventRecord foul = events.get(0);
        assertEquals(PlayType.FOUL, foul.playType());
        assertEquals(PossessionEngine.FLAGRANT_FOUL_1_OUTCOME, foul.outcome());
        assertEquals(committer.getPlayerId(), foul.primaryPlayerId());
        assertEquals("DEF", foul.committingTeamId(),
                "committingTeamId is load-bearing on a flagrant (#034 C)");

        for (GameData.EventRecord ft : events.subList(1, 3)) {
            assertEquals(PlayType.FREE_THROW, ft.playType());
            assertTrue(String.valueOf(ft.outcome()).endsWith("_FLAGRANT"),
                    "FTs carry the new FLAGRANT source (#029 D's suffix), so the harness "
                            + "cannot silently attribute them to a real source");
            assertEquals(fouled.getPlayerId(), ft.primaryPlayerId(),
                    "The player who WAS FOULED shoots (#034 D) — never the best shooter, "
                            + "which is pickTechnicalFreeThrowShooter's rule (#032 G)");
        }
    }

    /**
     * #034 E/F: the severity sub-roll, and the ejection it drives. A flagrant-2 emits
     * the {@code _2} outcome and charges {@code recordFlagrantTwo()}; a flagrant-1 does
     * neither. Both award the same two free throws — the grade changes nothing else.
     */
    @Test
    void flagrantTwoGradesTheOutcomeAndChargesTheEjectionCounterWhileFlagrantOneDoesNot() {
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        GameData two = freshData();
        PlayerGameState ejected = defense.get(0);
        // 0.0 < FLAGRANT_TWO_SHARE ⇒ a flagrant-2.
        engine.awardFlagrant(two, ejected, offense.get(0), "OFF", "OFF", "DEF", 1, 50,
                new ScriptedRng(0.99, 0.0));
        assertEquals(PossessionEngine.FLAGRANT_FOUL_2_OUTCOME, two.getEvents().get(0).outcome());
        assertEquals(1, ejected.getFlagrantTwos());
        assertTrue(ejected.isEjectedForFlagrant(), "one flagrant-2 ejects immediately");

        GameData one = freshData();
        PlayerGameState stays = defense.get(1);
        // 0.99 >= FLAGRANT_TWO_SHARE ⇒ a flagrant-1.
        engine.awardFlagrant(one, stays, offense.get(0), "OFF", "OFF", "DEF", 1, 50,
                new ScriptedRng(0.99, 0.99));
        assertEquals(PossessionEngine.FLAGRANT_FOUL_1_OUTCOME, one.getEvents().get(0).outcome());
        assertEquals(0, stays.getFlagrantTwos());
        assertFalse(stays.isEjectedForFlagrant());

        assertEquals(
                two.getEvents().stream().filter(e -> e.playType() == PlayType.FREE_THROW).count(),
                one.getEvents().stream().filter(e -> e.playType() == PlayType.FREE_THROW).count(),
                "The grade changes NOTHING but the ejection — 2 FTs either way (#034 E)");
    }

    /**
     * #034 I: a flagrant IS a personal foul, so the committer wears it through the
     * ordinary {@code recordFoul()} path — and {@code flagrantTwos} is NOT a parallel
     * foul counter (the #033 D double-count trap in reverse).
     *
     * <p>{@code awardFlagrant} deliberately does not charge the foul itself (every call
     * site has already done so), which is what this asserts: the counters stay separate
     * and the ejection counter never inflates the foul count.
     */
    @Test
    void flagrantTwosCountEjectionCausesNotFoulsSoTheTwoCountersNeverDoubleCount() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        PlayerGameState committer = defense.get(0);

        committer.recordFoul(); // what every call site does before rolling the flagrant
        engine.awardFlagrant(data, committer, offense.get(0), "OFF", "OFF", "DEF", 1, 50,
                new ScriptedRng(0.99, 0.0));

        assertEquals(1, committer.getFouls(),
                "The flagrant is ONE personal foul — recordFlagrantTwo() must not add a second");
        assertEquals(1, committer.getFlagrantTwos(),
                "…and the ejection cause is counted separately (#034 F/I)");
        assertEquals(0, committer.getTechnicalFouls(),
                "§3.14a's counter split does NOT apply to flagrants (#032 E vs #034 I)");
    }

    /**
     * #034 I + #032's follow-up: <b>the bonus tally is UNCHANGED, asserted rather than
     * left to inspection.</b> A flagrant is a personal foul, so unlike §3.14a's technical
     * it COUNTS toward the penalty — meaning {@code countsTowardBonus} needs no new entry
     * and {@code GameData.isInBonus} must treat it like any other foul.
     *
     * <p>This is pinned by a test because the exclusion lives in TWO independent
     * derivations ({@code GameData.isInBonus} and the harness's own per-team-period
     * tally), and "we changed nothing" is otherwise indistinguishable from "we forgot".
     */
    @Test
    void flagrantFoulsCountTowardTheBonusTallyUnlikeTechnicals() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        seedFouls(data, "DEF", 1, config.bonusFoulsPerPeriod() - 1);
        assertFalse(data.isInBonus("DEF", 1, config), "precondition: one short of the penalty");

        engine.awardFlagrant(data, defense.get(0), offense.get(0), "OFF", "OFF", "DEF",
                1, 50, new ScriptedRng(0.99, 0.99));

        assertTrue(data.isInBonus("DEF", 1, config),
                "A flagrant is a PERSONAL foul and must reach the penalty tally (#034 I) — "
                        + "the opposite of §3.14a's TECHNICAL_FOUL exclusion (#032 E)");
        assertEquals(config.bonusFoulsPerPeriod(),
                data.periodFoulCount("DEF", 1),
                "…counted exactly once, not twice");
    }

    /**
     * #034 C: at the rebounding site — the ONLY two-sided one — a DEFENSIVE flagrant
     * leaves the offense the ball, and the fouled OFFENSIVE player shoots.
     */
    @Test
    void defensiveReboundingFlagrantRetainsForTheOffenseAndTheOffensivePlayerShoots() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        ReboundFoul foul = new ReboundFoul(ReboundFoul.Side.DEFENSE, defense.get(0));

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, foul, offense, defense, "OFF", "DEF", 1, 50,
                /*capReached*/ false, new ScriptedRng(0.5, /*flagrant*/ 0.0, /*grade*/ 0.99));

        assertTrue(result.offenseRetains(),
                "A defensive flagrant returns the ball to the offense (#034 B/C)");
        assertEquals(PossessionEngine.FLAGRANT_FOUL_1_OUTCOME,
                data.getEvents().get(0).outcome(),
                "…and REPLACES the REBOUNDING_FOUL_DEFENSE event, it does not add to it");
        assertTrue(data.getEvents().stream()
                        .filter(e -> e.playType() == PlayType.FREE_THROW)
                        .allMatch(e -> e.primaryPlayerId().startsWith("OFF")),
                "The fouled OFFENSIVE player shoots (#034 D)");
    }

    /**
     * #034 C: the flipping case — the only one in the pass, and the reason the
     * rebounding site is the only site that needs a fork at all. An OFFENSE-committed
     * flagrant sends the DEFENSE to the line and ends the possession.
     */
    @Test
    void offensiveReboundingFlagrantFlipsThePossessionAndTheDefensivePlayerShoots() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        ReboundFoul foul = new ReboundFoul(ReboundFoul.Side.OFFENSE, offense.get(0));

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, foul, offense, defense, "OFF", "DEF", 1, 50,
                /*capReached*/ false, new ScriptedRng(0.5, /*flagrant*/ 0.0, /*grade*/ 0.99));

        assertFalse(result.offenseRetains(), "An offensive flagrant flips the possession");
        assertEquals("OFF", data.getEvents().get(0).committingTeamId(),
                "committingTeamId is load-bearing here, not merely uniform (#028 D / #034 C)");
        assertTrue(data.getEvents().stream()
                        .filter(e -> e.playType() == PlayType.FREE_THROW)
                        .allMatch(e -> e.primaryPlayerId().startsWith("DEF")),
                "The fouled DEFENSIVE player shoots");
    }

    /**
     * #034 B: <b>the cap is respected</b>, and it degrades exactly as §3.10's does — the
     * free throws are still awarded, and only the retention is refused. This is the
     * assertion that would catch an uncapped retention loop, one of the three named
     * suspects if the §3.4 aggregates ever move (#034 J).
     */
    @Test
    void aFlagrantAtTheRetentionCapStillAwardsFreeThrowsButEndsThePossession() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);
        ReboundFoul foul = new ReboundFoul(ReboundFoul.Side.DEFENSE, defense.get(0));

        PossessionEngine.ReboundFoulResult result = engine.resolveReboundFoul(
                data, foul, offense, defense, "OFF", "DEF", 1, 50,
                /*capReached*/ true, new ScriptedRng(0.5, /*flagrant*/ 0.0, /*grade*/ 0.99));

        assertFalse(result.offenseRetains(),
                "The flagrant retention is NOT exempt from "
                        + "MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION (#034 B)");
        assertEquals(2, data.getEvents().stream()
                        .filter(e -> e.playType() == PlayType.FREE_THROW).count(),
                "…but the FTs are still awarded — the §3.10 degradation shape");
    }

    /**
     * #034 B, the whole-loop view: a flagrant at the stopped-shot site RE-ENTERS the
     * second-chance loop rather than ending the possession — the first free-throw path
     * in the engine that does not end it (#030 B's invariant, broken deliberately).
     *
     * <p>Driven through {@code resolvePossession} rather than a helper so the actual
     * {@code continue} is exercised: a retained possession must produce a SECOND shot
     * attempt after the free throws.
     */
    @Test
    void aStoppedShotFlagrantReEntersTheLoopSoThePossessionContinuesAfterTheFreeThrows() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        // The draw order at the top of the loop: pickShooter, pickShotType,
        // pickDefender (three weighted picks — 0.5 is a mid-list pick for each), then
        // the turnover gate (0.99 = no turnover), the foul roll (0.0 = foul), the
        // flagrant roll (0.0 = flagrant) and the severity sub-roll (0.99 = grade 1).
        // The 0.5 tail then lets the retained possession play out normally.
        resolvePossession(data, offense, defense, "OFF", "DEF", 1, 1,
                new ScriptedRng(0.5, 0.5, 0.5, 0.5, 0.99, 0.0, 0.0, 0.99));

        List<GameData.EventRecord> events = data.getEvents();
        assertEquals(PossessionEngine.FLAGRANT_FOUL_1_OUTCOME, events.get(0).outcome());
        assertEquals(2, events.stream()
                        .filter(e -> e.playType() == PlayType.FREE_THROW).count(),
                "2 FTs, REPLACING the stopped shot's 2-or-3 (#034 C)");
        assertTrue(events.stream().anyMatch(e -> e.playType() == PlayType.SHOT
                        || e.playType() == PlayType.TURNOVER),
                "The offense KEPT the ball: the possession ran another action after the "
                        + "free throws, via the same offensiveRetentions++/continue §3.7/§3.8/"
                        + "§3.10 use (#034 B)");
    }

    /**
     * #034 B at the two SHOOTING sites: the retention respects
     * {@code MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION} there too, degrading exactly as
     * §3.10's does — the free throws are still awarded, only the retention is refused.
     *
     * <p>Reaching the cap needs the loop to retain three times first, so the script
     * drives three flagrant stopped shots and then a fourth: the fourth must END the
     * possession. Without this the cap-reached branch at these two sites is unreachable
     * in practice (a flagrant is ~0.0084 per foul, so four in one possession never
     * occurs naturally) — which is precisely why it is worth pinning.
     */
    @Test
    void flagrantRetentionAtTheShootingSitesStopsAtTheSecondChanceCap() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        // Per loop iteration: 3 weighted picks, no-turnover, foul, flagrant, grade-1,
        // then the two free-throw make rolls awardFreeThrows consumes (0.5 = made).
        double[] iteration = { 0.5, 0.5, 0.5, 0.99, 0.0, 0.0, 0.99, 0.5, 0.5 };
        double[] script = new double[iteration.length * 4];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(iteration, 0, script, i * iteration.length, iteration.length);
        }
        resolvePossession(data, offense, defense, "OFF", "DEF", 1, 1,
                new ScriptedRng(0.5, script));

        long flagrants = data.getEvents().stream()
                .filter(e -> e.playType() == PlayType.FOUL).count();
        assertEquals(config.maxOffensiveRetentionsPerPossession() + 1, flagrants,
                "The offense retains up to the cap and no further — the flagrant is NOT "
                        + "exempt from MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION (#034 B)");
        assertEquals(flagrants * SimConfig.FLAGRANT_FREE_THROWS,
                data.getEvents().stream()
                        .filter(e -> e.playType() == PlayType.FREE_THROW).count(),
                "…and every one of them still awarded its two FTs, cap or no cap");
    }

    /**
     * #034 B/C at the AND-1 site specifically: the flagrant and-1 retains, and it too
     * stops at the cap. This is the three-scoring-channel case — made FG + 2 FTs + the
     * ball back — so the assertion doubles as the guard that the basket is scored
     * <b>once</b>, not re-scored by the flagrant path.
     */
    @Test
    void flagrantAndOneRetainsScoresTheBasketOnceAndStopsAtTheCap() {
        GameData data = freshData();
        List<PlayerGameState> offense = teamOf5("OFF", 10);
        List<PlayerGameState> defense = teamOf5("DEF", 10);

        // Per iteration: 3 picks, no-turnover, no-foul, no-block, MADE, assist-roll
        // (0.99 = unassisted), and-1 (0.0 = yes), flagrant (0.0), grade-1 (0.99),
        // then the two FT make rolls.
        double[] iteration = { 0.5, 0.5, 0.5, 0.99, 0.99, 0.99, 0.0, 0.99, 0.0, 0.0, 0.99, 0.5, 0.5 };
        double[] script = new double[iteration.length * 4];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(iteration, 0, script, i * iteration.length, iteration.length);
        }
        resolvePossession(data, offense, defense, "OFF", "DEF", 1, 1,
                new ScriptedRng(0.5, script));

        List<GameData.EventRecord> events = data.getEvents();
        long madeShots = events.stream()
                .filter(e -> e.playType() == PlayType.SHOT)
                .filter(e -> String.valueOf(e.outcome()).startsWith("MADE")).count();
        long flagrants = events.stream()
                .filter(e -> e.playType() == PlayType.FOUL)
                .filter(e -> String.valueOf(e.outcome()).startsWith("FLAGRANT")).count();

        assertEquals(config.maxOffensiveRetentionsPerPossession() + 1, flagrants,
                "The flagrant and-1 retains up to the cap and no further (#034 B)");
        assertEquals(flagrants, madeShots,
                "Each retained iteration scored EXACTLY ONE basket — the flagrant path "
                        + "must not re-score the make it rides (#034 C)");
        assertEquals(0, events.stream()
                        .filter(e -> "AND_ONE".equals(e.outcome())).count(),
                "…and the ordinary AND_ONE event never fires: the flagrant REPLACES it");
        assertEquals(flagrants * SimConfig.FLAGRANT_FREE_THROWS,
                events.stream().filter(e -> e.playType() == PlayType.FREE_THROW).count(),
                "2 FTs each, NOT the and-1's 1 and not 1+2 (#034 C)");
    }

}
