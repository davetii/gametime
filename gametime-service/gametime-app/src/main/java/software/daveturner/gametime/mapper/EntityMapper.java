package software.daveturner.gametime.mapper;

import org.springframework.stereotype.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.model.*;

import java.util.*;

@Component
public class EntityMapper {

    private final SkillMapper skillMapper;

    public EntityMapper(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    public List<Team> mapLeague(List<TeamEntity> entities) {
        List<Team> list = new ArrayList<>();
        for (TeamEntity entity: entities) {
            list.add(entityToTeam(entity));
        }
        return list;
    }

    public Team entityToTeam(TeamEntity entity) {
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
        entityPlayersToPlayers(team, entity.getPlayers());
        return team;
    }

    private void entityPlayersToPlayers(Team team, List<PlayerEntity> players) {
        if (players == null || players.size() == 0) { return; }
        players.forEach(e -> {
            Player player =mapEntityToPlayer(e);
            team.getPlayers().add(player);
        });
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
        player.setYearsPro(e.getYearsPro());
        player.setSkills(skillMapper.mapSkills(player));
        return player;
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
        return coach;
    }
}
