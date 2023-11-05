package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndividualDefenseSkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new IndividualDefenseSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageIndividualDefenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureStrongAndFastHasHighIndividualDefense() {
        player.setStrength(9);
        player.setSpeed(9);
        assertPlayer(9.5d, calc);
    }

    @Test
    public void ensureWeakBigGuysLowIndividualDefense() {
        player.setStrength(5);
        player.setSize(9);
        assertPlayer(2.0d, calc);
    }
}
