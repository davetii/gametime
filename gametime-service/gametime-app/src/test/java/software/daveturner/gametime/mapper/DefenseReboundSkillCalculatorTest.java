package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefenseReboundSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new DefenseReboundSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageDefenseReboundsReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureBigAndStrongReboundsWell() {
        player.setSize(16);
        player.setStrength(16);
        assertPlayer(14.6d, calc);
    }
}
