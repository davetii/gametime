package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PostScoringSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new PostScoringSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAveragePostReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }
}
