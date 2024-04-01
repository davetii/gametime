package software.daveturner.gametime.cucumber;

import io.cucumber.java.en.*;
import io.cucumber.spring.*;
import org.junit.*;
import org.junit.jupiter.api.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.*;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit4.*;
import org.springframework.web.client.*;
import software.daveturner.gametime.model.*;

@RunWith(SpringRunner.class)
@ContextConfiguration
@CucumberContextConfiguration
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
@Ignore
public class StepDefs {

    @Autowired
    RestTemplate restTemplate;

    protected Team t;

    protected int httpStatus;

    protected Player p;

    @LocalServerPort
    protected int port;

    protected String baseUrl;


    @Given("server is running for team test")
    public void server_is_running() {
        baseUrl = "http://localhost:" + port + "/api/v1/team/";
    }
    @Given("server is running for player test")
    public void server_is_running_for_player_test() {
        baseUrl = "http://localhost:" + port + "/api/v1/player/";
    }

    @When("readplayer is called with {string}")
    public void when_readplayer_is_called_with(String id) {
        try {
            ResponseEntity<Player> r = restTemplate.getForEntity(baseUrl + id, Player.class);
            p = r.getBody();
            httpStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            httpStatus = e.getStatusCode().value();
        }
    }

    @When("fetchteam is called with {string}")
    public void when_fetchteam_is_called_with(String id) {
        try {
            ResponseEntity<Team> r = restTemplate.getForEntity(baseUrl + id, Team.class);
            t = r.getBody();
            httpStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            httpStatus = e.getStatusCode().value();
        }
    }

    @Then("fetchteam api returns {string}, {string} , {string} and {string}")
    public void then_fetchteam_api_returns(String id, String coach, String gm, String conference) {
        Assertions.assertEquals(t.getId().getValue(), id);
        Assertions.assertEquals(t.getCoach().getFirstName() + " " + t.getCoach().getLastName(), coach);
        Assertions.assertEquals(t.getGm().getFirstName() + " " + t.getGm().getLastName(), gm);
        Assertions.assertEquals(t.getConference().getValue(), conference);
    }

    @Then("fetchteam api returns http status code {string}")
    public void then_fetchteam_api_returns_http_status_code(String status) {
        Assertions.assertEquals(httpStatus, Integer.valueOf(status));
    }

    @Then("readplayer api returns {string} , {string} , {string} and {string}")
    public void readplayer_api_returns(String thing, String firstName, String lastName, String position) {
        Assertions.assertEquals(p.getStatus().getValue(), thing);
        Assertions.assertEquals(p.getFirstName(), firstName);
        Assertions.assertEquals(p.getLastName(), lastName);
        Assertions.assertEquals(p.getPosition().getValue(), position);
    }

    @Then("readplayer api returns http status code {string}")
    public void readplayer_api_returns_http_status_code(String status) {
        Assertions.assertEquals(httpStatus, Integer.parseInt(status));
    }
}