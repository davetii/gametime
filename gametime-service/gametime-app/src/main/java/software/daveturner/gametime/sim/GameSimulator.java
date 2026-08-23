package software.daveturner.gametime.sim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.exception.ResourceNotFoundException;
import software.daveturner.gametime.model.RosterEntry;
import software.daveturner.gametime.model.Team;
import software.daveturner.gametime.repo.*;
import software.daveturner.gametime.service.TeamQueryService;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@Service
@Transactional
public class GameSimulator {

    private final TeamQueryService teamQueryService;
    private final PossessionEngine possessionEngine;
    private final GameRepo gameRepo;
    private final GameEventRepo gameEventRepo;
    private final BoxScoreRepo boxScoreRepo;
    private final SimConfig config;

    public GameSimulator(TeamQueryService teamQueryService, PossessionEngine possessionEngine,
                         GameRepo gameRepo, GameEventRepo gameEventRepo,
                         BoxScoreRepo boxScoreRepo, SimConfig config) {
        this.teamQueryService = teamQueryService;
        this.possessionEngine = possessionEngine;
        this.gameRepo = gameRepo;
        this.gameEventRepo = gameEventRepo;
        this.boxScoreRepo = boxScoreRepo;
        this.config = config;
    }

    public SimResult simulate(String homeTeamId, String awayTeamId, long seed, int possessionsPerPeriod) {
        Team homeTeam = teamQueryService.getTeam(homeTeamId)
                .orElseThrow(ResourceNotFoundException::new);
        Team awayTeam = teamQueryService.getTeam(awayTeamId)
                .orElseThrow(ResourceNotFoundException::new);

        List<PlayerGameState> homeSquad = buildRotation(homeTeam);
        List<PlayerGameState> awaySquad = buildRotation(awayTeam);

        // §3.4/§3.5: bundle each team's rotation (full squad + on-floor five) +
        // coach modifiers into a TeamContext. The RotationState makes the on-floor
        // five dynamic — substitutions mutate it between possessions (§3.5).
        CoachModifiers homeMods = CoachModifiers.from(homeTeam.getCoach(), config);
        CoachModifiers awayMods = CoachModifiers.from(awayTeam.getCoach(), config);
        TeamContext home = new TeamContext(homeTeamId,
                new RotationState(homeSquad, homeMods, config), homeMods);
        TeamContext away = new TeamContext(awayTeamId,
                new RotationState(awaySquad, awayMods, config), awayMods);

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
        game.setSeed(seed);
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
            // §3.10 (#028 D): who committed it — set on FOUL events, null elsewhere.
            event.setCommittingTeamId(e.committingTeamId());
            // §3.18 (#041 A): the counterparty — the stealer on TURNOVER/STOLEN, the
            // blocker on SHOT/BLOCKED_*, the fouled shooter on FOUL/SHOOTING_FOUL.
            // Always on the opposite team from primaryPlayerId; null elsewhere.
            event.setOpponentPlayerId(e.opponentPlayerId());
            gameEventRepo.save(event);
        }

        // §3.5: every player who took the floor gets a box-score row, not just the
        // 5 starters. A player who never checked in (onFloorPossessions == 0) has
        // nothing to reconcile, so they get no row.
        List<PlayerGameState> allPlayers = new ArrayList<>(homeSquad);
        allPlayers.addAll(awaySquad);
        int totalGameMinutes = SimConfig.PERIODS * SimConfig.MINUTES_PER_PERIOD
                + Math.max(0, data.getPeriods() - SimConfig.PERIODS) * SimConfig.OT_MINUTES;
        int homePossessions = totalOnFloorPossessions(homeSquad);
        int awayPossessions = totalOnFloorPossessions(awaySquad);
        for (PlayerGameState p : allPlayers) {
            if (p.getOnFloorPossessions() == 0) {
                continue;
            }
            BoxScoreEntity bs = new BoxScoreEntity();
            bs.setId(UUID.randomUUID().toString());
            bs.setGameId(gameId);
            bs.setPlayerId(p.getPlayerId());
            bs.setPoints(p.getPoints());
            bs.setOffensiveRebounds(p.getOffensiveRebounds());
            bs.setDefensiveRebounds(p.getDefensiveRebounds());
            bs.setAssists(p.getAssists());
            bs.setSteals(p.getSteals());
            bs.setBlocks(p.getBlocks());
            bs.setTurnovers(p.getTurnovers());
            bs.setFouls(p.getFouls());
            // §3.14a (#032 E): the twelfth accumulator, written alongside the other
            // eleven. Separate from fouls — a technical does not feed the six-foul
            // limit (the split is the whole point of #032 E).
            bs.setTechnicalFouls(p.getTechnicalFouls());
            // §3.5 (Decision A): minutes are a possession-share projection. The
            // team is 5-on-the-floor every possession, so team on-floor possessions
            // sum to 5 × (team possessions); a player's minutes are their share of
            // that × the team's total minutes (5 × game minutes). No game clock.
            int teamPossessions = p.getTeamId().equals(homeTeamId)
                    ? homePossessions : awayPossessions;
            int minutes = teamPossessions == 0 ? 0
                    : (int) Math.round(
                            (double) p.getOnFloorPossessions() / teamPossessions
                                    * 5.0 * totalGameMinutes);
            bs.setMinutes(minutes);
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

    /**
     * §3.5: build the full rotation for a team — the 5 STARTERs first, then every
     * other roster entry that carries a {@code rotationOrder} (the bench queue,
     * #014) in ascending order. Entries with a null rotationOrder that aren't
     * starters (e.g. INACTIVE / MINORS) are excluded — they're not in the game-day
     * rotation. The starters-first, bench-by-rotationOrder ordering is what
     * {@link RotationState} uses for sub priority and rested-return.
     */
    List<PlayerGameState> buildRotation(Team team) {
        String teamId = team.getId().getValue();
        List<RosterEntry> starters = new ArrayList<>();
        List<RosterEntry> bench = new ArrayList<>();
        for (RosterEntry e : team.getPlayers()) {
            if (e.getLineupRole() == software.daveturner.gametime.model.LineupRole.STARTER) {
                starters.add(e);
            } else if (e.getRotationOrder() != null) {
                bench.add(e);
            }
        }
        bench.sort(Comparator.comparingInt(RosterEntry::getRotationOrder));

        List<PlayerGameState> rotation = new ArrayList<>();
        for (RosterEntry e : starters) {
            rotation.add(new PlayerGameState(e.getPlayer().getId(), teamId, e, config));
        }
        for (RosterEntry e : bench) {
            rotation.add(new PlayerGameState(e.getPlayer().getId(), teamId, e, config));
        }
        return rotation;
    }

    private int totalOnFloorPossessions(List<PlayerGameState> squad) {
        return squad.stream().mapToInt(PlayerGameState::getOnFloorPossessions).sum();
    }
}
