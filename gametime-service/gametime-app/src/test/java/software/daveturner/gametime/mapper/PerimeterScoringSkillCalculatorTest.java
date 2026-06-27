package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PerimeterScoringSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new PerimeterScoringSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAveragePerimeterReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureGoodShooterRatesHigh() {
        player.setShotSkill(16);
        assertPlayer(13.5d, calc);
    }
}
