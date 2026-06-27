package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransitionSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new TransitionSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageTransitionReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureFastPlayerExcelsInTransition() {
        player.setSpeed(16);
        assertPlayer(13.3d, calc);
    }
}
