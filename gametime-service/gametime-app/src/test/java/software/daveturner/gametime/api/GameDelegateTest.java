package software.daveturner.gametime.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.transaction.annotation.*;
import software.daveturner.gametime.exception.*;
import software.daveturner.gametime.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §3.6 game endpoints through the delegate (the fetchTeam pattern): simulate,
 * get, and play-by-play against real seed teams on H2. Covers seed resolution
 * (#024 B), the same-team 422 guard (#024 F), the 404s, the home/away box-score
 * split (#024 A), and sequence-ordered play-by-play (#024 D).
 */
@SpringBootTest
@Transactional
class GameDelegateTest {

    @Autowired
    V1ApiDelegate api;

    private static SimulateGameRequest request(String home, String away, Long seed) {
        SimulateGameRequest r = new SimulateGameRequest();
        r.setHomeTeamId(home);
        r.setAwayTeamId(away);
        r.setSeed(seed);
        return r;
    }

    @Test
    void simulateReturnsGameResultWithSplitBoxScores() {
        GameResult result = api.simulateGame(request("BOS", "LA", 42L)).getBody();

        assertNotNull(result);
        Game game = result.getGame();
        assertEquals("BOS", game.getHomeTeamId());
        assertEquals("LA", game.getAwayTeamId());
        assertEquals(Game.StatusEnum.FINAL, game.getStatus());
        assertNotNull(game.getId());

        // Both teams take the floor, so both buckets are populated and disjoint.
        assertFalse(result.getHomeBoxScore().isEmpty());
        assertFalse(result.getAwayBoxScore().isEmpty());
        Set<String> home = new HashSet<>();
        result.getHomeBoxScore().forEach(b -> home.add(b.getPlayerId()));
        Set<String> away = new HashSet<>();
        result.getAwayBoxScore().forEach(b -> away.add(b.getPlayerId()));
        assertTrue(Collections.disjoint(home, away),
                "a player belongs to exactly one team's box score");

        // Box-score points reconcile with the game header total.
        int homePoints = result.getHomeBoxScore().stream().mapToInt(BoxScore::getPoints).sum();
        int awayPoints = result.getAwayBoxScore().stream().mapToInt(BoxScore::getPoints).sum();
        assertEquals(game.getHomeScore() + game.getAwayScore(), homePoints + awayPoints);
    }

    @Test
    void simulateWithProvidedSeedPersistsAndEchoesThatSeed() {
        GameResult result = api.simulateGame(request("CHI", "NY", 777L)).getBody();
        assertEquals(777L, result.getGame().getSeed());

        // The persisted game is re-fetchable with the same seed on its header.
        String gameId = result.getGame().getId();
        assertEquals(777L, api.fetchGame(gameId).getBody().getGame().getSeed());
    }

    @Test
    void simulateWithoutSeedRollsAndPersistsARandomSeed() {
        GameResult result = api.simulateGame(request("MIA", "PHI", null)).getBody();
        // A fresh seed was rolled and recorded (not left null).
        assertNotNull(result.getGame().getSeed());
    }

    @Test
    void simulateSameTeamThrowsUnprocessable() {
        assertThrows(ResourceUnprocessableException.class,
                () -> api.simulateGame(request("BOS", "BOS", 1L)));
    }

    @Test
    void simulateUnknownTeamThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> api.simulateGame(request("NOPE", "LA", 1L)));
    }

    @Test
    void fetchGameReturnsThePersistedResult() {
        String gameId = api.simulateGame(request("BOS", "LA", 42L)).getBody().getGame().getId();

        GameResult fetched = api.fetchGame(gameId).getBody();
        assertEquals(gameId, fetched.getGame().getId());
        assertFalse(fetched.getHomeBoxScore().isEmpty());
        assertFalse(fetched.getAwayBoxScore().isEmpty());
    }

    @Test
    void fetchGameUnknownIdThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> api.fetchGame("does-not-exist"));
    }

    @Test
    void fetchPlayByPlayReturnsEventsInSequenceOrder() {
        String gameId = api.simulateGame(request("BOS", "LA", 42L)).getBody().getGame().getId();

        List<GameEvent> events = api.fetchPlayByPlay(gameId).getBody();
        assertFalse(events.isEmpty());

        int prev = 0;
        for (GameEvent e : events) {
            assertTrue(e.getSequence() > prev, "events are strictly sequence-ordered");
            prev = e.getSequence();
            assertNotNull(e.getPlayType());
        }

        // At least one assisted SHOT surfaces assistPlayerId (#024 D / #022 B).
        boolean anyAssist = events.stream().anyMatch(e -> e.getAssistPlayerId() != null);
        assertTrue(anyAssist, "play-by-play should surface at least one assister");
    }

    @Test
    void fetchPlayByPlayUnknownIdThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> api.fetchPlayByPlay("does-not-exist"));
    }
}
