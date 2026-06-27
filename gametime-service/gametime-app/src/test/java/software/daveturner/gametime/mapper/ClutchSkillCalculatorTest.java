package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClutchSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new ClutchSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageClutchReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureComposedPlayerIsClutch() {
        player.setComposure(16);
        assertPlayer(13.3d, calc);
    }
}
