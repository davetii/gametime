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
        List<String> playerIds = playerTeamRepo.findByTeamId(entity.getId()).stream()
                .map(PlayerTeamEntity::getPlayerId)
                .collect(Collectors.toList());
        List<PlayerEntity> roster = playerIds.isEmpty()
                ? List.of()
                : playerRepo.findByIdIn(playerIds);
        return entityMapper.entityToTeam(entity, roster);
    }
}
