package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimConfigTest {

    private final SimConfig config = new SimConfig();

    @Test
    void clampProbabilityEnforcesFloor() {
        assertEquals(SimConfig.PROB_FLOOR, config.clampProbability(-0.5));
        assertEquals(SimConfig.PROB_FLOOR, config.clampProbability(0.0));
    }

    @Test
    void clampProbabilityEnforcesCeiling() {
        assertEquals(SimConfig.PROB_CEILING, config.clampProbability(1.5));
        assertEquals(SimConfig.PROB_CEILING, config.clampProbability(1.0));
    }

    @Test
    void clampProbabilityPassesThroughMidValues() {
        assertEquals(0.5, config.clampProbability(0.5));
        assertEquals(0.42, config.clampProbability(0.42));
    }

    @Test
    void contestProbabilityEqualSkillsReturnsBase() {
        double result = config.contestProbability(0.42, 10.0, 10.0);
        assertEquals(0.42, result, 0.001);
    }

    @Test
    void contestProbabilityHighOffenseIncreasesProb() {
        double result = config.contestProbability(0.42, 20.0, 10.0);
        assertTrue(result > 0.42);
    }

    @Test
    void contestProbabilityHighDefenseDecreasesProb() {
        double result = config.contestProbability(0.42, 10.0, 20.0);
        assertTrue(result < 0.42);
    }

    @Test
    void contestProbabilityClampedAtExtremes() {
        double high = config.contestProbability(0.90, 20.0, 1.0);
        assertTrue(high <= SimConfig.PROB_CEILING);

        double low = config.contestProbability(0.10, 1.0, 20.0);
        assertTrue(low >= SimConfig.PROB_FLOOR);
    }

    @Test
    void freeThrowProbabilityAverageSkill() {
        double result = config.freeThrowProbability(10.0);
        assertEquals(0.75, result, 0.001);
    }

    @Test
    void freeThrowProbabilityHighSkill() {
        double result = config.freeThrowProbability(20.0);
        assertTrue(result > 0.75);
        assertTrue(result <= SimConfig.PROB_CEILING);
    }

    @Test
    void freeThrowProbabilityLowSkill() {
        double result = config.freeThrowProbability(1.0);
        assertTrue(result < 0.75);
        assertTrue(result >= SimConfig.PROB_FLOOR);
    }

    // --- §3.4 coach / chemistry helpers (decisions.md #022) ---

    @Test
    void coachModifierAverageAttributeIsNoEffect() {
        assertEquals(1.0, config.coachModifier(10), 0.0001);
    }

    @Test
    void coachModifierNullAttributeIsNoEffect() {
        assertEquals(1.0, config.coachModifier(null), 0.0001);
    }

    @Test
    void coachModifierAboveAverageExceedsOne() {
        assertEquals(1.0 + SimConfig.COACH_SENSITIVITY, config.coachModifier(20), 0.0001);
        assertTrue(config.coachModifier(15) > 1.0);
    }

    @Test
    void coachModifierBelowAverageBelowOne() {
        assertTrue(config.coachModifier(5) < 1.0);
        assertTrue(config.coachModifier(1) < 1.0);
    }

    @Test
    void assistProbabilityAveragePassingReturnsBase() {
        assertEquals(SimConfig.BASE_ASSIST, config.assistProbability(10.0), 0.0001);
    }

    @Test
    void assistProbabilityHigherPassingAssistsMore() {
        assertTrue(config.assistProbability(20.0) > SimConfig.BASE_ASSIST);
    }

    @Test
    void assistProbabilityLowerPassingAssistsLess() {
        assertTrue(config.assistProbability(1.0) < SimConfig.BASE_ASSIST);
    }

    @Test
    void assistProbabilityClampedToValidRange() {
        assertTrue(config.assistProbability(20.0) <= SimConfig.PROB_CEILING);
        assertTrue(config.assistProbability(1.0) >= SimConfig.PROB_FLOOR);
    }

    @Test
    void chemistryMakeMultiplierAllAverageIsNoEffect() {
        assertEquals(1.0, config.chemistryMakeMultiplier(10.0, 10.0, 10.0), 0.0001);
    }

    @Test
    void chemistryMakeMultiplierHighAcumenLifts() {
        assertTrue(config.chemistryMakeMultiplier(20.0, 10.0, 10.0) > 1.0);
    }

    @Test
    void chemistryMakeMultiplierTeamOffenseEdgeLifts() {
        assertTrue(config.chemistryMakeMultiplier(10.0, 20.0, 10.0) > 1.0);
    }

    @Test
    void chemistryMakeMultiplierStrongOppDefenseSuppresses() {
        assertTrue(config.chemistryMakeMultiplier(10.0, 10.0, 20.0) < 1.0);
    }
}
