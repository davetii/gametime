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
        assertEquals(1, p.getFouls());
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
}
