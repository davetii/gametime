package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AcumenSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new AcumenSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageAcumenReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighIntelligenceRaisesAcumen() {
        player.setIntelligence(16);
        assertPlayer(13.1d, calc);
    }

    @Test
    public void ensureBigEgoLowersAcumen() {
        player.setEgo(16);
        assertPlayer(9.4d, calc);
    }

    @Test
    public void ensureVeteranGetsExperienceBoost() {
        player.setYearsPro(12);
        assertPlayer(11.5d, calc);
    }

    @Test
    public void ensureRookieIsPenalized() {
        player.setYearsPro(1);
        assertPlayer(8.5d, calc);
    }
}
