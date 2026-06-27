package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShotContestSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new ShotContestSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageShotContestReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureLongPlayerContestsWell() {
        player.setWingspan(16);
        assertPlayer(13.3d, calc);
    }
}
