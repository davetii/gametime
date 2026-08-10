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
        // weight is stored as VARCHAR (see PlayerEntity) but exposed as an integer
        // in the API; the data is always numeric.
        player.setWeight(weightToInteger(e.getWeight()));
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
        e.setWeight(p.getWeight() == null ? null : p.getWeight().toString());
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

    /**
     * §3.6 (#024 A): map a game header. No per-period line scores — a period line
     * is derivable from the event log (#020), so it isn't stored or exposed.
     */
    public Game entityToGame(GameEntity e) {
        Game game = new Game();
        game.setId(e.getId());
        game.setHomeTeamId(e.getHomeTeamId());
        game.setAwayTeamId(e.getAwayTeamId());
        if (e.getStatus() != null) {
            game.setStatus(Game.StatusEnum.fromValue(e.getStatus().name()));
        }
        game.setHomeScore(e.getHomeScore());
        game.setAwayScore(e.getAwayScore());
        game.setPeriods(e.getPeriods());
        game.setSeed(e.getSeed());
        return game;
    }

    /**
     * §3.6 (#024 D): map a play-by-play event. Surfaces assistPlayerId (the §3.4
     * column, #022 B) — null on unassisted makes and every non-SHOT event. No
     * time field — a display clock is derived on read (#024 E).
     */
    public GameEvent entityToGameEvent(GameEventEntity e) {
        GameEvent event = new GameEvent();
        event.setSequence(e.getSequence());
        event.setPeriod(e.getPeriod());
        event.setOffenseTeamId(e.getOffenseTeamId());
        event.setDefenseTeamId(e.getDefenseTeamId());
        if (e.getPlayType() != null) {
            event.setPlayType(GameEvent.PlayTypeEnum.fromValue(e.getPlayType().name()));
        }
        event.setOutcome(e.getOutcome());
        event.setPrimaryPlayerId(e.getPrimaryPlayerId());
        event.setAssistPlayerId(e.getAssistPlayerId());
        return event;
    }

    /** §3.6: map a single per-player stat line. No teamId (#020, #024 A). */
    public BoxScore entityToBoxScore(BoxScoreEntity e) {
        BoxScore bs = new BoxScore();
        bs.setPlayerId(e.getPlayerId());
        bs.setPoints(e.getPoints());
        bs.setOffensiveRebounds(e.getOffensiveRebounds());
        bs.setDefensiveRebounds(e.getDefensiveRebounds());
        bs.setAssists(e.getAssists());
        bs.setSteals(e.getSteals());
        bs.setBlocks(e.getBlocks());
        bs.setTurnovers(e.getTurnovers());
        bs.setFouls(e.getFouls());
        bs.setTechnicalFouls(e.getTechnicalFouls());
        bs.setMinutes(e.getMinutes());
        bs.setFieldGoalsAttempted(e.getFieldGoalsAttempted());
        bs.setFieldGoalsMade(e.getFieldGoalsMade());
        bs.setThreePointersAttempted(e.getThreePointersAttempted());
        bs.setThreePointersMade(e.getThreePointersMade());
        bs.setFreeThrowsAttempted(e.getFreeThrowsAttempted());
        bs.setFreeThrowsMade(e.getFreeThrowsMade());
        return bs;
    }

    /**
     * §3.6 (#024 A) — the crux: assemble a {@link GameResult}, splitting the box
     * scores into home/away buckets. The box_score row has no team_id (#020), so
     * the team is resolved from the game + player_team: a row belongs to the home
     * team iff its playerId is in {@code homePlayerIds} (the home roster fetched
     * once via {@code PlayerTeamRepo.findByTeamId}), else it's away. Every player
     * with a box-score row took the floor for one of the two teams, so this two-way
     * split is exhaustive.
     */
    public GameResult toGameResult(GameEntity game, List<BoxScoreEntity> boxScores,
                                   Set<String> homePlayerIds) {
        GameResult result = new GameResult();
        result.setGame(entityToGame(game));
        result.setHomeBoxScore(new ArrayList<>());
        result.setAwayBoxScore(new ArrayList<>());
        for (BoxScoreEntity e : boxScores) {
            BoxScore bs = entityToBoxScore(e);
            if (homePlayerIds.contains(e.getPlayerId())) {
                result.getHomeBoxScore().add(bs);
            } else {
                result.getAwayBoxScore().add(bs);
            }
        }
        return result;
    }

    private Position positionFromId(String id) {
        for (Position pos : Position.values()) {
            if (pos.id.equals(id)) {
                return pos;
            }
        }
        throw new IllegalArgumentException("Unknown position id: " + id);
    }

    /**
     * Convert the VARCHAR-stored {@code weight} to the integer the API exposes.
     * Seed data is always numeric; a blank/malformed value maps to null rather
     * than throwing, so a stray row can't break a whole roster read.
     */
    private Integer weightToInteger(String weight) {
        if (weight == null || weight.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(weight.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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
