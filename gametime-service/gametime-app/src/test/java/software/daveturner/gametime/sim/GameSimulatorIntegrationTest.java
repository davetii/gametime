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

        assertEquals(10, boxScores.size(), "5 starters per team = 10 box scores");

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
        assertTrue(result.getHomeScore() >= 50 && result.getHomeScore() <= 160,
                "Home score: " + result.getHomeScore());
        assertTrue(result.getAwayScore() >= 50 && result.getAwayScore() <= 160,
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

    private int pointsFromEntity(GameEventEntity e) {
        if (e.getPlayType() == PlayType.SHOT && e.getOutcome().startsWith("MADE")) {
            return e.getOutcome().contains("3PT") ? 3 : 2;
        }
        if (e.getPlayType() == PlayType.FREE_THROW && "MADE".equals(e.getOutcome())) {
            return 1;
        }
        return 0;
    }
}
