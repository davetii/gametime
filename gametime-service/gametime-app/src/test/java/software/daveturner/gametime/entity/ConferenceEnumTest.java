package software.daveturner.gametime.entity;


import org.junit.jupiter.api.*;
import software.daveturner.gametime.model.*;

class ConferenceEnumTest {

    @Test
    public void assertEnum() {
        Assertions.assertNotNull(Team.ConferenceEnum.valueOf("EAST"));
        Assertions.assertNotNull(Team.ConferenceEnum.valueOf("WEST"));
        Assertions.assertNotNull(Team.ConferenceEnum.valueOf("SOUTH"));
        Assertions.assertNotNull(Team.ConferenceEnum.valueOf("NORTH"));
    }

}