package software.daveturner.gametime.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.exception.*;
import software.daveturner.gametime.model.*;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class V1ApiDelegateimplTest {

    // seed player already assigned to a team (team ATL)
    private static final String SEED_PLAYER_ID = "330eb324-3382-4fca-b20e-2b4c7e278047";

    @Autowired
    V1ApiDelegate api;

    private Player newPlayer() {
        Player p = new Player();
        p.setFirstName("Test");
        p.setLastName("Player");
        p.setStatus(Player.StatusEnum.BENCH);
        p.setPosition(Player.PositionEnum.PG);
        p.setHeight("6-3");
        p.setWeight(200);
        p.setYearsPro(2);
        p.setAgility(10);
        p.setCharisma(10);
        p.setCohesion(10);
        p.setDetermination(10);
        p.setEgo(10);
        p.setEndurance(10);
        p.setEnergy(10);
        p.setHandle(10);
        p.setHealth(10);
        p.setIntelligence(10);
        p.setLuck(10);
        p.setShotSelection(10);
        p.setShotSkill(10);
        p.setSize(10);
        p.setSpeed(10);
        p.setStrength(10);
        p.setVerticality(10);
        p.setWingspan(10);
        p.setComposure(10);
        p.setAggression(10);
        p.setAwareness(10);
        return p;
    }

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

    @Test
    void ensureCreatePlayerGeneratesIdAndReturnsPlayerWithSkills() {
        Player created = api.createPlayer(newPlayer()).getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertFalse(created.getId().isBlank());
        assertEquals("Test", created.getFirstName());
        // skills are derived on the way out
        assertNotNull(created.getSkills());
        assertNotNull(created.getSkills().getAcumen());
        // a freshly created player is round-trippable via readPlayer
        assertEquals(200, api.readPlayer(created.getId()).getStatusCode().value());
    }

    @Test
    void ensureUpdatePlayerPersistsChanges() {
        Player created = api.createPlayer(newPlayer()).getBody();
        created.setLastName("Updated");
        Player updated = api.updatePlayer(created).getBody();
        assertNotNull(updated);
        assertEquals("Updated", updated.getLastName());
        assertEquals("Updated", api.readPlayer(created.getId()).getBody().getLastName());
    }

    @Test
    void ensureUpdatePlayerThrowsResourceNotFoundWhenPlayerMissing() {
        Player ghost = newPlayer();
        ghost.setId("does-not-exist");
        assertThrows(ResourceNotFoundException.class, () -> api.updatePlayer(ghost));
    }

    @Test
    void ensureAddPlayerToTeamSucceedsForUnassignedPlayer() {
        Player created = api.createPlayer(newPlayer()).getBody();
        assertNull(created.getCurrentTeamId()); // free agent on creation
        assertEquals(200, api.addPlayerToTeam("NY", created.getId()).getStatusCode().value());
        // the newly assigned player now shows up on the team's roster
        boolean onRoster = api.fetchTeam("NY").getBody().getPlayers().stream()
                .anyMatch(p -> created.getId().equals(p.getId()));
        assertTrue(onRoster);
        // and the player now reports its current team
        assertEquals("NY", api.readPlayer(created.getId()).getBody().getCurrentTeamId());
    }

    @Test
    void ensureAddPlayerToTeamRecordsHistory() {
        Player created = api.createPlayer(newPlayer()).getBody();
        assertTrue(api.fetchPlayerHistory(created.getId()).getBody().isEmpty());
        api.addPlayerToTeam("NY", created.getId());
        var history = api.fetchPlayerHistory(created.getId()).getBody();
        assertEquals(1, history.size());
        assertEquals("NY", history.get(0).getTeamId());
        assertEquals(created.getId(), history.get(0).getPlayerId());
        assertEquals("SIGN", history.get(0).getTransactionType().getValue());
        assertNotNull(history.get(0).getTransactionDate());
    }

    @Test
    void ensureFetchPlayerHistoryThrowsResourceNotFoundWhenPlayerMissing() {
        assertThrows(ResourceNotFoundException.class,
                () -> api.fetchPlayerHistory("does-not-exist"));
    }

    @Test
    void ensureSeedPlayerReportsCurrentTeam() {
        // seed player loaded from roster.csv is on team ATL
        assertEquals("ATL", api.readPlayer(SEED_PLAYER_ID).getBody().getCurrentTeamId());
    }

    @Test
    void ensureAddPlayerToTeamThrowsConflictWhenPlayerAlreadyOnTeam() {
        assertThrows(ResourceConflictException.class,
                () -> api.addPlayerToTeam("NY", SEED_PLAYER_ID));
    }

    @Test
    void ensureAddPlayerToTeamThrowsResourceNotFoundWhenTeamMissing() {
        Player created = api.createPlayer(newPlayer()).getBody();
        assertThrows(ResourceNotFoundException.class,
                () -> api.addPlayerToTeam("NOPE", created.getId()));
    }

    @Test
    void ensureAddPlayerToTeamThrowsResourceNotFoundWhenPlayerMissing() {
        assertThrows(ResourceNotFoundException.class,
                () -> api.addPlayerToTeam("NY", "does-not-exist"));
    }
}