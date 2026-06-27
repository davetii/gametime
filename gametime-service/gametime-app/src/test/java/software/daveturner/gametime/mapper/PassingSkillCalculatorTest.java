package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PassingSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new PassingSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAveragePassingReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighHandleAndIntelligencePassWell() {
        player.setHandle(16);
        player.setIntelligence(16);
        assertPlayer(17.2d, calc);
    }
}
