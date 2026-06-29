package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class BoxScoreEntityTest {

    BoxScoreEntity b1 = createBoxScore("1");
    BoxScoreEntity b2 = createBoxScore("1");
    BoxScoreEntity b3 = createBoxScore("2");

    BoxScoreEntity empty_box = new BoxScoreEntity();

    @Test
    void equalsAndHashCode() {
        assertTrue(b1.equals(b2));
        assertTrue(b2.equals(b1));
        assertFalse(b1.equals(b3));
        assertFalse(b2.equals(b3));
        assertTrue(b3.equals(b3));
    }

    @Test
    void toStringIncludesId() {
        Assertions.assertTrue(b1.toString().contains("id=1"));
    }

    @Test
    void assertAccessors() {
        BoxScoreEntity b = new BoxScoreEntity();
        b.setId("bs1");
        b.setGameId("g1");
        b.setPlayerId("p1");
        b.setPoints(24);
        b.setOffensiveRebounds(2);
        b.setDefensiveRebounds(7);
        b.setAssists(5);
        b.setSteals(1);
        b.setBlocks(3);
        b.setTurnovers(2);
        b.setFouls(4);
        b.setMinutes(34);
        b.setFieldGoalsAttempted(18);
        b.setFieldGoalsMade(9);
        b.setThreePointersAttempted(6);
        b.setThreePointersMade(3);
        b.setFreeThrowsAttempted(4);
        b.setFreeThrowsMade(3);

        assertEquals("bs1", b.getId());
        assertEquals("g1", b.getGameId());
        assertEquals("p1", b.getPlayerId());
        assertEquals(24, b.getPoints());
        assertEquals(2, b.getOffensiveRebounds());
        assertEquals(7, b.getDefensiveRebounds());
        assertEquals(5, b.getAssists());
        assertEquals(1, b.getSteals());
        assertEquals(3, b.getBlocks());
        assertEquals(2, b.getTurnovers());
        assertEquals(4, b.getFouls());
        assertEquals(34, b.getMinutes());
        assertEquals(18, b.getFieldGoalsAttempted());
        assertEquals(9, b.getFieldGoalsMade());
        assertEquals(6, b.getThreePointersAttempted());
        assertEquals(3, b.getThreePointersMade());
        assertEquals(4, b.getFreeThrowsAttempted());
        assertEquals(3, b.getFreeThrowsMade());
    }

    private BoxScoreEntity createBoxScore(String id) {
        BoxScoreEntity b = new BoxScoreEntity();
        b.setId(id);
        return b;
    }
}
