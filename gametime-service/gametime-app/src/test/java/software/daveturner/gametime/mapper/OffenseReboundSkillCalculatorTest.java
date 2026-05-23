package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OffenseReboundSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new OffenseReboundSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageOffenseReboundsReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureLowSizeReturnsExpected() {
        player.setSize(2);
        assertPlayer(2.5, calc);
    }

    @Test
    public void ensureHighDeterminationReturnsExpected() {
        player.setDetermination(9);
        assertPlayer(8.5, calc);
    }

    @Test
    public void ensureBigSizeAndSpeedReturnsExepected() {
        player.setSize(8);
        player.setSpeed(8);
        assertPlayer(8, calc);
    }
}
