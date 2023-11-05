package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AcumenSkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new AcumenSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageAcumenReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureHighAltheticismReturnsExpected() {
        player.setAgility(9);
        assertPlayer(6D, calc);
    }

    @Test
    public void ensureBigEgoReturnsExpected() {
        player.setEgo(10);
        assertPlayer(2, calc);
        player.setEgo(9);
        assertPlayer(3, calc);
        player.setEgo(8);
        assertPlayer(4, calc);
        player.setEgo(7);
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureExtraHandleReturnsExpected() {
        player.setHandle(11); assertPlayer(11, calc);
        player.setHandle(9); assertPlayer(7.7, calc);
        player.setHandle(8); assertPlayer(6.5, calc);
    }

    @Test
    public void ensureVetReturnsExpected() {
        player.setYearsPro(12);
        assertPlayer(9, calc);

        player.setYearsPro(9);
        assertPlayer(7.5, calc);

        player.setYearsPro(8);
        assertPlayer(7, calc);

        player.setYearsPro(6);
        assertPlayer(6, calc);

        player.setYearsPro(5);
        assertPlayer(AVERAGE_SKILLSET, calc);

        player.setYearsPro(1);
        assertPlayer(3, calc);

        player.setYearsPro(2);
        assertPlayer(4, calc);
    }

    @Test
    public void ensureAgilityEgoMixReturnsExpected() {
        player.setEgo(10);
        player.setAgility(9);
        assertPlayer(3d, calc);
    }

    @Test
    public void ensureRepeatResultReturnsExpected() {
        AcumenSkillCalculator acumenSkillCalculator = new AcumenSkillCalculator();
        assertEquals(25D, acumenSkillCalculator.repeatAdjustment(11, 20d));
        assertEquals(17D, acumenSkillCalculator.repeatAdjustment(1, 20d));
    }
}
