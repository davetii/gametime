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

    // --- §3.7 block probability (defender-driven contest) ---

    @Test
    void blockProbabilityEqualSkillsReturnsBase() {
        // Average defender vs. average finisher lands exactly at base.
        double result = config.blockProbability(SimConfig.BASE_BLOCK_DRIVE, 10.0, 10.0);
        assertEquals(SimConfig.BASE_BLOCK_DRIVE, result, 0.001);
    }

    @Test
    void blockProbabilityStrongDefenderIncreasesProb() {
        // The DEFENDER drives the contest: a strong rim protector blocks more.
        double result = config.blockProbability(SimConfig.BASE_BLOCK_DRIVE, 20.0, 10.0);
        assertTrue(result > SimConfig.BASE_BLOCK_DRIVE);
    }

    @Test
    void blockProbabilityStrongFinisherDecreasesProb() {
        // A great finisher (the counter-factor) gets blocked less.
        double result = config.blockProbability(SimConfig.BASE_BLOCK_DRIVE, 10.0, 20.0);
        assertTrue(result < SimConfig.BASE_BLOCK_DRIVE);
    }

    @Test
    void blockProbabilityClampedToValidRange() {
        double high = config.blockProbability(0.90, 20.0, 1.0);
        assertTrue(high <= SimConfig.PROB_CEILING);
        double low = config.blockProbability(0.02, 1.0, 20.0);
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

    // --- §3.5 fatigue / rotation helpers (decisions.md #023) ---

    @Test
    void fatigueFactorFullEnergyIsNoEffect() {
        assertEquals(1.0, config.fatigueFactor(SimConfig.MAX_ENERGY), 0.0001);
    }

    @Test
    void fatigueFactorEmptyEnergyIsMaxPenalty() {
        assertEquals(1.0 - SimConfig.FATIGUE_MAX_PENALTY, config.fatigueFactor(0.0), 0.0001);
    }

    @Test
    void fatigueFactorDecreasesMonotonicallyWithEnergy() {
        assertTrue(config.fatigueFactor(SimConfig.MAX_ENERGY) > config.fatigueFactor(50.0));
        assertTrue(config.fatigueFactor(50.0) > config.fatigueFactor(10.0));
    }

    @Test
    void fatigueFactorClampsOutOfRangeEnergy() {
        assertEquals(1.0, config.fatigueFactor(SimConfig.MAX_ENERGY + 50), 0.0001);
        assertEquals(1.0 - SimConfig.FATIGUE_MAX_PENALTY, config.fatigueFactor(-10.0), 0.0001);
    }

    @Test
    void energyDrainAverageEnduranceIsBaseDrain() {
        assertEquals(SimConfig.ENERGY_DRAIN_PER_POSSESSION, config.energyDrain(10.0), 0.0001);
    }

    @Test
    void energyDrainHigherEnduranceDrainsLess() {
        assertTrue(config.energyDrain(20.0) < config.energyDrain(10.0));
        assertTrue(config.energyDrain(10.0) < config.energyDrain(1.0));
    }

    @Test
    void energyDrainScaleIsFloored() {
        // Even an off-the-charts endurance drains at least MIN_DRAIN_SCALE × base.
        double minDrain = SimConfig.ENERGY_DRAIN_PER_POSSESSION * SimConfig.MIN_DRAIN_SCALE;
        assertEquals(minDrain, config.energyDrain(1000.0), 0.0001);
    }

    @Test
    void rotationDepthScalesWithFactorAndFloorsAtOne() {
        assertEquals(SimConfig.BASE_ROTATION_DEPTH, config.rotationDepth(1.0));
        assertTrue(config.rotationDepth(1.5) > SimConfig.BASE_ROTATION_DEPTH);
        assertTrue(config.rotationDepth(0.5) < SimConfig.BASE_ROTATION_DEPTH);
        assertEquals(1, config.rotationDepth(0.0), "depth never drops below 1");
    }

    @Test
    void subEnergyThresholdStartersToleratedLonger() {
        double bench = config.subEnergyThreshold(1.0, false);
        double starter = config.subEnergyThreshold(1.0, true);
        assertEquals(SimConfig.BASE_SUB_ENERGY_THRESHOLD, bench, 0.0001);
        assertEquals(SimConfig.BASE_SUB_ENERGY_THRESHOLD - SimConfig.STARTER_SUB_THRESHOLD_BONUS,
                starter, 0.0001);
        assertTrue(starter < bench, "starters have a lower sub threshold (pulled later)");
    }

    @Test
    void subEnergyThresholdAggressiveCoachPullsEarlier() {
        double passive = config.subEnergyThreshold(0.8, false);
        double aggressive = config.subEnergyThreshold(1.2, false);
        assertTrue(aggressive > passive,
                "a more aggressive coach has a higher threshold (pulls earlier)");
    }
}
