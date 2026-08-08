package software.daveturner.gametime.cucumber;

import io.cucumber.java.en.*;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import software.daveturner.gametime.model.*;

import java.util.*;

/**
 * §3.6 game endpoints over HTTP. Shares the Cucumber Spring context configured by
 * {@link CucumberStepDefs} (only that class carries @CucumberContextConfiguration);
 * this class just adds glue for the /v1/game/* surface.
 */
public class GameStepDefs {

    @Autowired
    RestTemplate restTemplate;

    @LocalServerPort
    int port;

    private String gameBaseUrl;
    private int status;
    private GameResult result;
    private GameEvent[] events;

    @io.cucumber.java.en.Given("server is running for game test")
    public void server_is_running_for_game_test() {
        gameBaseUrl = "http://localhost:" + port + "/api/v1/game";
    }

    @When("simulategame is called for {string} vs {string}")
    public void simulategame_is_called(String home, String away) {
        SimulateGameRequest body = new SimulateGameRequest();
        body.setHomeTeamId(home);
        body.setAwayTeamId(away);
        try {
            ResponseEntity<GameResult> r = restTemplate.postForEntity(
                    gameBaseUrl + "/simulate", body, GameResult.class);
            result = r.getBody();
            status = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
    }

    @When("fetchgame is called for the last simulated game")
    public void fetchgame_is_called_for_last_game() {
        try {
            ResponseEntity<GameResult> r = restTemplate.getForEntity(
                    gameBaseUrl + "/" + result.getGame().getId(), GameResult.class);
            result = r.getBody();
            status = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
    }

    @When("fetchgame is called for {string}")
    public void fetchgame_is_called_for(String gameId) {
        try {
            ResponseEntity<GameResult> r = restTemplate.getForEntity(
                    gameBaseUrl + "/" + gameId, GameResult.class);
            status = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
    }

    @When("playbyplay is called for the last simulated game")
    public void playbyplay_is_called_for_last_game() {
        try {
            ResponseEntity<GameEvent[]> r = restTemplate.getForEntity(
                    gameBaseUrl + "/" + result.getGame().getId() + "/play-by-play",
                    GameEvent[].class);
            events = r.getBody();
            status = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
    }

    @When("playbyplay is called for {string}")
    public void playbyplay_is_called_for(String gameId) {
        try {
            ResponseEntity<GameEvent[]> r = restTemplate.getForEntity(
                    gameBaseUrl + "/" + gameId + "/play-by-play", GameEvent[].class);
            status = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
    }

    @Then("game api returns http status code {string}")
    public void game_api_returns_http_status_code(String expected) {
        Assertions.assertEquals(Integer.parseInt(expected), status);
    }

    @Then("the game result is FINAL with both box scores populated")
    public void the_game_result_is_final_with_box_scores() {
        Assertions.assertEquals(GameResult.class, result.getClass());
        Assertions.assertEquals(Game.StatusEnum.FINAL, result.getGame().getStatus());
        Assertions.assertFalse(result.getHomeBoxScore().isEmpty());
        Assertions.assertFalse(result.getAwayBoxScore().isEmpty());
    }

    @Then("the play-by-play is non-empty and sequence-ordered")
    public void the_play_by_play_is_ordered() {
        Assertions.assertNotNull(events);
        Assertions.assertTrue(events.length > 0);
        int prev = 0;
        for (GameEvent e : events) {
            Assertions.assertTrue(e.getSequence() > prev);
            prev = e.getSequence();
        }
    }
}
