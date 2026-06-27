package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DriveSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new DriveSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageDriveReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureQuickPlayerDrivesBetter() {
        player.setSpeed(16);
        assertPlayer(11.9d, calc);
    }

    @Test
    public void ensureBigPlayerDrivesWorse() {
        player.setSize(16);
        assertPlayer(8.9d, calc);
    }

    @Test
    public void ensureAgingLegsDeclineAtDrive() {
        player.setYearsPro(20);
        assertPlayer(7.5d, calc);
    }
}
