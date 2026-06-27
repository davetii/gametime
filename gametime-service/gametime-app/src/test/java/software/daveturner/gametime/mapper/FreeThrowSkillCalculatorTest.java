package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FreeThrowSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new FreeThrowSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageFreeThrowsReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureGoodShooterRatesHigh() {
        player.setShotSkill(16);
        assertPlayer(14.5d, calc);
    }

    @Test
    public void ensureVeteranImproves() {
        player.setYearsPro(12);
        assertPlayer(11.5d, calc);
    }
}
