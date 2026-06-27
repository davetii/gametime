package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ScreenSettingSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new ScreenSettingSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageScreenSettingReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureStrongPlayerSetsGoodScreens() {
        player.setStrength(16);
        assertPlayer(13.3d, calc);
    }
}
