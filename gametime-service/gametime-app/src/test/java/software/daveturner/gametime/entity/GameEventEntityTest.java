package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class GameEventEntityTest {

    GameEventEntity e1 = createEvent("1");
    GameEventEntity e2 = createEvent("1");
    GameEventEntity e3 = createEvent("2");

    GameEventEntity empty_event = new GameEventEntity();

    @Test
    void equalsAndHashCode() {
        assertTrue(e1.equals(e2));
        assertTrue(e2.equals(e1));
        assertFalse(e1.equals(e3));
        assertFalse(e2.equals(e3));
        assertTrue(e3.equals(e3));
    }

    @Test
    void toStringIncludesId() {
        Assertions.assertTrue(e1.toString().contains("id=1"));
    }

    @Test
    void assertAccessors() {
        GameEventEntity e = new GameEventEntity();
        e.setId("ev1");
        e.setGameId("g1");
        e.setSequence(7);
        e.setPeriod(1);
        e.setOffenseTeamId("BOS");
        e.setDefenseTeamId("LAL");
        e.setPlayType(PlayType.SHOT);
        e.setOutcome("made 2pt");
        e.setPrimaryPlayerId("p1");

        assertEquals("ev1", e.getId());
        assertEquals("g1", e.getGameId());
        assertEquals(7, e.getSequence());
        assertEquals(1, e.getPeriod());
        assertEquals("BOS", e.getOffenseTeamId());
        assertEquals("LAL", e.getDefenseTeamId());
        assertEquals(PlayType.SHOT, e.getPlayType());
        assertEquals("made 2pt", e.getOutcome());
        assertEquals("p1", e.getPrimaryPlayerId());
    }

    private GameEventEntity createEvent(String id) {
        GameEventEntity e = new GameEventEntity();
        e.setId(id);
        return e;
    }
}
