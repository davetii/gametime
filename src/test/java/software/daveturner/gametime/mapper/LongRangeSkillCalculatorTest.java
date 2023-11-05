package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LongRangeSkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new LongRangeSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageLongRangeReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }
}
