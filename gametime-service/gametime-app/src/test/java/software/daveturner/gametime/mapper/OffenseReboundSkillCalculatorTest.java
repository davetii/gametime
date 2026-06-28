package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OffenseReboundSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new OffenseReboundSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageOffenseReboundsReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureBigDeterminedPlayerCrashesGlass() {
        player.setSize(16);
        player.setDetermination(16);
        assertPlayer(14.6d, calc);
    }
}
