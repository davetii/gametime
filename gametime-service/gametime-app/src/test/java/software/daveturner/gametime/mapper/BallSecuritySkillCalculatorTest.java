package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BallSecuritySkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new BallSecuritySkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageBallSecurityReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureStrongHandleRaisesBallSecurity() {
        player.setHandle(16);
        assertPlayer(13.5d, calc);
    }
}
