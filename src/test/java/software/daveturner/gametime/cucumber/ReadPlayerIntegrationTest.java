package software.daveturner.gametime.cucumber;

import io.cucumber.java.en.*;
import io.cucumber.junit.*;
import org.junit.jupiter.api.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.http.*;
import software.daveturner.gametime.api.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.service.*;

import java.util.*;

@SpringBootTest
public class ReadPlayerIntegrationTest {

    @Autowired
    private GametimeService service;
    @Autowired
    V1ApiDelegateimpl api = new V1ApiDelegateimpl(service);

    Player p;
    String actualStatus;
    @When("readplayer is called with {string}")
    public void when_readplayer_is_called_with(String id) {
        ResponseEntity<Player> r = api.readPlayer(id);
        p = r.getBody();
        actualStatus = r.getStatusCode().toString();
    }


    @Then("readplayer api returns {string} , {string} , {string} and {string}")
    public void then_api_returns(String status, String firstName, String lastName, String position) {
        Assertions.assertEquals(p.getStatus().getValue(), status);
        Assertions.assertEquals(p.getFirstName(), firstName);
        Assertions.assertEquals(p.getLastName(), lastName);
        Assertions.assertEquals(p.getPosition().getValue(), position);
    }

    @Then("readplayer api returns http status code {string}")
    public void then_readplayer_api_returns_http_status_code(String status) {
        System.out.println("statusCode: " + actualStatus);
        Assertions.assertEquals(actualStatus, status);
    }

}
