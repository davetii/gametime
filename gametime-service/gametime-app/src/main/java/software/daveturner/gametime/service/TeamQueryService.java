package software.daveturner.gametime.service;

import org.springframework.stereotype.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.mapper.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.repo.*;

import java.util.*;
import java.util.stream.*;

/**
 * Loads a team together with its current roster (player_team assignments +
 * players + lineup slots), assembled via {@link EntityMapper#entityToTeam}.
 *
 * <p>Extracted as a focused collaborator that both {@link GametimeServiceImp}
 * (the top-level application service) and {@code sim.GameSimulator} (the game
 * engine) depend on. Previously the engine reached into the whole
 * {@code GametimeService} just for {@code getTeam}, and the service depended on
 * the engine for {@code simulateGame} — a Spring constructor cycle. Depending on
 * this small read-only service instead points the dependency arrow one way (a
 * low-level engine no longer depends on the god-service) and gives the
 * roster-assembly logic a single home.
 */
@Component
public class TeamQueryService {

    private final TeamRepo teamRepo;
    private final PlayerTeamRepo playerTeamRepo;
    private final PlayerRepo playerRepo;
    private final EntityMapper entityMapper;

    public TeamQueryService(TeamRepo teamRepo, PlayerTeamRepo playerTeamRepo,
                            PlayerRepo playerRepo, EntityMapper entityMapper) {
        this.teamRepo = teamRepo;
        this.playerTeamRepo = playerTeamRepo;
        this.playerRepo = playerRepo;
        this.entityMapper = entityMapper;
    }

    /** A team with its current roster, or empty if the id is unknown. */
    public Optional<Team> getTeam(String teamId) {
        return teamRepo.findById(teamId).map(this::toTeamWithRoster);
    }

    /** Assemble a team entity's roster from player_team + players and map it. */
    public Team toTeamWithRoster(TeamEntity entity) {
        List<PlayerTeamEntity> assignments = playerTeamRepo.findByTeamId(entity.getId());
        Map<String, PlayerEntity> players = assignments.isEmpty()
                ? Map.of()
                : playerRepo.findByIdIn(assignments.stream()
                        .map(PlayerTeamEntity::getPlayerId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(PlayerEntity::getId, p -> p));
        return entityMapper.entityToTeam(entity, assignments, players);
    }
}
