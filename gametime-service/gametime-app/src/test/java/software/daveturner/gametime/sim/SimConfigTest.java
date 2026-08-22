package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimConfigTest {

    private final SimConfig config = SimConfig.baseline();

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
        double result = config.blockProbability(config.baseBlockDrive(), 10.0, 10.0);
        assertEquals(config.baseBlockDrive(), result, 0.001);
    }

    @Test
    void blockProbabilityStrongDefenderIncreasesProb() {
        // The DEFENDER drives the contest: a strong rim protector blocks more.
        double result = config.blockProbability(config.baseBlockDrive(), 20.0, 10.0);
        assertTrue(result > config.baseBlockDrive());
    }

    @Test
    void blockProbabilityStrongFinisherDecreasesProb() {
        // A great finisher (the counter-factor) gets blocked less.
        double result = config.blockProbability(config.baseBlockDrive(), 10.0, 20.0);
        assertTrue(result < config.baseBlockDrive());
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
        assertEquals(config.baseAssist(), config.assistProbability(10.0), 0.0001);
    }

    @Test
    void assistProbabilityHigherPassingAssistsMore() {
        assertTrue(config.assistProbability(20.0) > config.baseAssist());
    }

    @Test
    void assistProbabilityLowerPassingAssistsLess() {
        assertTrue(config.assistProbability(1.0) < config.baseAssist());
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
        assertEquals(1.0 - config.fatigueMaxPenalty(), config.fatigueFactor(0.0), 0.0001);
    }

    @Test
    void fatigueFactorDecreasesMonotonicallyWithEnergy() {
        assertTrue(config.fatigueFactor(SimConfig.MAX_ENERGY) > config.fatigueFactor(50.0));
        assertTrue(config.fatigueFactor(50.0) > config.fatigueFactor(10.0));
    }

    @Test
    void fatigueFactorClampsOutOfRangeEnergy() {
        assertEquals(1.0, config.fatigueFactor(SimConfig.MAX_ENERGY + 50), 0.0001);
        assertEquals(1.0 - config.fatigueMaxPenalty(), config.fatigueFactor(-10.0), 0.0001);
    }

    @Test
    void energyDrainAverageEnduranceIsBaseDrain() {
        assertEquals(config.energyDrainPerPossession(), config.energyDrain(10.0), 0.0001);
    }

    @Test
    void energyDrainHigherEnduranceDrainsLess() {
        assertTrue(config.energyDrain(20.0) < config.energyDrain(10.0));
        assertTrue(config.energyDrain(10.0) < config.energyDrain(1.0));
    }

    @Test
    void energyDrainScaleIsFloored() {
        // Even an off-the-charts endurance drains at least MIN_DRAIN_SCALE × base.
        double minDrain = config.energyDrainPerPossession() * config.minDrainScale();
        assertEquals(minDrain, config.energyDrain(1000.0), 0.0001);
    }

    @Test
    void rotationDepthScalesWithFactorAndFloorsAtOne() {
        assertEquals(config.baseRotationDepth(), config.rotationDepth(1.0));
        assertTrue(config.rotationDepth(1.5) > config.baseRotationDepth());
        assertTrue(config.rotationDepth(0.5) < config.baseRotationDepth());
        assertEquals(1, config.rotationDepth(0.0), "depth never drops below 1");
    }

    @Test
    void subEnergyThresholdStartersToleratedLonger() {
        double bench = config.subEnergyThreshold(1.0, false);
        double starter = config.subEnergyThreshold(1.0, true);
        assertEquals(config.baseSubEnergyThreshold(), bench, 0.0001);
        assertEquals(config.baseSubEnergyThreshold() - config.starterSubThresholdBonus(),
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
            assertEquals(0.0, config.foulTroubleSitProbabilities()[f], 0.0,
                    "no coach benches a player for " + f + " fouls");
            assertEquals(0.0, config.foulTroubleSitProbability(f, 1.2, 16.0, true, null), 0.0);
        }
        assertEquals(0.0, config.foulTroubleSitProbabilities()[config.foulOutLimit()], 0.0,
                "6 fouls is the HARD foul-out rule's business, not the soft rule's");
        assertEquals(0.0, config.foulTroubleSitProbability(
                config.foulOutLimit(), 1.2, 16.0, true, null), 0.0);
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
        assertEquals(1.0 + config.foulTroubleStarterBonus(), starter, 1e-9);
        assertTrue(starter > firstOffBench, "a starter is managed more tightly");
        assertTrue(firstOffBench > deepBench, "protection falls down the rotation queue");
        assertEquals(config.foulTroubleMinRosterFactor(), deepBench, 1e-9,
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
        int nominalChecks = config.defaultPossessionsPerPeriod() * SimConfig.PERIODS * 2;
        assertEquals(config.technicalFoulsPerTeamGame() / nominalChecks,
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
        assertNotEquals(config.foulOutLimit(), SimConfig.TECHNICAL_EJECTION_LIMIT);
    }

    /** #030 C's lesson applied: a technical is ONE free throw, not two. */
    @Test
    void aTechnicalIsExactlyOneFreeThrow() {
        assertEquals(1, SimConfig.TECHNICAL_FREE_THROWS);
        assertNotEquals(SimConfig.FREE_THROWS_PER_FOUL, SimConfig.TECHNICAL_FREE_THROWS);
    }

    // ---------- §3.14b: the flagrant rate + its emergent divisor (#034 E/G) ----------

    /**
     * #034 G: the constant is the per-team-per-GAME rate, divided down by the
     * PERSONAL-FOUL rate because the roll fires per FOUL, not per possession or per
     * check. ~0.16 / 17.82 ≈ 0.0090 (§3.17 re-measured the divisor — see below).
     */
    @Test
    void flagrantFoulProbabilityDividesTheGameRateByThePersonalFoulRate() {
        assertEquals(config.flagrantFoulsPerTeamGame()
                        / SimConfig.PERSONAL_FOULS_PER_TEAM_GAME,
                config.flagrantFoulProbability(), 1e-12);
        assertEquals(0.0090, config.flagrantFoulProbability(), 1e-4,
                "The per-foul probability lands around 0.0090");
    }

    /**
     * #034 G, the honest cost: unlike the technical rate's NOMINAL, config-derived
     * divisor, this one is a MEASURED quantity — an assumption about the engine's
     * current behavior. It is a named constant precisely so a pass that moves the foul
     * rate can grep for it, and §3.16 is the first pass that did.
     *
     * <p>§3.13/§3.14a measured 19.0. §3.16's charge fix (#039 G) made ~1.2 charges per
     * team-game into personal fouls that had counted toward nothing, taking the
     * measured rate to <b>20.15</b> (20.50 all-events minus ~0.35 technicals, 5 seeds).
     * §3.16's NON_SHOOTING_FOUL re-partition does NOT enter it — that only re-labels a foul
     * already rolled and charged (#039 A).
     *
     * <p>⚠ <b>§3.17 moved it again, to 17.82</b> — and it is the clearest demonstration
     * yet of why #034 G forbids drift. §3.17 touches <b>no foul constant whatsoever</b>;
     * it moves the SHOT MIX. But shifting draws from DRIVE/POST ({@code foul-mult} 1.0)
     * to THREE (0.133) means far fewer of them draw contact, so personal fouls fell
     * 20.18 → 17.82 (−11.5%). <b>The drift was already visible in the output</b>: the
     * measured flagrant rate ran 0.119 against its ~0.16 ballpark, which is exactly the
     * 17.82/20.15 ratio. Nothing failed — which is #032 B2's whole point.
     */
    @Test
    void theFlagrantDivisorIsTheMeasuredPersonalFoulRateNotAConfiguredCount() {
        assertEquals(17.82, SimConfig.PERSONAL_FOULS_PER_TEAM_GAME, 1e-12,
                "§3.17 re-measured this deliberately (#034 G forbids letting it drift): "
                        + "18.18 fouls/team/game over ALL foul events, minus ~0.36 "
                        + "technicals, i.e. 17.82 personal fouls");
        // The contrast that makes the coupling worth stating: the technical divisor is
        // derived from constants, so it moves only when a constant moves. This one does
        // not appear in any other formula — moving the foul rate moves flagrants
        // silently, which is exactly what #034 G records.
        int technicalDivisor =
                config.defaultPossessionsPerPeriod() * SimConfig.PERIODS * 2;
        assertNotEquals((double) technicalDivisor, SimConfig.PERSONAL_FOULS_PER_TEAM_GAME,
                "The two rates divide by different KINDS of quantity (#034 G)");
    }

    /**
     * #034 G, quantitatively: the floor argument still holds but is THINNER than
     * §3.14a's — PROB_FLOOR is ~2.4× the flagrant rate, against >10× for technicals.
     * Decisive, but argued rather than assumed, and worth pinning because a future rate
     * increase could erode it.
     */
    @Test
    void theProbabilityFloorWouldStillSwampTheFlagrantRateButByALesserMargin() {
        double perFoul = config.flagrantFoulProbability();
        assertTrue(SimConfig.PROB_FLOOR > perFoul * 2,
                "PROB_FLOOR must still dwarf the flagrant rate");
        assertTrue(SimConfig.PROB_FLOOR < perFoul * 10,
                "…but by a THINNER margin than the technical rate's >10× (#034 G) — if "
                        + "this fails the floor-free choice has become obvious again, and "
                        + "the javadoc's caveat can be relaxed");
        assertEquals(SimConfig.PROB_FLOOR, config.clampProbability(perFoul), 1e-12,
                "The normal clamp WOULD floor it — a 2.4× inflation, the #028 trap");
        assertEquals(perFoul, config.clampRareProbability(perFoul), 1e-12,
                "The rare clamp (#032 H's FIFTH site) leaves it alone");
    }

    /**
     * #034 E: the severity share is a flat CONDITIONAL share, not a clamped probability
     * — it needs no clamp, and it is not a second independently-tunable rate.
     */
    @Test
    void theFlagrantTwoShareIsAFlatConditionalShare() {
        assertEquals(0.15, config.flagrantTwoShare(), 1e-12);
        assertTrue(config.flagrantTwoShare() > SimConfig.PROB_FLOOR,
                "It is a share of an already-rare parent event, far above the floor — "
                        + "no clamp is involved at all (#034 E)");
    }

    /**
     * #034 C/E/F: the flagrant counts. Two free throws flat (REPLACING the underlying
     * award, never adding), and ONE flagrant-2 ejects.
     */
    @Test
    void aFlagrantIsTwoFreeThrowsAndOneFlagrantTwoEjects() {
        assertEquals(2, SimConfig.FLAGRANT_FREE_THROWS,
                "Flat 2 at every site and for both grades (#034 C)");
        assertNotEquals(SimConfig.AND_ONE_FREE_THROWS, SimConfig.FLAGRANT_FREE_THROWS,
                "A flagrant and-1 is 2, not the and-1's 1 — replaces, doesn't add");
        assertEquals(1, SimConfig.FLAGRANT_EJECTION_LIMIT,
                "One flagrant-2 is enough — which is WHY the predicate stays derived");
        assertTrue(SimConfig.FLAGRANT_EJECTION_LIMIT < SimConfig.TECHNICAL_EJECTION_LIMIT,
                "…a lower threshold than the technical ejection, same monotonic shape");
    }
}
