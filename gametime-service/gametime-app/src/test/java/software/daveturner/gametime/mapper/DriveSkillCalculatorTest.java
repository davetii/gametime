package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DriveSkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new DriveSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageDriveReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureBigEgoReturnsExpected() {
        player.setEgo(9); assertPlayer(7, calc);
    }

    @Test
    public void ensureShotSkillReturnsExpected() {
        player.setShotSkill(9); assertPlayer(7, calc);
    }

    @Test
    public void ensureLargeSizeReturnsExpected() {
        player.setSize(9); assertPlayer(3, calc);
    }

    @Test
    public void ensureLowEnergyReturnsExpected() {
        player.setEnergy(2); assertPlayer(2, calc);
    }

    @Test
    public void ensureYearsProdReturnsExpected() {
        player.setYearsPro(20);
        assertPlayer(1.0, calc);
    }
}
