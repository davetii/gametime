package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.model.Coach;

import static org.junit.jupiter.api.Assertions.*;

class CoachModifiersTest {

    private final SimConfig config = new SimConfig();

    private Coach coach(Integer pace, Integer offScheme, Integer defScheme) {
        Coach c = new Coach();
        c.setPace(pace);
        c.setOffensiveScheme(offScheme);
        c.setDefensiveScheme(defScheme);
        return c;
    }

    @Test
    void nullCoachIsNeutral() {
        CoachModifiers m = CoachModifiers.from(null, config);
        assertEquals(1.0, m.paceMultiplier(), 0.0001);
        assertEquals(1.0, m.shotMixLean(), 0.0001);
        assertEquals(1.0, m.defensivePressure(), 0.0001);
    }

    @Test
    void neutralFactoryIsAllOnes() {
        CoachModifiers m = CoachModifiers.neutral();
        assertEquals(1.0, m.paceMultiplier(), 0.0001);
        assertEquals(1.0, m.shotMixLean(), 0.0001);
        assertEquals(1.0, m.defensivePressure(), 0.0001);
    }

    @Test
    void averageAttributesYieldNoEffect() {
        CoachModifiers m = CoachModifiers.from(coach(10, 10, 10), config);
        assertEquals(1.0, m.paceMultiplier(), 0.0001);
        assertEquals(1.0, m.shotMixLean(), 0.0001);
        assertEquals(1.0, m.defensivePressure(), 0.0001);
    }

    @Test
    void nullAttributesFallBackToAverage() {
        CoachModifiers m = CoachModifiers.from(coach(null, null, null), config);
        assertEquals(1.0, m.paceMultiplier(), 0.0001);
        assertEquals(1.0, m.shotMixLean(), 0.0001);
        assertEquals(1.0, m.defensivePressure(), 0.0001);
    }

    @Test
    void aboveAverageAttributesLiftMultipliers() {
        CoachModifiers m = CoachModifiers.from(coach(20, 20, 20), config);
        assertTrue(m.paceMultiplier() > 1.0);
        assertTrue(m.shotMixLean() > 1.0);
        assertTrue(m.defensivePressure() > 1.0);
        // attr 20 ⇒ 1 + 0.20 * (20-10)/10 = 1.20
        assertEquals(1.0 + SimConfig.COACH_SENSITIVITY, m.paceMultiplier(), 0.0001);
    }

    @Test
    void belowAverageAttributesLowerMultipliers() {
        CoachModifiers m = CoachModifiers.from(coach(1, 1, 1), config);
        assertTrue(m.paceMultiplier() < 1.0);
        assertTrue(m.shotMixLean() < 1.0);
        assertTrue(m.defensivePressure() < 1.0);
    }

    @Test
    void eachAttributeDrivesItsOwnMultiplier() {
        CoachModifiers m = CoachModifiers.from(coach(20, 10, 1), config);
        assertTrue(m.paceMultiplier() > 1.0);
        assertEquals(1.0, m.shotMixLean(), 0.0001);
        assertTrue(m.defensivePressure() < 1.0);
    }

    // --- §3.5 rotation readers (decisions.md #023, Decision D) ---

    private Coach rotationCoach(Integer rotationDepth, Integer subAggressiveness) {
        Coach c = coach(10, 10, 10);
        c.setRotationDepth(rotationDepth);
        c.setSubstitutionAggressiveness(subAggressiveness);
        return c;
    }

    @Test
    void rotationFactorsAreNeutralForNullOrAverage() {
        assertEquals(1.0, CoachModifiers.neutral().rotationDepthFactor(), 0.0001);
        assertEquals(1.0, CoachModifiers.neutral().subAggressivenessFactor(), 0.0001);
        assertEquals(1.0, CoachModifiers.from(null, config).rotationDepthFactor(), 0.0001);
        assertEquals(1.0, CoachModifiers.from(null, config).subAggressivenessFactor(), 0.0001);
        CoachModifiers avg = CoachModifiers.from(rotationCoach(10, 10), config);
        assertEquals(1.0, avg.rotationDepthFactor(), 0.0001);
        assertEquals(1.0, avg.subAggressivenessFactor(), 0.0001);
    }

    @Test
    void nullRotationAttributesFallBackToAverage() {
        // The pre-§3.5 coach(...) leaves rotation attrs null → neutral factors.
        CoachModifiers m = CoachModifiers.from(coach(10, 10, 10), config);
        assertEquals(1.0, m.rotationDepthFactor(), 0.0001);
        assertEquals(1.0, m.subAggressivenessFactor(), 0.0001);
    }

    @Test
    void aboveAverageRotationAttributesLiftFactors() {
        CoachModifiers m = CoachModifiers.from(rotationCoach(20, 20), config);
        assertEquals(1.0 + SimConfig.COACH_SENSITIVITY, m.rotationDepthFactor(), 0.0001);
        assertEquals(1.0 + SimConfig.COACH_SENSITIVITY, m.subAggressivenessFactor(), 0.0001);
    }

    @Test
    void belowAverageRotationAttributesLowerFactors() {
        CoachModifiers m = CoachModifiers.from(rotationCoach(1, 1), config);
        assertTrue(m.rotationDepthFactor() < 1.0);
        assertTrue(m.subAggressivenessFactor() < 1.0);
    }

    @Test
    void rotationAttributesAreIndependent() {
        CoachModifiers m = CoachModifiers.from(rotationCoach(20, 1), config);
        assertTrue(m.rotationDepthFactor() > 1.0);
        assertTrue(m.subAggressivenessFactor() < 1.0);
    }
}
