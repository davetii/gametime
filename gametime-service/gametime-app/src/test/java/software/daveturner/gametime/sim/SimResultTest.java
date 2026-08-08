package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimResultTest {

    @Test
    void constructorAndGetters() {
        SimResult r = new SimResult("g1", "H", "A", 105, 98, 4, 180);
        assertEquals("g1", r.getGameId());
        assertEquals("H", r.getHomeTeamId());
        assertEquals("A", r.getAwayTeamId());
        assertEquals(105, r.getHomeScore());
        assertEquals(98, r.getAwayScore());
        assertEquals(4, r.getPeriods());
        assertEquals(180, r.getTotalEvents());
    }
}
