package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import static org.junit.jupiter.api.Assertions.*;

class GameDataTest {

    @Test
    void addScoreTracksHomeAndAway() {
        GameData data = new GameData();
        data.setHomeTeamId("H");
        data.setAwayTeamId("A");

        data.addScore("H", 2);
        data.addScore("H", 3);
        data.addScore("A", 2);

        assertEquals(5, data.getHomeScore());
        assertEquals(2, data.getAwayScore());
    }

    @Test
    void addEventAccumulatesEvents() {
        GameData data = new GameData();
        data.addEvent("H", "A", 1, 1, PlayType.SHOT, "MADE_2PT_DRIVE", "p1");
        data.addEvent("A", "H", 1, 2, PlayType.TURNOVER, "STOLEN", "p2");

        assertEquals(2, data.getEvents().size());
        assertEquals(PlayType.SHOT, data.getEvents().get(0).playType());
        assertEquals(PlayType.TURNOVER, data.getEvents().get(1).playType());
    }

    @Test
    void periodsGetterAndSetter() {
        GameData data = new GameData();
        data.setPeriods(5);
        assertEquals(5, data.getPeriods());
    }

    @Test
    void teamIdGettersAndSetters() {
        GameData data = new GameData();
        data.setHomeTeamId("BOS");
        data.setAwayTeamId("LA");
        assertEquals("BOS", data.getHomeTeamId());
        assertEquals("LA", data.getAwayTeamId());
    }
}
