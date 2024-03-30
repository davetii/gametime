package software.daveturner.gametime.cucumber;

import io.cucumber.java.en.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.http.*;
import software.daveturner.gametime.api.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.service.*;

@SpringBootTest
public class FetchTeamIntegrationTest {

    @Autowired
    private GametimeService service;
    @Autowired
    V1ApiDelegateimpl api = new V1ApiDelegateimpl(service);

    Team t;

    String httpStatus;

    @When("fetchteam is called with {string}")
    public void when_fetchteam_is_called_with(String id) {
        ResponseEntity<Team> r = api.fetchTeam(id);
        t = r.getBody();
        httpStatus = r.getStatusCode().toString();
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
        Assertions.assertEquals(httpStatus, status);
    }
}
