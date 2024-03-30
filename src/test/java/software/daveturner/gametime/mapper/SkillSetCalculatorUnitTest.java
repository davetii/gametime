package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import software.daveturner.gametime.model.Player;

import java.util.*;

public class SkillSetCalculatorUnitTest {

    protected static final int AVERAGE_ATTRIBUTE = 5;
    protected static final int DEFAULT_YEARS_PRO = 5;
    protected static final double AVERAGE_SKILLSET = 5D;

    public Player player;

    SkillCalculator calc;

    @BeforeEach
    public void setup() {
        player  = BASE_PLAYER();
    }

    protected Player BASE_PLAYER() {
        Player player = new Player();
        player.setId("330eb324-3382-4fca-b20e-2b4c7e278047");
        player.setFirstName("firstname");
        player.setLastName("lastname");
        player.setYearsPro(DEFAULT_YEARS_PRO);
        player.setAgility(AVERAGE_ATTRIBUTE);
        player.setCharisma(AVERAGE_ATTRIBUTE);
        player.setDetermination(AVERAGE_ATTRIBUTE);
        player.setEgo(AVERAGE_ATTRIBUTE);
        player.setEndurance(AVERAGE_ATTRIBUTE);
        player.setEnergy(AVERAGE_ATTRIBUTE);
        player.setHandle(AVERAGE_ATTRIBUTE);
        player.setHealth(AVERAGE_ATTRIBUTE);
        player.setIntelligence(AVERAGE_ATTRIBUTE);
        player.setLuck(AVERAGE_ATTRIBUTE);
        player.setCohesion(AVERAGE_ATTRIBUTE);
        player.setShotSelection(AVERAGE_ATTRIBUTE);
        player.setShotSkill(AVERAGE_ATTRIBUTE);
        player.setSize(AVERAGE_ATTRIBUTE);
        player.setSpeed(AVERAGE_ATTRIBUTE);
        player.setStrength(AVERAGE_ATTRIBUTE);
        player.setYearsPro(4);
        return player;
    }

    public void assertPlayer(double d, SkillCalculator calc) {
        Assertions.assertEquals(calc.round(d), calc.calc(player));
    }
}
