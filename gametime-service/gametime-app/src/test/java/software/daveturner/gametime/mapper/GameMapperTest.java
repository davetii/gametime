package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the §3.6 game entity→model mapping (#024 A/D): the three
 * entity maps and the home/away box-score split. Uses hand-built entities so
 * the split logic is exercised without running the engine.
 */
@SpringBootTest
class GameMapperTest {

    @Autowired
    EntityMapper mapper;

    @Test
    void entityToGameMapsEveryHeaderFieldIncludingSeed() {
        GameEntity e = new GameEntity();
        e.setId("g1");
        e.setHomeTeamId("BOS");
        e.setAwayTeamId("LA");
        e.setStatus(GameStatus.FINAL);
        e.setHomeScore(110);
        e.setAwayScore(104);
        e.setPeriods(4);
        e.setSeed(1234567890123L);

        Game g = mapper.entityToGame(e);

        assertEquals("g1", g.getId());
        assertEquals("BOS", g.getHomeTeamId());
        assertEquals("LA", g.getAwayTeamId());
        assertEquals(Game.StatusEnum.FINAL, g.getStatus());
        assertEquals(110, g.getHomeScore());
        assertEquals(104, g.getAwayScore());
        assertEquals(4, g.getPeriods());
        assertEquals(1234567890123L, g.getSeed());
    }

    @Test
    void entityToGameEventMapsAllFieldsIncludingAssist() {
        GameEventEntity e = new GameEventEntity();
        e.setSequence(7);
        e.setPeriod(1);
        e.setOffenseTeamId("BOS");
        e.setDefenseTeamId("LA");
        e.setPlayType(PlayType.SHOT);
        e.setOutcome("MADE_2PT");
        e.setPrimaryPlayerId("scorer");
        e.setAssistPlayerId("passer");

        GameEvent ev = mapper.entityToGameEvent(e);

        assertEquals(7, ev.getSequence());
        assertEquals(1, ev.getPeriod());
        assertEquals("BOS", ev.getOffenseTeamId());
        assertEquals("LA", ev.getDefenseTeamId());
        assertEquals(GameEvent.PlayTypeEnum.SHOT, ev.getPlayType());
        assertEquals("MADE_2PT", ev.getOutcome());
        assertEquals("scorer", ev.getPrimaryPlayerId());
        assertEquals("passer", ev.getAssistPlayerId());
    }

    @Test
    void entityToGameEventLeavesAssistNullWhenUnassisted() {
        GameEventEntity e = new GameEventEntity();
        e.setSequence(1);
        e.setPeriod(1);
        e.setOffenseTeamId("BOS");
        e.setDefenseTeamId("LA");
        e.setPlayType(PlayType.TURNOVER);
        e.setOutcome("STEAL");
        e.setPrimaryPlayerId("ballhandler");
        // no assister on a non-SHOT event

        GameEvent ev = mapper.entityToGameEvent(e);

        assertNull(ev.getAssistPlayerId());
        assertEquals(GameEvent.PlayTypeEnum.TURNOVER, ev.getPlayType());
    }

    @Test
    void entityToBoxScoreMapsEveryCounter() {
        BoxScoreEntity e = boxScore("p1", 25);
        e.setOffensiveRebounds(2);
        e.setDefensiveRebounds(6);
        e.setAssists(7);
        e.setSteals(3);
        e.setBlocks(1);
        e.setTurnovers(4);
        e.setFouls(5);
        e.setMinutes(34);
        e.setFieldGoalsAttempted(18);
        e.setFieldGoalsMade(9);
        e.setThreePointersAttempted(6);
        e.setThreePointersMade(3);
        e.setFreeThrowsAttempted(5);
        e.setFreeThrowsMade(4);

        BoxScore bs = mapper.entityToBoxScore(e);

        assertEquals("p1", bs.getPlayerId());
        assertEquals(25, bs.getPoints());
        assertEquals(2, bs.getOffensiveRebounds());
        assertEquals(6, bs.getDefensiveRebounds());
        assertEquals(7, bs.getAssists());
        assertEquals(3, bs.getSteals());
        assertEquals(1, bs.getBlocks());
        assertEquals(4, bs.getTurnovers());
        assertEquals(5, bs.getFouls());
        assertEquals(34, bs.getMinutes());
        assertEquals(18, bs.getFieldGoalsAttempted());
        assertEquals(9, bs.getFieldGoalsMade());
        assertEquals(6, bs.getThreePointersAttempted());
        assertEquals(3, bs.getThreePointersMade());
        assertEquals(5, bs.getFreeThrowsAttempted());
        assertEquals(4, bs.getFreeThrowsMade());
    }

    @Test
    void toGameResultSplitsBoxScoresHomeVsAwayByRosterMembership() {
        GameEntity game = new GameEntity();
        game.setId("g1");
        game.setHomeTeamId("BOS");
        game.setAwayTeamId("LA");
        game.setStatus(GameStatus.FINAL);

        // home roster = {h1, h2}; anything else is away
        Set<String> homePlayerIds = Set.of("h1", "h2");
        List<BoxScoreEntity> rows = List.of(
                boxScore("h1", 20),
                boxScore("a1", 18),
                boxScore("h2", 12),
                boxScore("a2", 9));

        GameResult result = mapper.toGameResult(game, rows, homePlayerIds);

        assertEquals("g1", result.getGame().getId());
        Set<String> home = playerIds(result.getHomeBoxScore());
        Set<String> away = playerIds(result.getAwayBoxScore());
        assertEquals(Set.of("h1", "h2"), home);
        assertEquals(Set.of("a1", "a2"), away);
    }

    @Test
    void toGameResultHandlesEmptyBoxScores() {
        GameEntity game = new GameEntity();
        game.setId("g1");
        game.setHomeTeamId("BOS");
        game.setAwayTeamId("LA");

        GameResult result = mapper.toGameResult(game, List.of(), Set.of("h1"));

        assertTrue(result.getHomeBoxScore().isEmpty());
        assertTrue(result.getAwayBoxScore().isEmpty());
    }

    private static Set<String> playerIds(List<BoxScore> rows) {
        Set<String> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.getPlayerId()));
        return ids;
    }

    private static BoxScoreEntity boxScore(String playerId, int points) {
        BoxScoreEntity e = new BoxScoreEntity();
        e.setId(UUID.randomUUID().toString());
        e.setGameId("g1");
        e.setPlayerId(playerId);
        e.setPoints(points);
        return e;
    }
}
