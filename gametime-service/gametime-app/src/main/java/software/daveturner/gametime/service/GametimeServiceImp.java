package software.daveturner.gametime.service;

import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.exception.*;
import software.daveturner.gametime.mapper.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.repo.*;

import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
@Transactional
public class GametimeServiceImp implements GametimeService {

    private final TeamRepo teamRepo;
    private final PlayerRepo playerRepo;
    private final PlayerTeamRepo playerTeamRepo;
    private final PlayerTeamHistRepo playerTeamHistRepo;

    private final EntityMapper entityMapper;

    Comparator<Team> byConfById
            = Comparator.comparing(Team::getConference)
            .thenComparing(t -> t.getId().toString());
    public GametimeServiceImp(TeamRepo teamRepo, PlayerRepo playerRepo,
                              PlayerTeamRepo playerTeamRepo, PlayerTeamHistRepo playerTeamHistRepo,
                              EntityMapper entityMapper) {
        this.teamRepo = teamRepo;
        this.playerRepo = playerRepo;
        this.playerTeamRepo = playerTeamRepo;
        this.playerTeamHistRepo = playerTeamHistRepo;
        this.entityMapper = entityMapper;
    }

    @Override
    public List<Team> getLeague() {
        List<Team> teams = StreamSupport
                .stream(teamRepo.findAll().spliterator(), false)
                .map(this::toTeamWithRoster)
                .collect(Collectors.toList());
        teams.sort(byConfById);
        return teams;
    }

    @Override
    public Optional<Player> getPlayer(String playerId) {
        return playerRepo.findById(playerId).map(this::toPlayerWithTeam);
    }

    @Override
    public Optional<Team> getTeam(String teamId) {
        return teamRepo.findById(teamId).map(this::toTeamWithRoster);
    }

    @Override
    public Player createPlayer(Player player) {
        if (player.getId() == null || player.getId().isBlank()) {
            player.setId(UUID.randomUUID().toString());
        }
        PlayerEntity saved = playerRepo.save(entityMapper.mapPlayerToEntity(player));
        // a freshly created player is a free agent — no player_team row
        return toPlayerWithTeam(saved);
    }

    @Override
    public Player updatePlayer(Player player) {
        if (player.getId() == null || !playerRepo.existsById(player.getId())) {
            throw new ResourceNotFoundException();
        }
        // the player→team link lives in player_team, untouched by attribute updates
        PlayerEntity saved = playerRepo.save(entityMapper.mapPlayerToEntity(player));
        return toPlayerWithTeam(saved);
    }

    @Override
    public void addPlayerToTeam(String teamId, String playerId) {
        if (!teamRepo.existsById(teamId)) {
            throw new ResourceNotFoundException();
        }
        if (!playerRepo.existsById(playerId)) {
            throw new ResourceNotFoundException();
        }
        if (playerTeamRepo.existsById(playerId)) {
            throw new ResourceConflictException("Player already on a team");
        }
        assign(playerId, teamId, TransactionType.SIGN);
    }

    @Override
    public List<PlayerTransaction> getPlayerHistory(String playerId) {
        if (!playerRepo.existsById(playerId)) {
            throw new ResourceNotFoundException();
        }
        return playerTeamHistRepo.findByPlayerIdOrderByTransactionDateDesc(playerId).stream()
                .map(entityMapper::histToTransaction)
                .collect(Collectors.toList());
    }

