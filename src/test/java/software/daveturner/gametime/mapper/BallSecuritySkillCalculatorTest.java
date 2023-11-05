package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BallSecuritySkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new BallSecuritySkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageBallSecurityReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighEnergyAffectsBallSecurity() {
        player.setEnergy(19);
        assertPlayer(3d, calc);
    }

    @Test
    public void ensureHighEgoAffectsBallSecurity() {
        player.setEgo(19);
        assertPlayer(3d, calc);
    }

    @Test
    public void ensureHighEnduranceAffectsBallSecurity() {
        player.setEndurance(18);
        assertPlayer(8d, calc);
    }
}
