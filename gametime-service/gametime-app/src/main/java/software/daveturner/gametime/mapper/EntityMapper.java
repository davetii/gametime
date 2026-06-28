package software.daveturner.gametime.mapper;

import org.springframework.stereotype.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.model.*;

import java.time.*;
import java.util.*;

@Component
public class EntityMapper {

    private final SkillMapper skillMapper;

    public EntityMapper(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    /**
     * Map a team with its current roster. The roster is sourced from player_team
     * (not a JPA association on the team): the caller supplies the current
     * assignments (carrying lineup role + rotation order) and a lookup of the
     * corresponding players. Each roster entry pairs a player with its lineup slot.
     */
    public Team entityToTeam(TeamEntity entity, List<PlayerTeamEntity> assignments,
                             Map<String, PlayerEntity> players) {
        if(entity == null) { return new Team(); }
        Team team = new Team();
        if(entity.getCoach() != null) {
            team.setCoach(entityToCoach(entity.getCoach()));
        }

        if(entity.getGm() != null) {
            team.setGm(entityToGm(entity.getGm()));
        }
        team.setLocale(entity.getLocale());
        team.setName(entity.getName());
        team.setId(Team.IdEnum.fromValue(entity.getId()));
        team.setConference(Team.ConferenceEnum.fromValue(entity.getConference()));
        team.setPlayers(new ArrayList<>());
        if (assignments != null) {
            assignments.stream()
                    .filter(pt -> players.containsKey(pt.getPlayerId()))
                    .map(pt -> toRosterEntry(players.get(pt.getPlayerId()), pt))
                    .forEach(team.getPlayers()::add);
        }
        return team;
    }

    public Player mapEntityToPlayer(PlayerEntity e) {
        Player player = new Player();
        player.setStatus(Player.StatusEnum.fromValue(e.getStatus().name()));
        player.setPosition(Player.PositionEnum.fromValue(e.getPosition().id));
        player.setLastName(e.getLastName());
        player.setFirstName(e.getFirstName());
        player.setId(e.getId());
        player.setHeight(e.getHeight());
        player.setWeight(e.getWeight());
        player.setOrigin(e.getOrigin());
        player.setDraftSlot(e.getDraftSlot());
        player.setAgility(e.getAgility());
        player.setCharisma(e.getCharisma());
        player.setCohesion(e.getCohesion());
        player.setDetermination(e.getDetermination());
        player.setEgo(e.getEgo());
        player.setEndurance(e.getEndurance());
        player.setEnergy(e.getEnergy());
        player.setHandle(e.getHandle());
        player.setHealth(e.getHealth());
        player.setIntelligence(e.getIntelligence());
        player.setLuck(e.getLuck());
        player.setShotSelection(e.getShotSelection());
        player.setShotSkill(e.getShotSkill());
        player.setSize(e.getSize());
        player.setStrength(e.getStrength());
        player.setSpeed(e.getSpeed());
        player.setVerticality(e.getVerticality());
        player.setWingspan(e.getWingspan());
        player.setComposure(e.getComposure());
        player.setAggression(e.getAggression());
        player.setAwareness(e.getAwareness());
        player.setYearsPro(e.getYearsPro());
        player.setSkills(skillMapper.mapSkills(player));
        return player;
    }


    public PlayerEntity mapPlayerToEntity(Player p) {
        PlayerEntity e = new PlayerEntity();
        e.setId(p.getId());
        e.setFirstName(p.getFirstName());
        e.setLastName(p.getLastName());
        if (p.getStatus() != null) {
            e.setStatus(Status.valueOf(p.getStatus().getValue()));
        }
        if (p.getPosition() != null) {
            e.setPosition(positionFromId(p.getPosition().getValue()));
        }
        e.setHeight(p.getHeight());
        e.setWeight(p.getWeight());
        e.setOrigin(p.getOrigin());
        e.setDraftSlot(p.getDraftSlot());
        e.setYearsPro(p.getYearsPro());
        e.setAgility(p.getAgility());
        e.setCharisma(p.getCharisma());
        e.setCohesion(p.getCohesion());
        e.setDetermination(p.getDetermination());
        e.setEgo(p.getEgo());
        e.setEndurance(p.getEndurance());
        e.setEnergy(p.getEnergy());
        e.setHandle(p.getHandle());
        e.setHealth(p.getHealth());
        e.setIntelligence(p.getIntelligence());
        e.setLuck(p.getLuck());
        e.setShotSelection(p.getShotSelection());
        e.setShotSkill(p.getShotSkill());
        e.setSize(p.getSize());
        e.setStrength(p.getStrength());
        e.setSpeed(p.getSpeed());
        e.setVerticality(p.getVerticality());
        e.setWingspan(p.getWingspan());
        e.setComposure(p.getComposure());
        e.setAggression(p.getAggression());
        e.setAwareness(p.getAwareness());
        // skills are derived from attributes, never persisted
        return e;
    }

    public PlayerTransaction histToTransaction(PlayerTeamHistEntity e) {
        PlayerTransaction t = new PlayerTransaction();
        t.setPlayerId(e.getPlayerId());
        t.setTeamId(e.getTeamId());
        if (e.getTransactionType() != null) {
            t.setTransactionType(PlayerTransaction.TransactionTypeEnum.fromValue(
                    e.getTransactionType().name()));
        }
        if (e.getTransactionDate() != null) {
            t.setTransactionDate(e.getTransactionDate().atZone(ZoneId.systemDefault())
                    .toOffsetDateTime());
        }
        return t;
    }

    /**
     * Build a roster entry from a player and their current player_team assignment.
     * lineupRole / rotationOrder are nullable (a player on a roster with no lineup
     * set yet has neither).
     */
    public RosterEntry toRosterEntry(PlayerEntity player, PlayerTeamEntity assignment) {
        RosterEntry entry = new RosterEntry();
        entry.setPlayer(mapEntityToPlayer(player));
        if (assignment.getLineupRole() != null) {
            entry.setLineupRole(software.daveturner.gametime.model.LineupRole.fromValue(
                    assignment.getLineupRole().name()));
        }
        entry.setRotationOrder(assignment.getRotationOrder());
        return entry;
    }

    private Position positionFromId(String id) {
        for (Position pos : Position.values()) {
            if (pos.id.equals(id)) {
                return pos;
            }
        }
        throw new IllegalArgumentException("Unknown position id: " + id);
    }

    protected GM entityToGm(GMEntity gmEntity) {
        if(gmEntity == null) { return new GM();}
        GM gm = new GM();
        gm.setLastName(gmEntity.getLastName());
        gm.setFirstName(gmEntity.getFirstName());
        return gm;
    }

    protected Coach entityToCoach(CoachEntity coachEntity) {
        if(coachEntity == null) { return new Coach();}
        Coach coach = new Coach();
        coach.setLastName(coachEntity.getLastName());
        coach.setFirstName(coachEntity.getFirstName());
        coach.setPace(coachEntity.getPace());
        coach.setOffensiveScheme(coachEntity.getOffensiveScheme());
        coach.setDefensiveScheme(coachEntity.getDefensiveScheme());
        coach.setRotationDepth(coachEntity.getRotationDepth());
        coach.setSubstitutionAggressiveness(coachEntity.getSubstitutionAggressiveness());
        return coach;
    }
}
