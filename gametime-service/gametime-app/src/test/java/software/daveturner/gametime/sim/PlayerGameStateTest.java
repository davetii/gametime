package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerGameStateTest {

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

        PlayerGameState p = new PlayerGameState("p1", "T1", entry);
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

    @Test
    void shotTypeWeightForDrive() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                15, 12, 8, 5, 3, 10, 10, 10, 10, 10, 10, 10, 10);
        assertEquals(27.0, p.shotTypeWeight(ShotType.DRIVE)); // drive + finishing
    }

    @Test
    void shotTypeWeightForThree() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1",
                5, 5, 5, 5, 18, 10, 10, 10, 10, 10, 10, 10, 10);
        assertEquals(18.0, p.shotTypeWeight(ShotType.THREE));
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

        PlayerGameState p = new PlayerGameState("p1", "T1", entry);
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

        PlayerGameState p = new PlayerGameState("p1", "T1", entry);
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
        PlayerGameState p = new PlayerGameState("p1", "T1", entry);
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
        for (int i = 0; i < SimConfig.FOUL_OUT_LIMIT - 1; i++) {
            p.recordFoul();
            assertFalse(p.isFouledOut(), "not fouled out below the limit");
        }
        p.recordFoul(); // hits the limit
        assertTrue(p.isFouledOut());
        assertEquals(SimConfig.FOUL_OUT_LIMIT, p.getFouls());
    }

    // --- §3.13 foul trouble (decisions.md #031 B/E) ---

    @Test
    void foulTroubleLevelIsDerivedFromTheFoulCounter() {
        PlayerGameState p = TestPlayerFactory.create("p1", "T1", 10.0);
        assertEquals(0, p.foulTroubleLevel());
        for (int i = 1; i <= SimConfig.FOUL_OUT_LIMIT; i++) {
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
        for (int i = 0; i < SimConfig.FOUL_OUT_LIMIT + 3; i++) {
            p.recordFoul();
        }
        assertEquals(SimConfig.FOUL_OUT_LIMIT, p.foulTroubleLevel());
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
}
