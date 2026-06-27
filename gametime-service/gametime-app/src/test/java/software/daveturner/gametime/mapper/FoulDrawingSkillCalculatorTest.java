package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FoulDrawingSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new FoulDrawingSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageFoulDrawingReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureAggressivePlayerDrawsFouls() {
        player.setAggression(16);
        assertPlayer(13.3d, calc);
    }
}
