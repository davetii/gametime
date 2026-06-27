package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FinishingSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new FinishingSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageFinishingReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureExplosiveLeaperFinishesWell() {
        player.setVerticality(16);
        assertPlayer(13.3d, calc);
    }
}
