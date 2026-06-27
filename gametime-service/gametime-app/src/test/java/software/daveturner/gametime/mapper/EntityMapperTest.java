package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EntityMapperTest {

    public static final String GMFIRSTNAME = "gmfirstname";
    private static final String PLAYERFIRSTNAME = "TEST_FIRST_NAME";
    private static final String PLAYERLASTNAME = "TEST_LAST_NAME";

    private static final String TESTID = "34bddf7f-0ec8-4478-83b0-74325b07c148";
    private static final String TEST_NAME = "TEST_NAME";
    private static final String TEST_ID_STRING = "TEST_ID_STRING";
    private static final String TEST_LOCALE = "TEST_LOCALE";
    private static final Integer TESTATTRIB = 10;
    private static final String CONFERENCEID = "EAST";
    private static final String CONFERENCENAME = "TEST_CONFERENCE_NAME";
    public static final String COACHFIRSTNAME = "coachfirstname";
    public static final String COACHLASTNAME = "coachlastname";
    public static final String GMLASTNAME = "gmlastname";
    public static final String TEAMID = "NY";
    public static final String TEAMLOCALE = "TEAM_LOCALE";
    public static final String TEAMNAME = "TEAM_NAME";

    @Autowired
    EntityMapper mapper;

    @Test
    public void entityToTeamInfoReturnsExpected() {
        TeamEntity entity  = testTeamEntity();
        entity.setCoach(testCoachEntity());
        entity.setGm(testGMEntity());
        Team team = mapper.entityToTeam(entity, List.of(testPLayerEntity()));
        assertTeam(team);
        assertEquals(CONFERENCEID, team.getConference().getValue());
        assertEquals(COACHFIRSTNAME, team.getCoach().getFirstName());
        assertEquals(COACHLASTNAME, team.getCoach().getLastName());
        assertEquals(GMFIRSTNAME, team.getGm().getFirstName());
        assertEquals(GMLASTNAME, team.getGm().getLastName());
        assertEquals(1, team.getPlayers().size());
        assertTrue(team.getPlayers().stream().findFirst().isPresent());
        assertEquals(PLAYERFIRSTNAME, team.getPlayers().stream().findFirst().get().getFirstName());
        assertEquals(PLAYERLASTNAME, team.getPlayers().stream().findFirst().get().getLastName());
    }

    private void assertTeam(Team t) {
        assertEquals(TEAMLOCALE, t.getLocale());
        assertEquals(TEAMNAME, t.getName());
        assertEquals(TEAMID, t.getId().getValue());
    }

    @Test public void testUUIStuff() {

        String s_uuid = "34bddf7f-0ec8-4478-83b0-74325b07c148";
        System.out.println(UUID.fromString(s_uuid));
    }

    @Test
    public void ensureEntityToCoachReturnsExpected() {
        Coach coach = mapper.entityToCoach(testCoachEntity());
        assertEquals(COACHFIRSTNAME, coach.getFirstName());
        assertEquals(COACHLASTNAME, coach.getLastName());
    }

    @Test
    public void ensureEntityToGMReturnsExpected() {
        GMEntity e = new GMEntity();
        e.setFirstName(PLAYERFIRSTNAME);
        e.setLastName(PLAYERLASTNAME);
        GM gm = mapper.entityToGm(e);
        assertEquals(PLAYERFIRSTNAME, gm.getFirstName());
        assertEquals(PLAYERLASTNAME, gm.getLastName());
    }

    @Test
    public void ensureEntityToTeamReturnsExpectedWithNullGM() {
        TeamEntity entity = testTeamEntity();
        entity.setCoach(testCoachEntity());
        Team team = mapper.entityToTeam(entity, List.of());
        assertEquals(TEAMNAME, team.getName());
        assertEquals(TEAMID, team.getId().toString());
        assertEquals(mapper.entityToCoach(testCoachEntity()).getFirstName(), team.getCoach().getFirstName());
        assertEquals(mapper.entityToCoach(testCoachEntity()).getLastName(), team.getCoach().getLastName());
    }

    @Test
    public void ensureEntityToTeamReturnsExpectedWithNullCoach() {
        TeamEntity entity = testTeamEntity();
        entity.setGm(testGMEntity());
        Team team = mapper.entityToTeam(entity, List.of());
        assertEquals(TEAMNAME, team.getName());
        assertEquals(TEAMID, team.getId().toString());
        assertEquals(mapper.entityToGm(testGMEntity()).getFirstName(), team.getGm().getFirstName());
        assertEquals(mapper.entityToGm(testGMEntity()).getLastName(), team.getGm().getLastName());
    }

    @Test
    public void ensureEntityToPlayerReturnsExpected() {
        assertPLayer(mapper.mapEntityToPlayer(testPLayerEntity()));
    }

    @Test
    public void ensureMapPlayerToEntityReturnsExpected() {
        Player player = mapper.mapEntityToPlayer(testPLayerEntity());
        PlayerEntity entity = mapper.mapPlayerToEntity(player);
        assertEquals(TESTID, entity.getId());
        assertEquals(PLAYERFIRSTNAME, entity.getFirstName());
        assertEquals(PLAYERLASTNAME, entity.getLastName());
        assertEquals(Status.ACTIVE, entity.getStatus());
        assertEquals(Position.PG, entity.getPosition());
        assertEquals(TESTATTRIB, entity.getAgility());
        assertEquals(TESTATTRIB, entity.getAwareness());
        assertEquals(4, entity.getYearsPro());
        // skills are derived (never persisted) and the player→team link lives in
        // player_team, so the mapped entity carries no team coupling at all.
    }

    @Test
    public void ensureMapPlayerToEntityMapsPositionByIdNotName() {
        // WING's model value is "W", which must map back to the WING enum, not fail
        PlayerEntity src = testPLayerEntity();
        src.setPosition(Position.WING);
        Player player = mapper.mapEntityToPlayer(src);
        assertEquals("W", player.getPosition().getValue());
        PlayerEntity entity = mapper.mapPlayerToEntity(player);
        assertEquals(Position.WING, entity.getPosition());
    }

    @Test
    public void ensureHistToTransactionReturnsExpected() {
        PlayerTeamHistEntity e = new PlayerTeamHistEntity();
        e.setPlayerId(TESTID);
        e.setTeamId(TEAMID);
        e.setTransactionType(TransactionType.SIGN);
        e.setTransactionDate(java.time.LocalDateTime.now());
        PlayerTransaction t = mapper.histToTransaction(e);
        assertEquals(TESTID, t.getPlayerId());
        assertEquals(TEAMID, t.getTeamId());
        assertEquals("SIGN", t.getTransactionType().getValue());
        assertNotNull(t.getTransactionDate());
    }

    @Test public void ensureEntityToTeamHandlesNull() {
        Team emptyTeam = new Team();
        Assertions.assertEquals(emptyTeam.getId(), mapper.entityToTeam(null, List.of()).getId());;
    }

    @Test public void ensureEntityToGmHandlesNull() {
        GM empty = new GM();
        Assertions.assertEquals(empty.getFirstName(), mapper.entityToGm(null).getFirstName());;
    }

    @Test public void ensureEntityToCoachHandlesNull() {
        Coach empty = new Coach();
        Assertions.assertEquals(empty.getFirstName(), mapper.entityToCoach(null).getFirstName());;
    }

    private void assertPLayer(Player player) {
        assertEquals(TESTATTRIB, player.getAgility());
        assertEquals(TESTATTRIB, player.getCharisma());
        assertEquals(TESTATTRIB, player.getCohesion());
        assertEquals(TESTATTRIB, player.getDetermination());
        assertEquals(TESTATTRIB, player.getEgo());
        assertEquals(TESTATTRIB, player.getEndurance());
        assertEquals(TESTATTRIB, player.getEgo());
        assertEquals(TESTATTRIB, player.getHandle());
        assertEquals(TESTATTRIB, player.getHealth());
        assertEquals(TESTATTRIB, player.getIntelligence());
        assertEquals(TESTATTRIB, player.getLuck());
        assertEquals(TESTATTRIB, player.getShotSelection());
        assertEquals(TESTATTRIB, player.getShotSkill());
        assertEquals(TESTATTRIB, player.getSize());
        assertEquals(TESTATTRIB, player.getStrength());
        assertEquals(TESTATTRIB, player.getSpeed());

        assertEquals(Status.ACTIVE.toString(), player.getStatus().toString());
        assertEquals(Position.PG.toString(), player.getPosition().toString());

        assertEquals(PLAYERLASTNAME, player.getLastName());
        assertEquals(PLAYERFIRSTNAME, player.getFirstName());
        assertEquals(TESTID, player.getId().toString());

    }

    private CoachEntity testCoachEntity() {
        CoachEntity entity = new CoachEntity();
        entity.setFirstName(COACHFIRSTNAME);
        entity.setLastName(COACHLASTNAME);
        return entity;
    }

    private GMEntity testGMEntity() {
        GMEntity entity = new GMEntity();
        entity.setFirstName(GMFIRSTNAME);
        entity.setLastName(GMLASTNAME);
        return entity;
    }

    private PlayerEntity testPLayerEntity() {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(TESTID);
        entity.setStatus(Status.ACTIVE);
        entity.setPosition(Position.PG);
        entity.setLastName(PLAYERLASTNAME);
        entity.setFirstName(PLAYERFIRSTNAME);
        entity.setAgility(TESTATTRIB);
        entity.setCharisma(TESTATTRIB);
        entity.setCohesion(TESTATTRIB);
        entity.setDetermination(TESTATTRIB);
        entity.setEgo(TESTATTRIB);
        entity.setEndurance(TESTATTRIB);
        entity.setEnergy(TESTATTRIB);
        entity.setHandle(TESTATTRIB);
        entity.setHealth(TESTATTRIB);
        entity.setIntelligence(TESTATTRIB);
        entity.setLuck(TESTATTRIB);
        entity.setShotSelection(TESTATTRIB);
        entity.setShotSkill(TESTATTRIB);
        entity.setSize(TESTATTRIB);
        entity.setStrength(TESTATTRIB);
        entity.setSpeed(TESTATTRIB);
        entity.setVerticality(TESTATTRIB);
        entity.setWingspan(TESTATTRIB);
        entity.setComposure(TESTATTRIB);
        entity.setAggression(TESTATTRIB);
        entity.setAwareness(TESTATTRIB);
        entity.setYearsPro(4);
        return entity;
    }

    private TeamEntity testTeamEntity() {
        TeamEntity entity  = new TeamEntity();
        entity.setId(TEAMID);
        entity.setLocale(TEAMLOCALE);
        entity.setName(TEAMNAME);
        entity.setConference("EAST");
        return entity;
    }





}