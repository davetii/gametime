package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TeamDefenseSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new TeamDefenseSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageTeamDefenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighCohesionRaisesTeamDefense() {
        player.setCohesion(16);
        assertPlayer(13.1d, calc);
    }

    @Test
    public void ensureBigEgoLowersTeamDefense() {
        player.setEgo(16);
        assertPlayer(9.4d, calc);
    }
}
