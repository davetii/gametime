package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.repo.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GameSimulatorIntegrationTest {

    @Autowired
    GameSimulator simulator;

    @Autowired
    GameRepo gameRepo;

    @Autowired
    GameEventRepo gameEventRepo;

    @Autowired
    BoxScoreRepo boxScoreRepo;

    @Test
    void simulateProducesPersistedGameWithFinalStatus() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        assertNotNull(result.getGameId());
        assertEquals("BOS", result.getHomeTeamId());
        assertEquals("LA", result.getAwayTeamId());
        assertTrue(result.getHomeScore() >= 0);
        assertTrue(result.getAwayScore() >= 0);
        assertTrue(result.getTotalEvents() > 0);

        GameEntity game = gameRepo.findById(result.getGameId()).orElseThrow();
        assertEquals(GameStatus.FINAL, game.getStatus());
        assertEquals(result.getHomeScore(), game.getHomeScore());
        assertEquals(result.getAwayScore(), game.getAwayScore());
    }

    @Test
    void simulateProducesEventsInSequenceOrder() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        assertFalse(events.isEmpty());

        int prevSeq = 0;
        for (GameEventEntity e : events) {
            assertTrue(e.getSequence() > prevSeq);
            prevSeq = e.getSequence();
            assertNotNull(e.getPlayType());
            assertNotNull(e.getOutcome());
            assertNotNull(e.getOffenseTeamId());
            assertNotNull(e.getDefenseTeamId());
        }
    }

    @Test
    void simulateProducesBoxScoresThatReconcileWithEvents() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        // §3.5: bench players who checked in also get box-score rows, so there are
        // now more than 10 (but at least the 10 starters).
        assertTrue(boxScores.size() >= 10,
                "At least 10 box scores (5 starters/team); benches add more: " + boxScores.size());

        int totalBoxPoints = boxScores.stream().mapToInt(BoxScoreEntity::getPoints).sum();
        int totalEventPoints = events.stream().mapToInt(this::pointsFromEntity).sum();
        assertEquals(totalEventPoints, totalBoxPoints, "Box score points must reconcile with events");
        assertEquals(result.getHomeScore() + result.getAwayScore(), totalBoxPoints);
    }

    @Test
    void simulateIsDeterministic() {
        SimResult r1 = simulator.simulate("CHI", "NY", 123L, 25);
        SimResult r2 = simulator.simulate("CHI", "NY", 123L, 25);

        assertEquals(r1.getHomeScore(), r2.getHomeScore());
        assertEquals(r1.getAwayScore(), r2.getAwayScore());
        assertEquals(r1.getPeriods(), r2.getPeriods());
        assertEquals(r1.getTotalEvents(), r2.getTotalEvents());
    }

    @Test
    void simulateDifferentSeedsDifferentResults() {
        SimResult r1 = simulator.simulate("MIA", "PHI", 100L, 25);
        SimResult r2 = simulator.simulate("MIA", "PHI", 200L, 25);

        boolean anyDiff = r1.getHomeScore() != r2.getHomeScore()
                || r1.getAwayScore() != r2.getAwayScore()
                || r1.getTotalEvents() != r2.getTotalEvents();
        assertTrue(anyDiff, "Different seeds should produce different games");
    }

    @Test
    void simulateProducesBelivableScore() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);
        // Plausibility bound (not a calibration gate — the harness guards the mean,
        // which sits ~113/team). A strong offensive team on a hot seed can top 160.
        assertTrue(result.getHomeScore() >= 50 && result.getHomeScore() <= 175,
                "Home score: " + result.getHomeScore());
        assertTrue(result.getAwayScore() >= 50 && result.getAwayScore() <= 175,
                "Away score: " + result.getAwayScore());
    }

    @Test
    void simulateProducesNonZeroRebounds() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        int totalOff = boxScores.stream().mapToInt(BoxScoreEntity::getOffensiveRebounds).sum();
        int totalDef = boxScores.stream().mapToInt(BoxScoreEntity::getDefensiveRebounds).sum();

        assertTrue(totalOff + totalDef > 0,
                "§3.3: box-score rebounds must no longer be all zeros");
        // Every missed shot is rebounded, so defensive rebounds should be common.
        assertTrue(totalDef > 0, "Should record defensive rebounds");
    }

    @Test
    void simulateReboundsReconcileWithReboundEvents() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        long offReboundEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && "OFFENSIVE".equals(e.getOutcome()))
                .count();
        long defReboundEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && "DEFENSIVE".equals(e.getOutcome()))
                .count();

        int boxOff = boxScores.stream().mapToInt(BoxScoreEntity::getOffensiveRebounds).sum();
        int boxDef = boxScores.stream().mapToInt(BoxScoreEntity::getDefensiveRebounds).sum();

        assertEquals(offReboundEvents, boxOff,
                "Offensive rebound box totals must match OFFENSIVE rebound events");
        assertEquals(defReboundEvents, boxDef,
                "Defensive rebound box totals must match DEFENSIVE rebound events");
    }

    @Test
    void simulateOutOfBoundsEventsExistAndAreExcludedFromReboundReconciliation() {
        // §3.8 (decisions.md #026 E): a missed shot can leave the court OOB — a
        // REBOUND-PlayType event with an OUT_OF_BOUNDS_* outcome that credits NO
        // rebounder. Such events must exist AND must be excluded from the shipped
        // rebound reconciliation invariant (which exact-matches OFFENSIVE/DEFENSIVE),
        // so the box-score rebound totals still reconcile with only the true rebounds.
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        List<GameEventEntity> oobEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && e.getOutcome() != null
                        && e.getOutcome().startsWith("OUT_OF_BOUNDS"))
                .toList();
        assertFalse(oobEvents.isEmpty(),
                "§3.8: a full game should emit OUT_OF_BOUNDS rebound events");
        for (GameEventEntity e : oobEvents) {
            assertNull(e.getPrimaryPlayerId(), "OOB credits no rebounder: " + e.getOutcome());
        }

        long offReboundEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && "OFFENSIVE".equals(e.getOutcome()))
                .count();
        long defReboundEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.REBOUND
                        && "DEFENSIVE".equals(e.getOutcome()))
                .count();
        int boxOff = boxScores.stream().mapToInt(BoxScoreEntity::getOffensiveRebounds).sum();
        int boxDef = boxScores.stream().mapToInt(BoxScoreEntity::getDefensiveRebounds).sum();

        assertEquals(offReboundEvents, boxOff,
                "OOB present, offensive rebound totals still reconcile (OOB excluded)");
        assertEquals(defReboundEvents, boxDef,
                "OOB present, defensive rebound totals still reconcile (OOB excluded)");
    }

    @Test
    void simulateProducesNonZeroAssists() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        int totalAssists = boxScores.stream().mapToInt(BoxScoreEntity::getAssists).sum();

        assertTrue(totalAssists > 0,
                "§3.4: box-score assists must no longer be all zeros");
    }

    @Test
    void simulateAssistsReconcileWithAssistedShotEvents() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        // Every assisted-SHOT event carries an assister; box-score assists must
        // equal that count (decisions.md #022/#020 — events are the source of truth).
        long assistedShotEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.SHOT && e.getAssistPlayerId() != null)
                .count();
        int boxAssists = boxScores.stream().mapToInt(BoxScoreEntity::getAssists).sum();

        assertEquals(assistedShotEvents, boxAssists,
                "Box-score assists must reconcile with assisted SHOT events");

        // Assisters are only ever stamped on made field goals.
        for (GameEventEntity e : events) {
            if (e.getAssistPlayerId() != null) {
                assertEquals(PlayType.SHOT, e.getPlayType());
                assertTrue(e.getOutcome().startsWith("MADE"),
                        "Only made shots carry an assister");
            }
        }
    }

    // --- §3.5 minutes / fatigue / substitution (decisions.md #023) ---

    @Test
    void benchPlayersAppearInBoxScores() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        // BOS and LA both carry benches, so more than the 10 starters play.
        assertTrue(boxScores.size() > 10,
                "§3.5: bench players who checked in get box-score rows: " + boxScores.size());
    }

    @Test
    void teamMinutesSumToFiveTimesGameMinutes() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        GameEntity game = gameRepo.findById(result.getGameId()).orElseThrow();

        int gameMinutes = SimConfig.PERIODS * SimConfig.MINUTES_PER_PERIOD
                + Math.max(0, game.getPeriods() - SimConfig.PERIODS) * SimConfig.OT_MINUTES;

        // Both teams are 5-on-the-floor every possession, so all box-score minutes
        // sum to 10 × game minutes (5 per team). Per-player integer rounding can
        // drift a couple of minutes off the exact total.
        int totalMinutes = boxScores.stream().mapToInt(BoxScoreEntity::getMinutes).sum();
        assertEquals(10 * gameMinutes, totalMinutes, 8,
                "All box-score minutes should sum to ~10 × game minutes (5 per team)");
    }

    @Test
    void everyPlayerWithAMinuteHasAPositiveShare() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        for (BoxScoreEntity b : boxScores) {
            // Every persisted row belongs to a player who took the floor.
            assertTrue(b.getMinutes() >= 0, "minutes are non-negative");
        }
        // At least the starters log real minutes.
        long withMinutes = boxScores.stream().filter(b -> b.getMinutes() > 0).count();
        assertTrue(withMinutes >= 10, "at least the 10 starters log minutes: " + withMinutes);
    }

    @Test
    void noOnFloorPlayerExceedsFoulOutLimit() {
        // A foul-out must actually remove a player: no box score should show a
        // player who kept accumulating minutes past FOUL_OUT_LIMIT fouls. (Fouls
        // can equal the limit — that's the DQ threshold — but a fouled-out player
        // stops playing, so their minutes are bounded well under a full game.)
        for (long seed : new long[]{1L, 7L, 42L, 99L, 123L}) {
            SimResult result = simulator.simulate("BOS", "LA", seed, 25);
            List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
            GameEntity game = gameRepo.findById(result.getGameId()).orElseThrow();
            int gameMinutes = SimConfig.PERIODS * SimConfig.MINUTES_PER_PERIOD
                    + Math.max(0, game.getPeriods() - SimConfig.PERIODS) * SimConfig.OT_MINUTES;
            for (BoxScoreEntity b : boxScores) {
                assertTrue(b.getFouls() <= SimConfig.FOUL_OUT_LIMIT,
                        "fouls never exceed the DQ limit (seed " + seed + ")");
                if (b.getFouls() >= SimConfig.FOUL_OUT_LIMIT) {
                    assertTrue(b.getMinutes() < gameMinutes,
                            "a fouled-out player cannot log a full game (seed " + seed + ")");
                }
            }
        }
    }

    // --- §3.7 blocked shots (decisions.md #025) ---

    @Test
    void simulateProducesNonZeroBlocks() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());
        int totalBlocks = boxScores.stream().mapToInt(BoxScoreEntity::getBlocks).sum();

        assertTrue(totalBlocks > 0,
                "§3.7: box-score blocks must no longer be hardcoded 0");
    }

    @Test
    void simulateBlocksReconcileWithBlockedShotEvents() {
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);

        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        // Reconciliation invariant (#025 F, extends #020/#022): count of SHOT events
        // with outcome LIKE 'BLOCKED%' == sum of BoxScore.blocks (events are the
        // source of truth). A block is a field-goal outcome mirroring how a steal is
        // a turnover outcome.
        long blockedShotEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.SHOT
                        && e.getOutcome().startsWith("BLOCKED"))
                .count();
        int boxBlocks = boxScores.stream().mapToInt(BoxScoreEntity::getBlocks).sum();

        assertEquals(blockedShotEvents, boxBlocks,
                "Box-score blocks must reconcile with BLOCKED SHOT events");

        // A blocked shot is a SHOT that is neither made nor assisted, and it counts
        // as a field-goal attempt (F1/F3/F4).
        for (GameEventEntity e : events) {
            if (e.getPlayType() == PlayType.SHOT && e.getOutcome().startsWith("BLOCKED")) {
                assertNull(e.getAssistPlayerId(), "a blocked shot carries no assister");
                assertFalse(e.getOutcome().startsWith("MADE"), "a block is never a make");
            }
        }
    }

    @Test
    void committingTeamIdPersistsAndRoundTripsForEveryFoul() {
        // §3.10 (#028 D): the one schema change must actually survive the H2 write/
        // read cycle — a nullable column that silently dropped its value would break
        // the penalty derivation without failing anything else.
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);
        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());

        List<GameEventEntity> fouls = events.stream()
                .filter(e -> e.getPlayType() == PlayType.FOUL).toList();
        assertFalse(fouls.isEmpty(), "A full game should persist FOUL events");

        for (GameEventEntity foul : fouls) {
            assertNotNull(foul.getCommittingTeamId(),
                    "Every persisted FOUL must carry a committing team: " + foul.getOutcome());
            assertTrue(foul.getCommittingTeamId().equals(foul.getOffenseTeamId())
                            || foul.getCommittingTeamId().equals(foul.getDefenseTeamId()),
                    "The committing team must be one of the two teams on the floor");
            if ("SHOOTING_FOUL".equals(foul.getOutcome())) {
                assertEquals(foul.getDefenseTeamId(), foul.getCommittingTeamId(),
                        "A shooting foul is always committed by the defense");
            }
            if ("REBOUNDING_FOUL_OFFENSE".equals(foul.getOutcome())) {
                assertEquals(foul.getOffenseTeamId(), foul.getCommittingTeamId(),
                        "An over-the-back is committed by the OFFENSE (#028 A2)");
            }
            if ("REBOUNDING_FOUL_DEFENSE".equals(foul.getOutcome())) {
                assertEquals(foul.getDefenseTeamId(), foul.getCommittingTeamId());
            }
        }
    }

    @Test
    void nonFoulEventsCarryNoCommittingTeam() {
        // The column is a FOUL fact, not a general "who did this" field — leaving it
        // null elsewhere keeps the penalty count unambiguous.
        SimResult result = simulator.simulate("BOS", "LA", 42L, 25);
        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());

        for (GameEventEntity e : events) {
            if (e.getPlayType() != PlayType.FOUL) {
                assertNull(e.getCommittingTeamId(),
                        "Only FOUL events carry a committing team, saw it on " + e.getPlayType());
            }
        }
    }

    @Test
    void bonusFreeThrowsScoreForTheFouledTeamAndStillReconcile() {
        // §3.10's reconciliation invariant: bonus FTs are new points, so the
        // points-vs-event-log identity (#020) must still hold with them in.
        SimResult result = simulator.simulate("CHI", "NY", 7L, 40);
        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        // There should be at least one rebounding foul at this length, and the
        // FT/points identity must survive it.
        long reboundingFouls = events.stream()
                .filter(e -> e.getPlayType() == PlayType.FOUL
                        && e.getOutcome().startsWith("REBOUNDING_FOUL"))
                .count();
        assertTrue(reboundingFouls > 0, "Expected rebounding fouls in a 40-possession game");

        int totalBoxPoints = boxScores.stream().mapToInt(BoxScoreEntity::getPoints).sum();
        int totalEventPoints = events.stream().mapToInt(this::pointsFromEntity).sum();
        assertEquals(totalEventPoints, totalBoxPoints,
                "Points must still reconcile with the event log once bonus FTs exist");
        assertEquals(result.getHomeScore() + result.getAwayScore(), totalBoxPoints);
    }

    @Test
    void everyReboundingFoulIncrementsAPlayerFoulCount() {
        // The rebounding foul feeds the per-player fouls counter (foul-outs, #023 F)
        // exactly like a shooting foul — no new box-score counter (#028 scope).
        SimResult result = simulator.simulate("CHI", "NY", 7L, 40);
        List<GameEventEntity> events = gameEventRepo
                .findByGameIdOrderBySequenceAsc(result.getGameId());
        List<BoxScoreEntity> boxScores = boxScoreRepo.findByGameId(result.getGameId());

        long foulEvents = events.stream()
                .filter(e -> e.getPlayType() == PlayType.FOUL)
                .filter(e -> e.getPrimaryPlayerId() != null)
                .count();
        int boxFouls = boxScores.stream().mapToInt(b -> b.getFouls() == null ? 0 : b.getFouls()).sum();
        assertEquals(foulEvents, boxFouls,
                "Box-score fouls must reconcile with FOUL events (both kinds)");
    }

    private int pointsFromEntity(GameEventEntity e) {
        if (e.getPlayType() == PlayType.SHOT && e.getOutcome().startsWith("MADE")) {
            return e.getOutcome().contains("3PT") ? 3 : 2;
        }
        // §3.11 (#029 D): FT outcomes are self-describing (MADE_SHOOTING /
        // MADE_BONUS / MADE_AND_ONE), so this is a prefix read, not an exact match.
        if (e.getPlayType() == PlayType.FREE_THROW && e.getOutcome().startsWith("MADE")) {
            return 1;
        }
        return 0;
    }
}
