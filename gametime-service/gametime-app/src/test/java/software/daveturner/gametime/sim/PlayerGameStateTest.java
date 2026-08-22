package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerGameStateTest {

    private final SimConfig config = SimConfig.baseline();

    @Test
    void constructorExtractsSkillsAsDoubles() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 15.0);
        assertEquals(15.0, p.getDrive());
        assertEquals(15.0, p.getFinishing());
        assertEquals(15.0, p.getPerimeter());
        assertEquals(15.0, p.getPost());
        assertEquals(15.0, p.getLongRange());
        assertEquals(15.0, p.getBallSecurity());
        assertEquals(15.0, p.getFreeThrows());
        assertEquals(15.0, p.getFoulDrawing());
        assertEquals(15.0, p.getIndividualDefense());
        assertEquals(15.0, p.getRimProtection());
        assertEquals(15.0, p.getShotContest());
        assertEquals(15.0, p.getStealing());
        assertEquals(15.0, p.getFoulProne());
        assertEquals(15.0, p.getOffenseRebound());
        assertEquals(15.0, p.getDefenseRebound());
    }

    @Test
    void reboundSkillsExtractedFrom15ParamFactory() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 18.0, 7.0);
        assertEquals(18.0, p.getOffenseRebound());
        assertEquals(7.0, p.getDefenseRebound());
    }

    @Test
    void reboundSkillsDefaultToAverageFrom13ParamFactory() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
        assertEquals(SimConfig.SCALE_AVG, p.getOffenseRebound());
        assertEquals(SimConfig.SCALE_AVG, p.getDefenseRebound());
    }

    @Test
    void reboundAccumulators() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        assertEquals(0, p.getOffensiveRebounds());
        assertEquals(0, p.getDefensiveRebounds());

        p.recordOffensiveRebound();
        p.recordOffensiveRebound();
        p.recordDefensiveRebound();

        assertEquals(2, p.getOffensiveRebounds());
        assertEquals(1, p.getDefensiveRebounds());
    }

    @Test
    void nullReboundSkillsDefaultToAverage() {
        var player = new software.daveturner.gametime.model.Player();
        player.setId("p1");
        var skills = new software.daveturner.gametime.model.PlayerSkills();
        // leave rebound skills null
        player.setSkills(skills);
        var entry = new software.daveturner.gametime.model.RosterEntry();
        entry.setPlayer(player);

        PlayerGameState p = new PlayerGameState("p1", "T1", entry, config);
        assertEquals(SimConfig.SCALE_AVG, p.getOffenseRebound());
        assertEquals(SimConfig.SCALE_AVG, p.getDefenseRebound());
    }

    @Test
    void offensiveWeightSumsOffensiveSkills() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                10, 10, 10, 10, 10, 5, 5, 5, 5, 5, 5, 5, 5);
        // drive + finishing + perimeter + post + longRange = 10+10+10+10+10 = 50
        assertEquals(50.0, p.offensiveWeight());
    }

    /**
     * §3.17 (decisions.md #040 B/C). ⚠ <b>THESE TWO TESTS PINNED THE BUG.</b> They
     * asserted the weight WAS the raw skill — {@code drive + finishing = 27} for DRIVE
     * and {@code longRange = 18} for THREE. That SUM on DRIVE against one skill each
     * elsewhere is #040 B's 5-into-4 collapse, the structural cause of the 20%-vs-41.5%
     * three-share gap. The weight is now the league share table bent by skill:
     * {@code share(type) * (1 + SHOT_MIX_SENSITIVITY * (skill - 10) / 10)}, over
     * {@code (drive + finishing) / 2.0} for DRIVE — the same expression
     * {@code offenseSkillForShot} uses, which is the whole point of the fix.
     */
    @Test
    void shotTypeWeightForDriveIsTheShareBentByTheDriveFinishingAVERAGE() {
        SimConfig config = SimConfig.baseline();
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                15, 12, 8, 5, 3, 10, 10, 10, 10, 10, 10, 10, 10);
        // (15 + 12) / 2 = 13.5 -> modifier 1 + 0.5 * (13.5 - 10) / 10 = 1.175
        double expected = config.shotShareDrive() * 1.175;
        assertEquals(expected, p.shotTypeWeight(ShotType.DRIVE), 1e-9);
    }

    @Test
    void shotTypeWeightForThreeIsTheShareBentByLongRange() {
        SimConfig config = SimConfig.baseline();
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                5, 5, 5, 5, 18, 10, 10, 10, 10, 10, 10, 10, 10);
        // longRange 18 -> modifier 1 + 0.5 * (18 - 10) / 10 = 1.4
        double expected = config.shotShareThree() * 1.4;
        assertEquals(expected, p.shotTypeWeight(ShotType.THREE), 1e-9);
    }

    /**
     * §3.17 (#040 C): at an average player every modifier is exactly 1.0, so the
     * weights ARE the league share table. This is what makes {@code sim.shot-share-*}
     * a readable calibration surface — its meaning does not depend on the population.
     */
    @Test
    void anAveragePlayerWeightsAreExactlyTheLeagueShares() {
        SimConfig config = SimConfig.baseline();
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", SimConfig.SCALE_AVG);
        for (ShotType t : ShotType.values()) {
            assertEquals(config.shotShare(t), p.shotTypeWeight(t), 1e-9,
                    t + " at an average player must be exactly its raw share");
        }
    }

    /**
     * §3.17 (#040 C): the modifier is floored at zero, so no skill value can produce a
     * negative weight and corrupt the weighted draw. At SHOT_MIX_SENSITIVITY 0.5 the
     * modifier stays positive across the whole 1-20 skill range, so this pins the guard
     * rather than a reachable case — which is exactly why it is worth pinning.
     */
    @Test
    void anExtremelyLowSkillNeverProducesANegativeWeight() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                1, 1, 1, 1, 1, 10, 10, 10, 10, 10, 10, 10, 10);
        for (ShotType t : ShotType.values()) {
            assertTrue(p.shotTypeWeight(t) >= 0.0,
                    t + " weight must never go negative");
        }
    }

    /**
     * §3.17 (#040 B): selection and accuracy finally AGREE on how drive and finishing
     * combine. Before this pass {@code shotTypeWeight} summed them and
     * {@code offenseSkillForShot} averaged them — the asymmetry that was the bug. The
     * accuracy path is UNTOUCHED; it is the selection path that moved to meet it.
     */
    @Test
    void selectionAndAccuracyNowAgreeOnDrivePlusFinishing() {
        SimConfig config = SimConfig.baseline();
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                16, 12, 8, 5, 3, 10, 10, 10, 10, 10, 10, 10, 10);
        double accuracySkill = p.offenseSkillForShot(ShotType.DRIVE);   // (16+12)/2 = 14
        double expectedWeight = config.shotShareDrive()
                * (1.0 + SimConfig.SHOT_MIX_SENSITIVITY
                        * (accuracySkill - SimConfig.SCALE_AVG) / SimConfig.SCALE_AVG);
        assertEquals(expectedWeight, p.shotTypeWeight(ShotType.DRIVE), 1e-9,
                "the selection weight must be built from the SAME drive/finishing "
                        + "combination the accuracy path uses (#040 B)");
    }

    @Test
    void offenseSkillForShotDriveAveragesDriveAndFinishing() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                16, 12, 8, 5, 3, 10, 10, 10, 10, 10, 10, 10, 10);
        assertEquals(14.0, p.offenseSkillForShot(ShotType.DRIVE));
    }

    @Test
    void boxScoreAccumulators() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);

        p.recordFieldGoalAttempt();
        p.recordFieldGoalMade(2);
        assertEquals(2, p.getPoints());

        p.recordThreePointAttempt();
        p.recordThreePointMade();
        p.recordFreeThrowAttempt();
        p.recordFreeThrowMade();
        p.recordTurnover();
        p.recordSteal();
        p.recordBlock();
        p.recordFoul();

        assertEquals(1, p.getFieldGoalsAttempted());
        assertEquals(1, p.getFieldGoalsMade());
        assertEquals(1, p.getThreePointersAttempted());
        assertEquals(1, p.getThreePointersMade());
        assertEquals(1, p.getFreeThrowsAttempted());
        assertEquals(1, p.getFreeThrowsMade());
        assertEquals(3, p.getPoints()); // 2 from FG + 1 from FT
        assertEquals(1, p.getTurnovers());
        assertEquals(1, p.getSteals());
        assertEquals(1, p.getBlocks());
        assertEquals(1, p.getFouls());
    }

    // --- §3.4 chemistry skills + assist accumulator ---

    @Test
    void chemistrySkillsExtractedFrom19ParamFactory() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
                17.0, 14.0, 19.0, 6.0);
        assertEquals(17.0, p.getTeamOffense());
        assertEquals(14.0, p.getTeamDefense());
        assertEquals(19.0, p.getPassing());
        assertEquals(6.0, p.getAcumen());
    }

    @Test
    void chemistrySkillsDefaultToAverageFromShorterFactories() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 18.0, 7.0);
        assertEquals(SimConfig.SCALE_AVG, p.getTeamOffense());
        assertEquals(SimConfig.SCALE_AVG, p.getTeamDefense());
        assertEquals(SimConfig.SCALE_AVG, p.getPassing());
        assertEquals(SimConfig.SCALE_AVG, p.getAcumen());
    }

    @Test
    void nullChemistrySkillsDefaultToAverage() {
        var player = new software.daveturner.gametime.model.Player();
        player.setId("p1");
        var skills = new software.daveturner.gametime.model.PlayerSkills();
        // leave chemistry skills null
        player.setSkills(skills);
        var entry = new software.daveturner.gametime.model.RosterEntry();
        entry.setPlayer(player);

        PlayerGameState p = new PlayerGameState("p1", "T1", entry, config);
        assertEquals(SimConfig.SCALE_AVG, p.getTeamOffense());
        assertEquals(SimConfig.SCALE_AVG, p.getTeamDefense());
        assertEquals(SimConfig.SCALE_AVG, p.getPassing());
        assertEquals(SimConfig.SCALE_AVG, p.getAcumen());
    }

    @Test
    void assistAccumulator() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        assertEquals(0, p.getAssists());
        p.recordAssist();
        p.recordAssist();
        assertEquals(2, p.getAssists());
    }

    @Test
    void nullSkillsDefaultToAverage() {
        // Create with null skills to test the null-safe toDouble path
        var player = new software.daveturner.gametime.model.Player();
        player.setId("p1");
        var skills = new software.daveturner.gametime.model.PlayerSkills();
        // leave all skills null
        player.setSkills(skills);
        var entry = new software.daveturner.gametime.model.RosterEntry();
        entry.setPlayer(player);

        PlayerGameState p = new PlayerGameState("p1", "T1", entry, config);
        assertEquals(SimConfig.SCALE_AVG, p.getDrive());
        assertEquals(SimConfig.SCALE_AVG, p.getFinishing());
    }

    // --- §3.5 fatigue / rotation state (decisions.md #023) ---

    @Test
    void extractsEnduranceEnergyAndRotationIdentity() {
        PlayerGameState p = TestPlayerFactory.createRotationPlayer("p1", "T1", 10.0,
                software.daveturner.gametime.model.LineupRole.ROTATION, 3, 14, 16);
        assertEquals(14.0, p.getEndurance());
        assertEquals(16.0, p.getEnergy());
        assertFalse(p.isStarter());
        assertEquals(3, p.getRotationOrder());
    }

    @Test
    void nullEnduranceEnergyDefaultToAverage() {
        var player = new software.daveturner.gametime.model.Player();
        player.setId("p1");
        player.setSkills(new software.daveturner.gametime.model.PlayerSkills());
        var entry = new software.daveturner.gametime.model.RosterEntry();
        entry.setPlayer(player);
        PlayerGameState p = new PlayerGameState("p1", "T1", entry, config);
        assertEquals(SimConfig.SCALE_AVG, p.getEndurance());
        assertEquals(SimConfig.SCALE_AVG, p.getEnergy());
    }

    @Test
    void starterFlagSetFromLineupRole() {
        PlayerGameState starter = TestPlayerFactory.create("s", "T1", 10.0); // factory sets STARTER
        assertTrue(starter.isStarter());
        PlayerGameState bench = TestPlayerFactory.createRotationPlayer("b", "T1", 10.0,
                software.daveturner.gametime.model.LineupRole.BENCH, 5, 10, 10);
        assertFalse(bench.isStarter());
    }

    @Test
    void startsAtFullEnergyWithNeutralFatigue() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        assertEquals(SimConfig.MAX_ENERGY, p.getCurrentEnergy(), 0.0001);
        assertEquals(1.0, p.fatigueFactor(), 0.0001);
        assertEquals(0, p.getOnFloorPossessions());
    }

    @Test
    void drainReducesEnergyAndCountsPossession() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        p.drainForPossession();
        assertTrue(p.getCurrentEnergy() < SimConfig.MAX_ENERGY);
        assertEquals(1, p.getOnFloorPossessions());
        assertTrue(p.fatigueFactor() < 1.0);
    }

    @Test
    void recoverRaisesEnergyButNeverAboveMax() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        // Drain a bunch, then recover.
        for (int i = 0; i < 10; i++) p.drainForPossession();
        double drained = p.getCurrentEnergy();
        p.recoverForPossession();
        assertTrue(p.getCurrentEnergy() > drained);
        // Recovering from full stays capped at full.
        PlayerGameState fresh = TestPlayerFactory.create("p2", "T1", 10.0);
        fresh.recoverForPossession();
        assertEquals(SimConfig.MAX_ENERGY, fresh.getCurrentEnergy(), 0.0001);
    }

    @Test
    void recoverDoesNotCountAsOnFloorPossession() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        p.recoverForPossession();
        assertEquals(0, p.getOnFloorPossessions(), "benched possessions don't count toward minutes");
    }

    @Test
    void fouledOutIsDerivedFromFoulCounter() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        assertFalse(p.isFouledOut());
        for (int i = 0; i < config.foulOutLimit() - 1; i++) {
            p.recordFoul();
            assertFalse(p.isFouledOut(), "not fouled out below the limit");
        }
        p.recordFoul(); // hits the limit
        assertTrue(p.isFouledOut());
        assertEquals(config.foulOutLimit(), p.getFouls());
    }

    // --- §3.13 foul trouble (decisions.md #031 B/E) ---

    @Test
    void foulTroubleLevelIsDerivedFromTheFoulCounter() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        assertEquals(0, p.foulTroubleLevel());
        for (int i = 1; i <= config.foulOutLimit(); i++) {
            p.recordFoul();
            assertEquals(i, p.foulTroubleLevel(),
                    "foul trouble tracks the counter with no stored flag");
        }
    }

    @Test
    void foulTroubleLevelIsCappedAtTheFoulOutLimit() {
        // Defensive: the counter can in principle be incremented past the limit
        // (a foul recorded on the possession the player fouls out), and the level
        // must stay a valid index into the sit-probability curve.
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        for (int i = 0; i < config.foulOutLimit() + 3; i++) {
            p.recordFoul();
        }
        assertEquals(config.foulOutLimit(), p.foulTroubleLevel());
        assertTrue(p.isFouledOut());
    }

    @Test
    void valueCompositeMatchesTheUserSetFormula() {
        // value = (individualDefense + rimProtection + defenseRebound + off + off)/5
        // where off = mean(drive, finishing, perimeter, post, longRange).
        // drive finishing perimeter post longRange ballSec ft foulDraw
        // indDef rimProt shotCont steal foulProne offReb defReb
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                12.0, 14.0, 8.0, 6.0, 10.0,   // offense: mean = 10.0
                10.0, 10.0, 10.0,
                16.0, 12.0, 10.0, 10.0, 10.0, // individualDefense 16, rimProtection 12
                10.0, 8.0);                   // offenseRebound 10, defenseRebound 8
        double expected = (16.0 + 12.0 + 8.0 + 10.0 + 10.0) / 5.0;
        assertEquals(expected, p.valueComposite(), 1e-9);
    }

    @Test
    void valueCompositeIsDefenseLeaningWithOffenseDoubleWeighted() {
        // Offense is double-weighted (~60/40 defense-leaning): raising all five
        // offense skills by 5 moves the composite by 2×5/5 = 2.0, while raising one
        // defensive input by 5 moves it by 5/5 = 1.0.
        PlayerGameState baseline = TestPlayerFactory.create("p", "T", 10.0);
        PlayerGameState betterOffense = TestPlayerFactory.create("p", "T",
                15.0, 15.0, 15.0, 15.0, 15.0,
                10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0);
        PlayerGameState betterDefense = TestPlayerFactory.create("p", "T",
                10.0, 10.0, 10.0, 10.0, 10.0,
                10.0, 10.0, 10.0, 15.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0);
        assertEquals(10.0, baseline.valueComposite(), 1e-9,
                "an all-average player sits at the league average");
        assertEquals(2.0, betterOffense.valueComposite() - baseline.valueComposite(), 1e-9);
        assertEquals(1.0, betterDefense.valueComposite() - baseline.valueComposite(), 1e-9);
    }

    @Test
    void valueCompositeSeparatesAStarFromARolePlayer() {
        PlayerGameState star = TestPlayerFactory.create("star", "T", 16.0);
        PlayerGameState role = TestPlayerFactory.create("role", "T", 7.0);
        assertTrue(star.valueComposite() > role.valueComposite());
        assertEquals(16.0, star.valueComposite(), 1e-9);
        assertEquals(7.0, role.valueComposite(), 1e-9);
    }

    // ---------- §3.14b: the flagrant-2 counter + derived predicate (#034 F/I) ----------

    /**
     * #034 F: {@code isEjectedForFlagrant()} is a DERIVED predicate over a monotonic
     * counter — the third of the same shape in this class, alongside {@code
     * isFouledOut()} ({@code fouls >= 6}) and {@code isEjected()} ({@code
     * technicalFouls >= 2}).
     *
     * <p>The counter is <b>absorbing</b>: once true it stays true, which is what makes a
     * stored flag unnecessary. A flag could never disagree with the counter, so it would
     * only be a second thing to keep in sync (#013/#015).
     */
    @Test
    void isEjectedForFlagrantIsDerivedFromAMonotonicCounterAndIsAbsorbing() {
        PlayerGameState p = TestPlayerFactory.create("p", "T", 10.0);
        assertFalse(p.isEjectedForFlagrant());
        assertEquals(0, p.getFlagrantTwos());

        p.recordFlagrantTwo();
        assertTrue(p.isEjectedForFlagrant(), "ONE flagrant-2 ejects (#034 E/F)");
        assertEquals(1, p.getFlagrantTwos());

        // Absorbing and monotonic: more of them cannot un-eject him.
        p.recordFlagrantTwo();
        assertTrue(p.isEjectedForFlagrant());
        assertEquals(2, p.getFlagrantTwos());
    }

    /**
     * #034 I: the three disqualification counters are INDEPENDENT. A flagrant-2 must not
     * touch {@code fouls} (its call sites charge that separately, through the ordinary
     * recordFoul() path) and must not touch {@code technicalFouls} — reusing the latter
     * would make getTechnicalFouls() report flagrants, silently corrupting a stat that
     * #033 surfaced on the box score.
     */
    @Test
    void theFlagrantTwoCounterIsIndependentOfFoulsAndTechnicals() {
        PlayerGameState p = TestPlayerFactory.create("p", "T", 10.0);
        p.recordFlagrantTwo();

        assertEquals(0, p.getFouls(),
                "recordFlagrantTwo() counts an EJECTION CAUSE, not a foul — summing the "
                        + "two would double-count (#033 D's trap in reverse)");
        assertEquals(0, p.getTechnicalFouls(),
                "…and never rides technicalFouls, which #033 exposed on the box score");
        assertFalse(p.isFouledOut());
        assertFalse(p.isEjected());
        assertTrue(p.isEjectedForFlagrant(), "…only the flagrant predicate fires");
    }

    /**
     * #034 I: a flagrant IS a personal foul, so when the call sites charge it normally
     * it feeds the six-foul limit and the foul-trouble curve like any other. A player
     * can therefore foul out ON a flagrant — automatic, requiring no code.
     */
    @Test
    void aFlagrantChargedNormallyStillFeedsTheSixFoulLimitAndFoulTrouble() {
        PlayerGameState p = TestPlayerFactory.create("p", "T", 10.0);
        for (int i = 0; i < config.foulOutLimit() - 1; i++) {
            p.recordFoul();
        }
        assertFalse(p.isFouledOut());
        assertEquals(config.foulOutLimit() - 1, p.foulTroubleLevel());

        // The sixth foul happens to be a flagrant-2: both effects apply.
        p.recordFoul();
        p.recordFlagrantTwo();

        assertTrue(p.isFouledOut(), "A flagrant counts toward the six (#034 I)");
        assertTrue(p.isEjectedForFlagrant(), "…and ejects on its own grade");
        assertEquals(config.foulOutLimit(), p.getFouls(),
                "…counted exactly once, not twice");
    }
}
