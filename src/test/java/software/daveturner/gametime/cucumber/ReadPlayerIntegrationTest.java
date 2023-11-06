package software.daveturner.gametime.cucumber;

import io.cucumber.java.en.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
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
    @When("readplayer is called with {string}")
    public void when_readplayer_is_called_with(String id) {
        p = api.readPlayer(UUID.fromString(id)).getBody();
    }


    @Then("api returns {string} , {string} , {string} and {string}")
    public void then_api_returns(String status, String firstName, String lastName, String position) {
        Assertions.assertEquals(p.getStatus().getValue(), status);
        Assertions.assertEquals(p.getFirstName(), firstName);
        Assertions.assertEquals(p.getLastName(), lastName);
        Assertions.assertEquals(p.getPosition().getValue(), position);
    }

}
