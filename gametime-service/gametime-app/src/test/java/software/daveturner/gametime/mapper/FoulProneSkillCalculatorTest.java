package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FoulProneSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new FoulProneSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageFoulProneReturnsExpected() {
        // Inverted skill (higher = worse), but average player still centers at 10.
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureAggressivePlayerFoulsMore() {
        player.setAggression(16);
        assertPlayer(11.1d, calc);
    }

    @Test
    public void ensureComposedPlayerFoulsLess() {
        player.setComposure(16);
        assertPlayer(8.9d, calc);
    }
}
