package software.daveturner.gametime.sim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.exception.ResourceNotFoundException;
import software.daveturner.gametime.model.RosterEntry;
import software.daveturner.gametime.model.Team;
import software.daveturner.gametime.repo.*;
import software.daveturner.gametime.service.GametimeService;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;

@Service
@Transactional
public class GameSimulator {

    private final GametimeService gametimeService;
    private final PossessionEngine possessionEngine;
    private final GameRepo gameRepo;
    private final GameEventRepo gameEventRepo;
    private final BoxScoreRepo boxScoreRepo;
    private final SimConfig config;

    public GameSimulator(GametimeService gametimeService, PossessionEngine possessionEngine,
                         GameRepo gameRepo, GameEventRepo gameEventRepo,
                         BoxScoreRepo boxScoreRepo, SimConfig config) {
        this.gametimeService = gametimeService;
        this.possessionEngine = possessionEngine;
        this.gameRepo = gameRepo;
        this.gameEventRepo = gameEventRepo;
        this.boxScoreRepo = boxScoreRepo;
        this.config = config;
    }

    public SimResult simulate(String homeTeamId, String awayTeamId, long seed, int possessionsPerPeriod) {
        Team homeTeam = gametimeService.getTeam(homeTeamId)
                .orElseThrow(ResourceNotFoundException::new);
        Team awayTeam = gametimeService.getTeam(awayTeamId)
                .orElseThrow(ResourceNotFoundException::new);

        List<PlayerGameState> homePlayers = buildStarters(homeTeam);
        List<PlayerGameState> awayPlayers = buildStarters(awayTeam);

        // §3.4: bundle each team's players + coach modifiers into a TeamContext.
        TeamContext home = new TeamContext(homeTeamId, homePlayers,
                CoachModifiers.from(homeTeam.getCoach(), config));
        TeamContext away = new TeamContext(awayTeamId, awayPlayers,
                CoachModifiers.from(awayTeam.getCoach(), config));

        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom")
                .create(seed);

        GameData data = possessionEngine.simulate(home, away, possessionsPerPeriod, rng);

        String gameId = UUID.randomUUID().toString();

        GameEntity game = new GameEntity();
        game.setId(gameId);
        game.setHomeTeamId(homeTeamId);
        game.setAwayTeamId(awayTeamId);
        game.setStatus(GameStatus.FINAL);
        game.setHomeScore(data.getHomeScore());
        game.setAwayScore(data.getAwayScore());
        game.setPeriods(data.getPeriods());
        gameRepo.save(game);

        for (GameData.EventRecord e : data.getEvents()) {
            GameEventEntity event = new GameEventEntity();
            event.setId(UUID.randomUUID().toString());
            event.setGameId(gameId);
            event.setSequence(e.sequence());
            event.setPeriod(e.period());
            event.setOffenseTeamId(e.offTeamId());
            event.setDefenseTeamId(e.defTeamId());
            event.setPlayType(e.playType());
            event.setOutcome(e.outcome());
            event.setPrimaryPlayerId(e.primaryPlayerId());
            event.setAssistPlayerId(e.assistPlayerId());
            gameEventRepo.save(event);
        }

        List<PlayerGameState> allPlayers = new ArrayList<>(homePlayers);
        allPlayers.addAll(awayPlayers);
        for (PlayerGameState p : allPlayers) {
            BoxScoreEntity bs = new BoxScoreEntity();
            bs.setId(UUID.randomUUID().toString());
            bs.setGameId(gameId);
            bs.setPlayerId(p.getPlayerId());
            bs.setPoints(p.getPoints());
            bs.setOffensiveRebounds(p.getOffensiveRebounds());
            bs.setDefensiveRebounds(p.getDefensiveRebounds());
            bs.setAssists(p.getAssists());
            bs.setSteals(p.getSteals());
            bs.setBlocks(0);
            bs.setTurnovers(p.getTurnovers());
            bs.setFouls(p.getFouls());
            bs.setMinutes(0);
            bs.setFieldGoalsAttempted(p.getFieldGoalsAttempted());
            bs.setFieldGoalsMade(p.getFieldGoalsMade());
            bs.setThreePointersAttempted(p.getThreePointersAttempted());
            bs.setThreePointersMade(p.getThreePointersMade());
            bs.setFreeThrowsAttempted(p.getFreeThrowsAttempted());
            bs.setFreeThrowsMade(p.getFreeThrowsMade());
            boxScoreRepo.save(bs);
        }

        return new SimResult(gameId, homeTeamId, awayTeamId,
                data.getHomeScore(), data.getAwayScore(),
                data.getPeriods(), data.getEvents().size());
    }

    List<PlayerGameState> buildStarters(Team team) {
        return team.getPlayers().stream()
                .filter(e -> e.getLineupRole() == software.daveturner.gametime.model.LineupRole.STARTER)
                .map(e -> new PlayerGameState(e.getPlayer().getId(), team.getId().getValue(), e))
                .collect(Collectors.toList());
    }
}
