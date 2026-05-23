package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PassingSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new PassingSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAveragePassingReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void assertGoodHandle() {
        player.setHandle(9);
        assertPlayer(11, calc);
    }

    @Test
    public void assertGoodIntel() {
        player.setIntelligence(9);
        assertPlayer(10.3, calc);
    }

    @Test
    public void assertBadHandle() {
        player.setHandle(4);
        assertPlayer(4.5, calc);
    }

    @Test
    public void assertBadIntel() {
        player.setIntelligence(4);
        assertPlayer(4.7, calc);
    }
}
