package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RimProtectionSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new RimProtectionSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageRimProtectionReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureTallExplosivePlayerProtectsRim() {
        player.setVerticality(16);
        player.setSize(16);
        assertPlayer(15.9d, calc);
    }

    @Test
    public void ensureSmallPlayerCannotProtectRim() {
        player.setSize(5);
        assertPlayer(6.6d, calc);
    }
}
