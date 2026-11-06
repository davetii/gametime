package software.daveturner.gametime.service;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.BoxScoreEntity;
import software.daveturner.gametime.entity.GameEntity;
import software.daveturner.gametime.model.TeamGameStats;
import software.daveturner.gametime.service.StatsMath.Sums;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatsMathTest {

    @Test
    void sumsOfRowsTreatNullColumnsAsZeroAndCountStarts() {
        BoxScoreEntity a = new BoxScoreEntity();
        a.setPoints(10);
        a.setStarted(true);
        BoxScoreEntity b = new BoxScoreEntity();
        Sums s = Sums.of(List.of(a, b));
        assertEquals(2, s.games());
        assertEquals(1, s.started());
        assertEquals(10, s.points());
        assertEquals(0, s.rebounds());
    }

    @Test
    void sumsFromARowReadNullAggregatesAsZero() {
        Object[] row = new Object[19];
        row[0] = "ignored";
        row[1] = 3L;
        row[3] = 42L;
        Sums s = Sums.fromRow(row, 1);
        assertEquals(3, s.games());
        assertEquals(42, s.points());
        assertEquals(0, s.freeThrowsMade());
    }

    @Test
    void gameMinutesAddFiveForEachOvertimePeriod() {
        assertEquals(48, StatsMath.gameMinutes(1, 4));
        assertEquals(58, StatsMath.gameMinutes(1, 6));
        assertEquals(101, StatsMath.gameMinutes(2, 9));
        assertEquals(0, StatsMath.gameMinutes(0, 0));
    }

    @Test
    void rateFormulasAreNullWithoutADenominator() {
        assertNull(StatsMath.pace(null, 48));
        assertNull(StatsMath.pace(100L, 0));
        assertEquals(100.0 * 48 / 53, StatsMath.pace(100L, 53), 1e-12);
        assertNull(StatsMath.rating(100, null));
        assertNull(StatsMath.rating(100, 0L));
        assertEquals(110.0, StatsMath.rating(110, 100L));
        assertNull(StatsMath.net(null, 1.0));
        assertNull(StatsMath.net(1.0, null));
        assertEquals(2.5, StatsMath.net(3.5, 1.0));
        assertNull(StatsMath.perGame(Sums.of(List.of()), 0));
    }

    @Test
    void teamGameStatsForAGameWithoutPossessionsOrPeriodsLeaveTheRatesNull() {
        GameEntity g = new GameEntity();
        g.setHomeTeamId("H");
        g.setAwayTeamId("A");
        BoxScoreEntity h = new BoxScoreEntity();
        h.setTeamId("H");
        h.setPoints(7);
        TeamGameStats s = StatsMath.teamGameStats(g, List.of(h), true);
        assertEquals(7, s.getTotals().getPoints());
        assertNull(s.getPace());
        assertNull(s.getOffensiveRating());
        assertNull(s.getNetRating());
        assertEquals(0, StatsMath.teamGameStats(g, List.of(h), false).getTotals().getPoints());
    }
}
