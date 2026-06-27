package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.*;

public class IndividualDefenseSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new IndividualDefenseSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageIndividualDefenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureQuickDeterminedAwareDefenderRatesHigh() {
        player.setAgility(16);
        player.setDetermination(16);
        player.setAwareness(16);
        assertPlayer(15.6d, calc);
    }

    @Test
    public void ensureFastStrongDefenderGetsComboBoost() {
        player.setSpeed(16);
        player.setStrength(16);
        assertPlayer(11.2d, calc);
    }

    @Test
    public void ensureWeakPlayerFloorsAtScaleMinimum() {
        player.setAgility(4);
        player.setDetermination(4);
        player.setIntelligence(4);
        player.setEgo(4);
        player.setEndurance(4);
        player.setHandle(4);
        player.setLuck(4);
        player.setHealth(4);
        player.setSpeed(4);
        player.setStrength(4);
        player.setAwareness(4);
        player.setWingspan(4);
        assertPlayer(1.0d, calc);
    }

    @Test
    public void ensureRookieIsPenalized() {
        player.setYearsPro(1);
        assertPlayer(8.5d, calc);
    }
}
