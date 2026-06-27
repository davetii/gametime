package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OffBallMovementSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new OffBallMovementSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageOffBallMovementReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureAwarePlayerMovesWellOffBall() {
        player.setAwareness(16);
        assertPlayer(13.3d, calc);
    }
}
