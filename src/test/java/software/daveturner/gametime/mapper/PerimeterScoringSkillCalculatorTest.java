package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PerimeterScoringSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new PerimeterScoringSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAveragePerimeterReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighShotSkillReturnsExpected() {
        player.setShotSkill(10);
        assertPlayer(12d, calc);
    }

    @Test
    public void ensureLowShotSkillReturnsExpected() {
        player.setShotSkill(1);
        assertPlayer(0.4d, calc);
    }
}
