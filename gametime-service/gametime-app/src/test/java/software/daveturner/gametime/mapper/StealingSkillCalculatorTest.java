package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StealingSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new StealingSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageStealingReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighAwarenessRaisesStealing() {
        player.setAwareness(16);
        assertPlayer(13.3d, calc);
    }
}
