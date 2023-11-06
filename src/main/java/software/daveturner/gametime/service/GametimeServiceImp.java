package software.daveturner.gametime.service;

import org.springframework.stereotype.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.mapper.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.repo.*;

import java.util.*;
import java.util.stream.*;

@Service
public class GametimeServiceImp implements GametimeService {

    private final TeamRepo teamRepo;
    private final PlayerRepo playerRepo;

    private final EntityMapper entityMapper;

    Comparator<Team> byConfById
            = Comparator.comparing(Team::getConference)
            .thenComparing(t -> t.getId().toString());
    public GametimeServiceImp(TeamRepo teamRepo, PlayerRepo playerRepo, EntityMapper entityMapper) {
        this.teamRepo = teamRepo;
        this.playerRepo = playerRepo;
        this.entityMapper = entityMapper;
    }

    @Override
    public List<Team> getLeague() {
        List<TeamEntity> teamEntities = StreamSupport
                .stream(teamRepo.findAll().spliterator(), false)
                .collect(Collectors.toList());
        List<Team> teams = entityMapper.mapLeague(teamEntities);
        teams.sort(byConfById);
        return teams;
    }

    @Override
    public Optional<Player> getPlayer(String playerId) {
        Optional<PlayerEntity> entity = playerRepo.findById(playerId);
        Player player = entityMapper.mapEntityToPlayer(entity.get());
        return Optional.of(player);
    }
}
