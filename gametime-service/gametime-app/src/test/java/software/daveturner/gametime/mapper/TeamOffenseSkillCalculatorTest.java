package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TeamOffenseSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new TeamOffenseSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageTeamOffenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighIntelReturnsExpected() {
        player.setIntelligence(8);
        assertPlayer(6.7d, calc);
    }

    @Test
    public void ensureHighEgoLowersTeamOffenseiveSkill() {
        player.setEgo(9);
        assertPlayer(3d, calc);
    }
}
