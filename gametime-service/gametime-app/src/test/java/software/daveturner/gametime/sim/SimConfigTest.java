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

    // --- §3.13 foul trouble (decisions.md #031 B) ---

    @Test
    void foulTroubleCurveIsZeroBelowThreeFoulsAndAtTheFoulOutLimit() {
        for (int f = 0; f <= 2; f++) {
            assertEquals(0.0, SimConfig.FOUL_TROUBLE_SIT_PROBABILITY[f], 0.0,
                    "no coach benches a player for " + f + " fouls");
            assertEquals(0.0, config.foulTroubleSitProbability(f, 1.2, 16.0, true, null), 0.0);
        }
        assertEquals(0.0, SimConfig.FOUL_TROUBLE_SIT_PROBABILITY[SimConfig.FOUL_OUT_LIMIT], 0.0,
                "6 fouls is the HARD foul-out rule's business, not the soft rule's");
        assertEquals(0.0, config.foulTroubleSitProbability(
                SimConfig.FOUL_OUT_LIMIT, 1.2, 16.0, true, null), 0.0);
    }

    @Test
    void foulTroubleCurveRisesWithFoulCount() {
        double three = config.foulTroubleSitProbability(3, 1.0, SimConfig.SCALE_AVG, false, 5);
        double four = config.foulTroubleSitProbability(4, 1.0, SimConfig.SCALE_AVG, false, 5);
        double five = config.foulTroubleSitProbability(5, 1.0, SimConfig.SCALE_AVG, false, 5);
        assertTrue(three > 0.0 && three < four && four < five,
                "one rising curve, not three thresholds: " + three + " " + four + " " + five);
    }

    @Test
    void foulTroubleProbabilityIsOutOfRangeSafe() {
        assertEquals(0.0, config.foulTroubleSitProbability(-1, 1.0, 10.0, true, null), 0.0);
        assertEquals(0.0, config.foulTroubleSitProbability(99, 1.0, 10.0, true, null), 0.0);
    }

    @Test
    void foulTroubleProbabilityScalesWithTheCoachFactor() {
        double passive = config.foulTroubleSitProbability(4, 0.8, SimConfig.SCALE_AVG, true, null);
        double aggressive = config.foulTroubleSitProbability(4, 1.2, SimConfig.SCALE_AVG, true, null);
        assertTrue(aggressive > passive,
                "a more aggressive coach sits a foul-troubled player more readily");
    }

    @Test
    void foulTroubleProbabilityProtectsHighValuePlayersMore() {
        // The deliberate INVERSE of the fatigue rule's starter tolerance (#031 B):
        // a better player is benched MORE readily at the same foul count.
        double star = config.foulTroubleSitProbability(4, 1.0, 16.0, true, null);
        double average = config.foulTroubleSitProbability(4, 1.0, 10.0, true, null);
        double weak = config.foulTroubleSitProbability(4, 1.0, 6.0, true, null);
        assertTrue(star > average && average > weak,
                "value scales the sit probability upward: " + weak + " " + average + " " + star);
    }

    @Test
    void foulTroubleProbabilityNeverGoesNegativeForAnExtremeLowValuePlayer() {
        // A value far below average must floor at 0, never invert the sign.
        double p = config.foulTroubleSitProbability(5, 1.0, -50.0, false, 8);
        assertTrue(p >= 0.0, "probability is never negative: " + p);
    }

    @Test
    void foulTroubleProbabilityIsNotSubjectToTheProbabilityFloor() {
        // clampProbability's PROB_FLOOR would give a clean player a 2% chance of
        // being benched on EVERY check (~100 per game). The rare-event sites must
        // dodge it; this is the third such site in the package (#030 follow-up).
        // A clean player must be EXACTLY zero, not floored up to PROB_FLOOR (2%),
        // which over ~100 checks a game would bench him roughly 87% of games.
        for (int f = 0; f <= 2; f++) {
            assertEquals(0.0, config.foulTroubleSitProbability(f, 1.2, 20.0, true, null), 0.0,
                    "a clean player is never a foul-trouble candidate, at any coach"
                            + " or value — the global probability floor must not apply");
        }
        // Contrast with the clamped helper: clampProbability would lift any of those
        // zeros to PROB_FLOOR. The foul-trouble helper must not.
        assertEquals(SimConfig.PROB_FLOOR, config.clampProbability(0.0), 0.0,
                "the clamped helper floors at PROB_FLOOR — which is exactly why the"
                        + " foul-trouble probability does not use it");
    }

    @Test
    void rosterProtectionFactorCombinesWithTheValueComposite() {
        double starter = config.rosterProtectionFactor(true, null);
        double firstOffBench = config.rosterProtectionFactor(false, 1);
        double deepBench = config.rosterProtectionFactor(false, 8);
        assertEquals(1.0 + SimConfig.FOUL_TROUBLE_STARTER_BONUS, starter, 1e-9);
        assertTrue(starter > firstOffBench, "a starter is managed more tightly");
        assertTrue(firstOffBench > deepBench, "protection falls down the rotation queue");
        assertEquals(SimConfig.FOUL_TROUBLE_MIN_ROSTER_FACTOR, deepBench, 1e-9,
                "a deep reserve is protected less, never exempt");
    }

    @Test
    void rosterProtectionFactorTreatsANullRotationOrderBenchPlayerAsFirstOffTheBench() {
        assertEquals(config.rosterProtectionFactor(false, 1),
                config.rosterProtectionFactor(false, null), 1e-9);
    }

    // ---------- §3.14a: the technical rate + the shared clamp (#032 B2/H) ----------

    /**
     * #032 B2: the constant is the per-team-per-GAME rate; the engine rolls a
     * per-CHECK probability derived from it. The two must not be confused — the
     * derived value is ~200× smaller.
     */
    @Test
    void technicalFoulProbabilityDividesTheGameRateByTheNominalCheckCount() {
        int nominalChecks = SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD * SimConfig.PERIODS * 2;
        assertEquals(SimConfig.TECHNICAL_FOULS_PER_TEAM_GAME / nominalChecks,
                config.technicalFoulProbability(), 1e-12);
        // ~0.00175, NOT #032 B2's stated ~0.0035: that estimate assumed ~100 checks
        // per team per game, but PossessionEngine.simulate advances BOTH rotations on
        // EVERY possession, so a team is checked ~200 times (its defensive
        // possessions included). The harness landing on the configured rate is the
        // empirical confirmation — a divisor of 100 would have doubled technicals.
        assertEquals(0.00175, config.technicalFoulProbability(), 1e-5,
                "The per-check probability lands around 0.00175");
        assertEquals(200, nominalChecks,
                "Both teams advance on every possession — 200 checks, not 100");
    }

    /**
     * #032 H, quantitatively: PROB_FLOOR is ~6× the technical rate, so routing it
     * through the NORMAL clamp would inflate technicals ~6-fold and make the constant
     * tunable only upward. This pins the reason the floor-free helper exists.
     */
    @Test
    void theProbabilityFloorWouldSwampTheTechnicalRate() {
        double perCheck = config.technicalFoulProbability();
        assertTrue(SimConfig.PROB_FLOOR > perCheck * 10,
                "PROB_FLOOR (" + SimConfig.PROB_FLOOR + ") must dwarf the technical rate ("
                        + perCheck + ") — that is why clampRareProbability exists");
        assertEquals(SimConfig.PROB_FLOOR, config.clampProbability(perCheck), 1e-12,
                "The normal clamp WOULD floor it — the failure #032 H avoids");
        assertEquals(perCheck, config.clampRareProbability(perCheck), 1e-12,
                "The rare clamp leaves it alone");
    }

    /** #032 H: the shared floor-free clamp — no floor, normal ceiling, never negative. */
    @Test
    void clampRareProbabilityIsFloorFreeButStillCeilinged() {
        assertEquals(0.0, config.clampRareProbability(0.0), 1e-12);
        assertEquals(0.0, config.clampRareProbability(-0.5), 1e-12,
                "Never negative");
        assertEquals(0.0001, config.clampRareProbability(0.0001), 1e-12,
                "A rate far below PROB_FLOOR survives untouched");
        assertEquals(SimConfig.PROB_CEILING, config.clampRareProbability(1.5), 1e-12);
    }

    /**
     * #032 H: the consolidation is BEHAVIOR-NEUTRAL. The three pre-existing
     * floor-free sites hand-rolled max(0.0, min(PROB_CEILING, p)); routing them
     * through the helper must reproduce that expression exactly.
     */
    @Test
    void theSharedClampReproducesTheHandRolledExpressionItReplaced() {
        for (double p : new double[]{-1.0, 0.0, 0.001, 0.02, 0.5, 0.97, 1.0, 2.0}) {
            assertEquals(Math.max(0.0, Math.min(SimConfig.PROB_CEILING, p)),
                    config.clampRareProbability(p), 1e-12,
                    "clampRareProbability must match the expression it consolidated, p=" + p);
        }
    }

    /** #032 F: two technicals is the ejection limit, and it is not the foul-out limit. */
    @Test
    void technicalEjectionLimitIsTwoAndSeparateFromTheFoulOutLimit() {
        assertEquals(2, SimConfig.TECHNICAL_EJECTION_LIMIT);
        assertNotEquals(SimConfig.FOUL_OUT_LIMIT, SimConfig.TECHNICAL_EJECTION_LIMIT);
    }

    /** #030 C's lesson applied: a technical is ONE free throw, not two. */
    @Test
    void aTechnicalIsExactlyOneFreeThrow() {
        assertEquals(1, SimConfig.TECHNICAL_FREE_THROWS);
        assertNotEquals(SimConfig.FREE_THROWS_PER_FOUL, SimConfig.TECHNICAL_FREE_THROWS);
    }
}
