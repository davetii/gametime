package software.daveturner.gametime.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.exception.*;
import software.daveturner.gametime.model.*;

import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 roster + lineup endpoints. Uses the real H2-seeded league; lineups are
 * built dynamically from each team's actual roster so the tests don't depend on
 * specific seed player ids.
 */
@SpringBootTest
class RosterLineupDelegateTest {

    @Autowired
    V1ApiDelegate api;

    private List<String> rosterPlayerIds(String teamId) {
        return api.fetchTeam(teamId).getBody().getPlayers().stream()
                .map(e -> e.getPlayer().getId())
                .collect(Collectors.toList());
    }

    /** A valid lineup over the given players: first 5 starters (rotation 1..5), rest bench. */
    private LineupRequest validLineup(List<String> playerIds) {
        List<LineupEntry> entries = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            LineupEntry e = new LineupEntry();
            e.setPlayerId(playerIds.get(i));
            if (i < 5) {
                // starters are an unordered set of 5 — no rotation order
                e.setLineupRole(LineupRole.STARTER);
            } else {
                // bench substitution queue: first off the bench = 1, then 2, 3…
                e.setLineupRole(LineupRole.BENCH);
                e.setRotationOrder(i - 4);
            }
            entries.add(e);
        }
        return new LineupRequest().entries(entries);
    }

    // ---- team roster (players now carry lineup slots) ----

    @Test
    void ensureFetchTeamReturnsRosterEntriesWithLineup() {
        var resp = api.fetchTeam("BOS");
        assertEquals(200, resp.getStatusCode().value());
        assertFalse(resp.getBody().getPlayers().isEmpty());
        // each entry carries a hydrated player
        assertNotNull(resp.getBody().getPlayers().get(0).getPlayer().getId());
    }

    @Test
    void ensureSeededRosterHasLineupRolesFromSeedData() {
        // roster.csv seeds player_team.lineup_role; every team ships with 5 STARTERs.
        long starters = api.fetchTeam("BOS").getBody().getPlayers().stream()
                .filter(e -> e.getLineupRole() == LineupRole.STARTER)
                .count();
        assertEquals(5, starters);
    }

    // ---- setLineup happy path ----

    @Test
    void ensureSetLineupAssignsRolesAndReturnsTeam() {
        List<String> players = rosterPlayerIds("CHI");
        var resp = api.setLineup("CHI", validLineup(players));
        assertEquals(200, resp.getStatusCode().value());

        long starters = resp.getBody().getPlayers().stream()
                .filter(e -> e.getLineupRole() == LineupRole.STARTER)
                .count();
        assertEquals(5, starters);
        // role persists across a re-fetch
        long startersAfter = api.fetchTeam("CHI").getBody().getPlayers().stream()
                .filter(e -> e.getLineupRole() == LineupRole.STARTER)
                .count();
        assertEquals(5, startersAfter);
    }

    // ---- setLineup validation failures ----

    @Test
    void ensureSetLineupThrowsBadRequestWhenNotFiveStarters() {
        List<String> players = rosterPlayerIds("MIA");
        LineupRequest req = validLineup(players);
        // demote a starter -> only 4 starters
        req.getEntries().get(0).setLineupRole(LineupRole.BENCH);
        req.getEntries().get(0).setRotationOrder(null);
        assertThrows(ResourceBadRequestException.class, () -> api.setLineup("MIA", req));
    }

    @Test
    void ensureSetLineupThrowsBadRequestOnDuplicatePlayer() {
        List<String> players = rosterPlayerIds("NY");
        LineupRequest req = validLineup(players);
        // duplicate the second player's id onto the first entry
        req.getEntries().get(1).setPlayerId(req.getEntries().get(0).getPlayerId());
        assertThrows(ResourceBadRequestException.class, () -> api.setLineup("NY", req));
    }

    @Test
    void ensureSetLineupThrowsBadRequestOnDuplicateRotationOrder() {
        List<String> players = rosterPlayerIds("PHI");
        LineupRequest req = validLineup(players);
        // two bench players share the same sub-queue position (entries 5 and 6
        // are bench, ordered 1 and 2 by validLineup; collide them on 1)
        req.getEntries().get(6).setRotationOrder(1);
        assertThrows(ResourceBadRequestException.class, () -> api.setLineup("PHI", req));
    }

    @Test
    void ensureSetLineupThrowsBadRequestWhenStarterHasRotationOrder() {
        List<String> players = rosterPlayerIds("TOR");
        LineupRequest req = validLineup(players);
        // a starter must not carry a rotation order (starters are an unordered set)
        req.getEntries().get(0).setRotationOrder(1);
        assertThrows(ResourceBadRequestException.class, () -> api.setLineup("TOR", req));
    }

    @Test
    void ensureSetLineupThrowsNotFoundWhenPlayerNotOnTeam() {
        List<String> players = new ArrayList<>(rosterPlayerIds("DEN"));
        LineupRequest req = validLineup(players);
        // swap a bench player for someone not on DEN
        req.getEntries().get(req.getEntries().size() - 1).setPlayerId("not-on-this-team");
        assertThrows(ResourceNotFoundException.class, () -> api.setLineup("DEN", req));
    }

    @Test
    void ensureSetLineupThrowsNotFoundForMissingTeam() {
        assertThrows(ResourceNotFoundException.class,
                () -> api.setLineup("NOPE", validLineup(rosterPlayerIds("BOS"))));
    }

    @Test
    void ensureSetLineupThrowsBadRequestOnEmptyRequest() {
        assertThrows(ResourceBadRequestException.class,
                () -> api.setLineup("BOS", new LineupRequest().entries(List.of())));
    }

    // ---- removePlayerFromTeam ----

    @Test
    void ensureRemovePlayerReleasesAndRecordsHistory() {
        // create + sign a fresh player so we don't disturb seed rosters other tests use
        Player created = api.createPlayer(freshPlayer()).getBody();
        api.addPlayerToTeam("UT", created.getId());
        assertEquals("UT", api.readPlayer(created.getId()).getBody().getCurrentTeamId());

        assertEquals(200, api.removePlayerFromTeam("UT", created.getId()).getStatusCode().value());

        // back to free agency
        assertNull(api.readPlayer(created.getId()).getBody().getCurrentTeamId());
        // a RELEASE row is appended to history (most-recent first)
        var history = api.fetchPlayerHistory(created.getId()).getBody();
        assertEquals("RELEASE", history.get(0).getTransactionType().getValue());
        assertEquals("UT", history.get(0).getTeamId());
    }

    @Test
    void ensureRemovePlayerThrowsNotFoundWhenAssignmentMissing() {
        Player created = api.createPlayer(freshPlayer()).getBody(); // free agent, no assignment
        assertThrows(ResourceNotFoundException.class,
                () -> api.removePlayerFromTeam("UT", created.getId()));
    }

    @Test
    void ensureRemovePlayerThrowsNotFoundWhenOnDifferentTeam() {
        Player created = api.createPlayer(freshPlayer()).getBody();
        api.addPlayerToTeam("LA", created.getId());
        // assignment exists, but for LA not SF
        assertThrows(ResourceNotFoundException.class,
                () -> api.removePlayerFromTeam("SF", created.getId()));
    }

    private Player freshPlayer() {
        Player p = new Player();
        p.setFirstName("Roster");
        p.setLastName("Tester");
        p.setStatus(Player.StatusEnum.ACTIVE);
        p.setPosition(Player.PositionEnum.PG);
        p.setHeight("6-3");
        p.setWeight(200);
        p.setYearsPro(2);
        p.setAgility(10); p.setCharisma(10); p.setCohesion(10); p.setDetermination(10);
        p.setEgo(10); p.setEndurance(10); p.setEnergy(10); p.setHandle(10);
        p.setHealth(10); p.setIntelligence(10); p.setLuck(10); p.setShotSelection(10);
        p.setShotSkill(10); p.setSize(10); p.setSpeed(10); p.setStrength(10);
        p.setVerticality(10); p.setWingspan(10); p.setComposure(10);
        p.setAggression(10); p.setAwareness(10);
        return p;
    }
}
