package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TeamOffenseSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new TeamOffenseSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageTeamOffenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureBigEgoLowersTeamOffense() {
        player.setEgo(16);
        assertPlayer(9.4d, calc);
    }
}
