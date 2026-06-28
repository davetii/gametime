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

    @Test
    void ensureSetLineupThrowsBadRequestWhenEntryHasNoRole() {
        List<String> players = rosterPlayerIds("LA");
        LineupRequest req = validLineup(players);
        // an entry with no lineup role is rejected
        req.getEntries().get(0).setLineupRole(null);
        assertThrows(ResourceBadRequestException.class, () -> api.setLineup("LA", req));
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

    // ---- roster rules: default role on sign + size caps ----

    @Test
    void ensureSignedPlayerDefaultsToInactiveLineupRole() {
        Player created = api.createPlayer(freshPlayer()).getBody();
        api.addPlayerToTeam("BUF", created.getId());
        try {
            LineupRole role = api.fetchTeam("BUF").getBody().getPlayers().stream()
                    .filter(e -> e.getPlayer().getId().equals(created.getId()))
                    .findFirst().orElseThrow().getLineupRole();
            assertEquals(LineupRole.INACTIVE, role);
        } finally {
            api.removePlayerFromTeam("BUF", created.getId());
        }
    }

    @Test
    void ensureSignThrowsConflictWhenActiveRosterFull() {
        // NY ships with the smallest seed roster; fill its active roster to the cap.
        String teamId = "NY";
        int existingActive = (int) api.fetchTeam(teamId).getBody().getPlayers().stream()
                .filter(e -> e.getLineupRole() != LineupRole.MINORS)
                .count();
        List<String> signed = new ArrayList<>();
        try {
            for (int i = existingActive; i < 15; i++) {
                Player p = api.createPlayer(freshPlayer()).getBody();
                api.addPlayerToTeam(teamId, p.getId());
                signed.add(p.getId());
            }
            // roster now at the 15-active cap — the next sign must 409
            Player overflow = api.createPlayer(freshPlayer()).getBody();
            signed.add(overflow.getId()); // created but should not get assigned
            assertThrows(ResourceConflictException.class,
                    () -> api.addPlayerToTeam(teamId, overflow.getId()));
        } finally {
            for (String id : signed) {
                try { api.removePlayerFromTeam(teamId, id); } catch (RuntimeException ignored) { }
            }
        }
    }

    @Test
    void ensureSetLineupThrowsBadRequestWhenMinorsExceedsCap() {
        // Exceeding the minors cap (5) needs 5 starters + at least 6 more players, i.e.
        // an >=11-player roster. Rather than assume a seed team is that big, sign enough
        // fresh players to guarantee it, so the test survives any seed roster size.
        String teamId = "HOU";
        List<String> signed = new ArrayList<>();
        try {
            while (rosterPlayerIds(teamId).size() < 11) {
                Player p = api.createPlayer(freshPlayer()).getBody();
                api.addPlayerToTeam(teamId, p.getId());
                signed.add(p.getId());
            }
            List<String> players = rosterPlayerIds(teamId);
            LineupRequest req = validLineup(players); // first 5 STARTER, rest BENCH
            // mark 6 of the non-starters MINORS (no rotation order) -> 6 > cap of 5
            int minorsMarked = 0;
            for (int i = 5; i < req.getEntries().size() && minorsMarked < 6; i++) {
                req.getEntries().get(i).setLineupRole(LineupRole.MINORS);
                req.getEntries().get(i).setRotationOrder(null);
                minorsMarked++;
            }
            assertEquals(6, minorsMarked);
            assertThrows(ResourceBadRequestException.class, () -> api.setLineup(teamId, req));
        } finally {
            for (String id : signed) {
                try { api.removePlayerFromTeam(teamId, id); } catch (RuntimeException ignored) { }
            }
        }
    }

    @Test
    void ensureSetLineupThrowsBadRequestWhenActiveExceedsCap() {
        // VAN (11 seed players, untouched by other tests) — build a 16-player roster
        // (15 active + 1 minors) and try to promote everyone to active via the PUT.
        // A 16th active sign is impossible (the sign cap blocks it), so the only way to
        // a 16-player roster is to park one in MINORS via the PUT, then sign the 16th.
        // Only the signed players are released afterward; the seed roster is left intact.
        String teamId = "VAN";
        List<String> signed = new ArrayList<>();
        try {
            // 1. fill to the 15-active cap (VAN seeds 11 active -> sign 4)
            int active = (int) api.fetchTeam(teamId).getBody().getPlayers().stream()
                    .filter(e -> e.getLineupRole() != LineupRole.MINORS).count();
            for (int i = active; i < 15; i++) {
                Player p = api.createPlayer(freshPlayer()).getBody();
                api.addPlayerToTeam(teamId, p.getId());
                signed.add(p.getId());
            }
            // 2. PUT a valid lineup that parks a *signed* player in MINORS -> 14 active,
            //    1 minors (park a signed one so seed roles are restored on release).
            //    Order the parked player last so validLineup makes it bench, not a
            //    starter — then override it to MINORS without breaking the 5-starter rule.
            String parkId = signed.get(signed.size() - 1);
            List<String> ordered = new ArrayList<>(rosterPlayerIds(teamId));
            ordered.remove(parkId);
            ordered.add(parkId); // parkId is now last -> bench in validLineup
            LineupRequest park = validLineup(ordered);
            park.getEntries().get(park.getEntries().size() - 1)
                    .lineupRole(LineupRole.MINORS).rotationOrder(null);
            assertEquals(200, api.setLineup(teamId, park).getStatusCode().value());
            // 3. room opened (14 active) — sign the 16th player (active 14 -> 15)
            Player p16 = api.createPlayer(freshPlayer()).getBody();
            api.addPlayerToTeam(teamId, p16.getId());
            signed.add(p16.getId());
            // 4. now 16 on the roster; a lineup making all 16 active -> 16 > 15 -> 400
            LineupRequest overfill = validLineup(rosterPlayerIds(teamId));
            assertThrows(ResourceBadRequestException.class,
                    () -> api.setLineup(teamId, overfill));
        } finally {
            for (String id : signed) {
                try { api.removePlayerFromTeam(teamId, id); } catch (RuntimeException ignored) { }
            }
        }
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
