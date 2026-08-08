package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class GameEntityTest {

    GameEntity g1 = createGame("1");
    GameEntity g2 = createGame("1");
    GameEntity g3 = createGame("2");

    GameEntity empty_game = new GameEntity();

    @Test
    void equalsAndHashCode() {
        assertTrue(g1.equals(g2));
        assertTrue(g2.equals(g1));
        assertFalse(g1.equals(g3));
        assertFalse(g2.equals(g3));
        assertTrue(g3.equals(g3));
    }

    @Test
    void toStringIncludesId() {
        Assertions.assertTrue(g1.toString().contains("id=1"));
    }

    @Test
    void assertAccessors() {
        GameEntity g = new GameEntity();
        g.setId("g99");
        g.setHomeTeamId("BOS");
        g.setAwayTeamId("LAL");
        g.setStatus(GameStatus.FINAL);
        g.setHomeScore(101);
        g.setAwayScore(99);
        g.setPeriods(4);

        assertEquals("g99", g.getId());
        assertEquals("BOS", g.getHomeTeamId());
        assertEquals("LAL", g.getAwayTeamId());
        assertEquals(GameStatus.FINAL, g.getStatus());
        assertEquals(101, g.getHomeScore());
        assertEquals(99, g.getAwayScore());
        assertEquals(4, g.getPeriods());
    }

    private GameEntity createGame(String id) {
        GameEntity g = new GameEntity();
        g.setId(id);
        return g;
    }
}
