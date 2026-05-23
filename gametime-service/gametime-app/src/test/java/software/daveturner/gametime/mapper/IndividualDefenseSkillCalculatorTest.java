package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.*;
import software.daveturner.gametime.model.*;

public class IndividualDefenseSkillCalculatorTest extends SkillSetCalculatorUnitTest{

    @BeforeEach
    public void setup() {
        calc = new IndividualDefenseSkillCalculator();
        player  = BASE_PLAYER();
    }

    @Test
    public void ensureAverageIndividualDefenseReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureStrongAndFastHasHighIndividualDefense() {
        player.setStrength(9);
        player.setSpeed(9);
        assertPlayer(9.5d, calc);
    }

    @Test
    public void ensureWeakBigGuysLowIndividualDefense() {
        player.setStrength(5);
        player.setSize(9);
        assertPlayer(2.0d, calc);
    }

    @Test public void ensureMaybeLowerIndividualDefenseReturnsExpected() {
        IndividualDefenseSkillCalculator defenseSkillCalculator = new IndividualDefenseSkillCalculator();
        Assertions.assertEquals(0d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 0, 0, 2);
        Assertions.assertEquals(10d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 1, 1, 2);
        Assertions.assertEquals(9d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 1, 1, 3);
        Assertions.assertEquals(9d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 2, 1, 2);
        Assertions.assertEquals(7d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 2, 2, 2);
        Assertions.assertEquals(6d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 2, 3, 2);
        Assertions.assertEquals(5d, defenseSkillCalculator.maybeLowerIndividualDefense(player));

        player = adjustSpeedStrenghtSize(player, 2, 3, 10);
        Assertions.assertEquals(6d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
        player = adjustSpeedStrenghtSize(player, 2, 4, 10);
        Assertions.assertEquals(4d, defenseSkillCalculator.maybeLowerIndividualDefense(player));
    }

    public Player adjustSpeedStrenghtSize(Player p, int Speed, int strength, int size) {
        p.setSpeed(Speed);
        p.setStrength(strength);
        p.setSize(size);
        return p;
    }
}
