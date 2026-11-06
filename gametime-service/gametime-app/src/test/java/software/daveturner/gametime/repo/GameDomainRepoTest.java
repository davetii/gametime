package software.daveturner.gametime.repo;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import software.daveturner.gametime.entity.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trips the Phase 3.1 game-domain entities through H2 to prove the
 * Liquibase schema (release.1.0.4.game.sql) creates the tables, the FKs to the
 * seeded team/player hold, and the repo finders return events in sequence order.
 * Team and player ids are taken from the seeded league so the FKs are valid
 * without hardcoding specific ids.
 */
@SpringBootTest
class GameDomainRepoTest {

    @Autowired
    GameRepo gameRepo;
    @Autowired
    GameEventRepo gameEventRepo;
    @Autowired
    BoxScoreRepo boxScoreRepo;
    @Autowired
    TeamRepo teamRepo;
    @Autowired
    PlayerRepo playerRepo;
    @Autowired
    JdbcTemplate jdbc;

    private String homeTeamId;
    private String awayTeamId;
    private String playerId;

    @BeforeEach
    void seedRefs() {
        List<TeamEntity> teams = new ArrayList<>();
        teamRepo.findAll().forEach(teams::add);
        assertTrue(teams.size() >= 2, "need at least two seeded teams");
        homeTeamId = teams.get(0).getId();
        awayTeamId = teams.get(1).getId();

        PlayerEntity anyPlayer = playerRepo.findAll().iterator().next();
        playerId = anyPlayer.getId();
    }

    @Test
    void persistsAndReadsBackGameWithEventsAndBoxScore() {
        GameEntity game = new GameEntity();
        game.setId("test-game-1");
        game.setHomeTeamId(homeTeamId);
        game.setAwayTeamId(awayTeamId);
        game.setStatus(GameStatus.FINAL);
        game.setHomeScore(101);
        game.setAwayScore(99);
        game.setPeriods(4);
        gameRepo.save(game);

        GameEntity loaded = gameRepo.findById("test-game-1").orElseThrow();
        assertEquals(GameStatus.FINAL, loaded.getStatus());
        assertEquals(homeTeamId, loaded.getHomeTeamId());
        assertEquals(101, loaded.getHomeScore());

        // events: insert out of order, verify the finder returns them by sequence
        gameEventRepo.save(event("ev2", "test-game-1", 2, PlayType.TURNOVER));
        gameEventRepo.save(event("ev1", "test-game-1", 1, PlayType.SHOT));
        gameEventRepo.save(event("ev3", "test-game-1", 3, PlayType.REBOUND));

        List<GameEventEntity> events =
                gameEventRepo.findByGameIdOrderBySequenceAsc("test-game-1");
        assertEquals(3, events.size());
        assertEquals(1, events.get(0).getSequence());
        assertEquals(2, events.get(1).getSequence());
        assertEquals(3, events.get(2).getSequence());

        BoxScoreEntity box = new BoxScoreEntity();
        box.setId("test-box-1");
        box.setGameId("test-game-1");
        box.setPlayerId(playerId);
        box.setPoints(24);
        boxScoreRepo.save(box);

        List<BoxScoreEntity> lines = boxScoreRepo.findByGameId("test-game-1");
        assertEquals(1, lines.size());
        assertEquals(playerId, lines.get(0).getPlayerId());
        assertEquals(24, lines.get(0).getPoints());
    }

    // ddl-auto=update would add a missing column itself, so check Liquibase ran 1.04.6.
    @Test
    void phase4ColumnsKeysAndIndexesComeFromChangeset1046() {
        // The header parses as author "1.04.6 failOnError", id "true".
        assertEquals("EXECUTED", jdbc.queryForObject(
                "select EXECTYPE from DATABASECHANGELOG"
                        + " where FILENAME like '%release.1.0.4.game.sql'"
                        + " and AUTHOR = '1.04.6 failOnError'", String.class));

        List<String> indexes = jdbc.queryForList(
                "select INDEX_NAME from INFORMATION_SCHEMA.INDEXES"
                        + " where TABLE_SCHEMA = 'GAMETIME' and TABLE_NAME = 'BOX_SCORE'",
                String.class);
        assertTrue(indexes.contains("IDX_BOX_SCORE_PLAYER"), indexes.toString());
        assertTrue(indexes.contains("IDX_BOX_SCORE_TEAM"), indexes.toString());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
                        + " where TABLE_SCHEMA = 'GAMETIME' and TABLE_NAME = 'BOX_SCORE'"
                        + " and CONSTRAINT_NAME = 'UQ_BOX_SCORE_GAME_PLAYER'",
                Integer.class));

        LocalDateTime playedAt = LocalDateTime.of(2026, 11, 5, 19, 30);
        GameEntity game = new GameEntity();
        game.setId("test-game-1046");
        game.setHomeTeamId(homeTeamId);
        game.setAwayTeamId(awayTeamId);
        game.setStatus(GameStatus.FINAL);
        game.setHomePossessions(100);
        game.setAwayPossessions(101);
        game.setPlayedAt(playedAt);
        gameRepo.save(game);

        GameEntity loadedGame = gameRepo.findById("test-game-1046").orElseThrow();
        assertEquals(100, loadedGame.getHomePossessions());
        assertEquals(101, loadedGame.getAwayPossessions());
        assertEquals(playedAt, loadedGame.getPlayedAt());

        BoxScoreEntity box = new BoxScoreEntity();
        box.setId("test-box-1046");
        box.setGameId("test-game-1046");
        box.setPlayerId(playerId);
        box.setTeamId(homeTeamId);
        box.setStarted(true);
        boxScoreRepo.save(box);

        BoxScoreEntity loadedBox = boxScoreRepo.findById("test-box-1046").orElseThrow();
        assertEquals(homeTeamId, loadedBox.getTeamId());
        assertEquals(Boolean.TRUE, loadedBox.getStarted());

        BoxScoreEntity duplicate = new BoxScoreEntity();
        duplicate.setId("test-box-1046-dup");
        duplicate.setGameId("test-game-1046");
        duplicate.setPlayerId(playerId);
        assertThrows(DataIntegrityViolationException.class, () -> boxScoreRepo.save(duplicate));
    }

    private GameEventEntity event(String id, String gameId, int seq, PlayType type) {
        GameEventEntity e = new GameEventEntity();
        e.setId(id);
        e.setGameId(gameId);
        e.setSequence(seq);
        e.setPeriod(1);
        e.setOffenseTeamId(homeTeamId);
        e.setDefenseTeamId(awayTeamId);
        e.setPlayType(type);
        e.setPrimaryPlayerId(playerId);
        return e;
    }
}
