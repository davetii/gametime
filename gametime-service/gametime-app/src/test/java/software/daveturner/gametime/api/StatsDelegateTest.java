package software.daveturner.gametime.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.model.GameResult;
import software.daveturner.gametime.model.SimulateGameRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsDelegateTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    V1ApiDelegate api;

    private String simulateAndPickAPlayer() {
        SimulateGameRequest r = new SimulateGameRequest();
        r.setHomeTeamId("BOS");
        r.setAwayTeamId("LA");
        r.setSeed(42L);
        GameResult result = api.simulateGame(r).getBody();
        return result.getHomeBoxScore().get(0).getPlayerId();
    }

    @Test
    void playerStatsAndGameLog200And404() throws Exception {
        String playerId = simulateAndPickAPlayer();
        mvc.perform(get("/api/v1/player/{id}/stats", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId));
        mvc.perform(get("/api/v1/player/{id}/gamelog", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value("BOS"));
        mvc.perform(get("/api/v1/player/nope/stats")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/player/nope/gamelog")).andExpect(status().isNotFound());
    }

    @Test
    void teamStats200And404() throws Exception {
        simulateAndPickAPlayer();
        mvc.perform(get("/api/v1/team/BOS/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value("BOS"));
        mvc.perform(get("/api/v1/team/NOPE/stats")).andExpect(status().isNotFound());
    }

    @Test
    void leaders200And400() throws Exception {
        simulateAndPickAPlayer();
        mvc.perform(get("/api/v1/league/leaders").param("category", "POINTS").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("POINTS"))
                .andExpect(jsonPath("$.basis").value("PER_GAME"));
        mvc.perform(get("/api/v1/league/leaders").param("category", "BOGUS")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/league/leaders")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/league/leaders").param("category", "POINTS").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/league/leaders").param("category", "POINTS").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gameHighs200And400() throws Exception {
        simulateAndPickAPlayer();
        mvc.perform(get("/api/v1/league/game-highs").param("category", "REBOUNDS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("REBOUNDS"));
        mvc.perform(get("/api/v1/league/game-highs").param("category", "FIELD_GOAL_PCT"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/league/game-highs").param("category", "POINTS").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gameResultCarriesBothTeamLines() {
        SimulateGameRequest r = new SimulateGameRequest();
        r.setHomeTeamId("BOS");
        r.setAwayTeamId("LA");
        r.setSeed(42L);
        GameResult result = api.simulateGame(r).getBody();
        assertEquals("BOS", result.getHomeTeamStats().getTeamId());
        assertEquals("LA", result.getAwayTeamStats().getTeamId());
        assertEquals(result.getGame().getHomeScore(), result.getHomeTeamStats().getTotals().getPoints());
        assertEquals(result.getGame().getAwayScore(), result.getAwayTeamStats().getTotals().getPoints());
        assertEquals(result.getHomeTeamStats().getPossessions(), result.getAwayTeamStats().getPossessions());
        double expectedOrtg = result.getGame().getHomeScore() * 100.0 / result.getHomeTeamStats().getPossessions();
        assertEquals(expectedOrtg, result.getHomeTeamStats().getOffensiveRating(), 1e-9);
        assertEquals(result.getAwayTeamStats().getOffensiveRating(),
                result.getHomeTeamStats().getDefensiveRating(), 1e-9);
        assertNotNull(result.getHomeTeamStats().getPace());
    }
}
