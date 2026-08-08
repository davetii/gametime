package software.daveturner.gametime.repo;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.entity.*;

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
