package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefenseReboundSkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new DefenseReboundSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageDefenseReboundsReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureLowEgoReturnsExpected() {
        player.setEgo(1);
        assertPlayer(2, calc);
    }

    @Test
    public void ensureLowEnduranceReturnsExpected() {
        player.setEndurance(5);
        assertPlayer(5, calc);
        player.setEndurance(3);
        assertPlayer(4, calc);
    }

    @Test
    public void ensureLowSizeReturnsExpected() {
        player.setSize(2);
        assertPlayer(2.3, calc);
    }

    @Test
    public void ensureBigAndStrongReturnsExpected() {
        player.setStrength(8);
        player.setSize(8);
        assertPlayer(8.3d, calc);
    }

    @Test
    public void ensureBigEgoHelpsDefensiveRebounding() {
        player.setEgo(8);
        assertPlayer(6, calc);
    }

    @Test
    public void ensureSuperAgileReturnsExpected() {
        player.setAgility(9);
        assertPlayer(7.5d, calc);
    }

    @Test
    public void ensureBigPlayerReturnsExpected() {
        player.setSize(9);
        assertPlayer(8.9d, calc);
    }
}
