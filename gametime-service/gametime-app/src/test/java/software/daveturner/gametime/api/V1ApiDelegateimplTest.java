package software.daveturner.gametime.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.exception.*;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class V1ApiDelegateimplTest {


    @Autowired
    V1ApiDelegate api;

    @Test
    void ensureFetchLeaguev1ReturnsExpected() {
        Assertions.assertEquals(200, api.fetchLeaguev1().getStatusCode().value());
        Assertions.assertEquals(40,  api.fetchLeaguev1().getBody().size());
    }

    @Test
    void ensureReadPlayerReturnsPlayer() {
        Assertions.assertEquals(200, api.readPlayer("330eb324-3382-4fca-b20e-2b4c7e278047").getStatusCode().value());
    }
    @Test
    void ensureReadPlayerThrowsResourceNotFoundWhenPlayerNotfound() {
        assertThrows(ResourceNotFoundException.class, () -> api.readPlayer("wqeqwe"));
    }

    @Test
    void ensureFetchTeamReturnsExpected() {
        Assertions.assertEquals(200, api.fetchTeam("NY").getStatusCode().value());
    }

    @Test
    void ensureFetchTeamThrowsResourceNotFoundWhenTeamNotfound() {
        assertThrows(ResourceNotFoundException.class, () -> api.fetchTeam("wqeqwe"));
    }
}