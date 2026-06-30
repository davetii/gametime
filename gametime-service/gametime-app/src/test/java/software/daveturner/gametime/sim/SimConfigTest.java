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
}