    @Override
    public Team setLineup(String teamId, LineupRequest request) {
        TeamEntity team = teamRepo.findById(teamId)
                .orElseThrow(ResourceNotFoundException::new);
        List<LineupEntry> entries = request == null ? null : request.getEntries();
        if (entries == null || entries.isEmpty()) {
            throw new ResourceBadRequestException("Lineup is empty");
        }

        // current assignments for this team, indexed by player
        Map<String, PlayerTeamEntity> current = playerTeamRepo.findByTeamId(teamId).stream()
                .collect(Collectors.toMap(PlayerTeamEntity::getPlayerId, pt -> pt));

        Set<String> seen = new HashSet<>();
        Set<Integer> rotationOrders = new HashSet<>();
        int starters = 0;
        for (LineupEntry e : entries) {
            String playerId = e.getPlayerId();
            if (playerId == null || !seen.add(playerId)) {
                throw new ResourceBadRequestException("Duplicate or missing player in lineup");
            }
            if (!current.containsKey(playerId)) {
                throw new ResourceNotFoundException(); // player not on this team
            }
            if (e.getLineupRole() == null) {
                throw new ResourceBadRequestException("Missing lineup role");
            }
            software.daveturner.gametime.entity.LineupRole role = toEntityRole(e.getLineupRole());
            if (role == software.daveturner.gametime.entity.LineupRole.STARTER) {
                starters++;
                // starters are an unordered set of 5 — they carry no rotation order
                if (e.getRotationOrder() != null) {
                    throw new ResourceBadRequestException("Starters must not have a rotation order");
                }
            }
            // rotationOrder is the bench substitution queue; when present it must be
            // unique across the whole lineup (first off the bench = 1, then 2, 3…).
            if (e.getRotationOrder() != null && !rotationOrders.add(e.getRotationOrder())) {
                throw new ResourceBadRequestException("Duplicate rotation order");
            }
        }
        if (starters != 5) {
            throw new ResourceBadRequestException("Lineup must have exactly 5 starters");
        }

        // all valid — apply
        for (LineupEntry e : entries) {
            PlayerTeamEntity pt = current.get(e.getPlayerId());
            pt.setLineupRole(toEntityRole(e.getLineupRole()));
            pt.setRotationOrder(e.getRotationOrder());
            playerTeamRepo.save(pt);
        }
        return toTeamWithRoster(team);
    }

    @Override
    public void removePlayerFromTeam(String teamId, String playerId) {
        PlayerTeamEntity assignment = playerTeamRepo.findById(playerId)
                .filter(pt -> pt.getTeamId().equals(teamId))
                .orElseThrow(ResourceNotFoundException::new);
        playerTeamRepo.delete(assignment);

        PlayerTeamHistEntity hist = new PlayerTeamHistEntity();
        hist.setId(UUID.randomUUID().toString());
        hist.setPlayerId(playerId);
        hist.setTeamId(teamId);
        hist.setTransactionType(TransactionType.RELEASE);
        hist.setTransactionDate(LocalDateTime.now());
        playerTeamHistRepo.save(hist);
    }

    /** Convert the generated API LineupRole model enum to the entity enum. */
    private software.daveturner.gametime.entity.LineupRole toEntityRole(
            software.daveturner.gametime.model.LineupRole role) {
        return software.daveturner.gametime.entity.LineupRole.valueOf(role.getValue());
    }

    /** Set the current assignment and append a history row. */
    private void assign(String playerId, String teamId, TransactionType type) {
        LocalDateTime now = LocalDateTime.now();

        PlayerTeamEntity current = new PlayerTeamEntity();
        current.setPlayerId(playerId);
        current.setTeamId(teamId);
        current.setTransactionType(type);
        current.setAssignedDate(now);
        playerTeamRepo.save(current);

        PlayerTeamHistEntity hist = new PlayerTeamHistEntity();
        hist.setId(UUID.randomUUID().toString());
        hist.setPlayerId(playerId);
        hist.setTeamId(teamId);
        hist.setTransactionType(type);
        hist.setTransactionDate(now);
        playerTeamHistRepo.save(hist);
    }

    private Player toPlayerWithTeam(PlayerEntity entity) {
        Player player = entityMapper.mapEntityToPlayer(entity);
        playerTeamRepo.findById(entity.getId())
                .ifPresent(pt -> player.setCurrentTeamId(pt.getTeamId()));
        return player;
    }

    private Team toTeamWithRoster(TeamEntity entity) {
        List<PlayerTeamEntity> assignments = playerTeamRepo.findByTeamId(entity.getId());
        Map<String, PlayerEntity> players = assignments.isEmpty()
                ? Map.of()
                : playerRepo.findByIdIn(assignments.stream()
                        .map(PlayerTeamEntity::getPlayerId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(PlayerEntity::getId, p -> p));
        return entityMapper.entityToTeam(entity, assignments, players);
    }
}
