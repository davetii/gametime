package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TeamDefenseSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new TeamDefenseSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageTeamDefenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighPersonReturnsExpected() {
        player.setCohesion(10);
        assertPlayer(12.7, calc);
    }

    @Test
    public void ensureSlowReturnsExpected() {
        player.setSpeed(2);
        assertPlayer(2.5d, calc);
    }
}
