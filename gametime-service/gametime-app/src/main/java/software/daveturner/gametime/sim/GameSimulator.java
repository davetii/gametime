package software.daveturner.gametime.sim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.exception.ResourceNotFoundException;
import software.daveturner.gametime.model.RosterEntry;
import software.daveturner.gametime.model.Team;
import software.daveturner.gametime.repo.*;
import software.daveturner.gametime.service.TeamQueryService;

import java.time.Clock;
import java.time.LocalDateTime;
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
    private final Clock clock;

    public GameSimulator(TeamQueryService teamQueryService, PossessionEngine possessionEngine,
                         GameRepo gameRepo, GameEventRepo gameEventRepo,
                         BoxScoreRepo boxScoreRepo, SimConfig config, Clock clock) {
        this.teamQueryService = teamQueryService;
        this.possessionEngine = possessionEngine;
        this.gameRepo = gameRepo;
        this.gameEventRepo = gameEventRepo;
        this.boxScoreRepo = boxScoreRepo;
        this.config = config;
        this.clock = clock;
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

        // #045 A, B: box score and score come from the event log, checked against the counters.
        BoxScoreSummarizer.Summary summary = BoxScoreSummarizer.summarize(data.getEvents());
        verifyAgainstCounters(summary, homeSquad, awaySquad, data);
        int homeScore = summary.points(playerIds(homeSquad));
        int awayScore = summary.points(playerIds(awaySquad));

        String gameId = UUID.randomUUID().toString();

        GameEntity game = new GameEntity();
        game.setId(gameId);
        game.setHomeTeamId(homeTeamId);
        game.setAwayTeamId(awayTeamId);
        game.setStatus(GameStatus.FINAL);
        game.setHomeScore(homeScore);
        game.setAwayScore(awayScore);
        game.setPeriods(data.getPeriods());
        game.setSeed(seed);
        game.setHomePossessions(data.getHomePossessions());
        game.setAwayPossessions(data.getAwayPossessions());
        game.setPlayedAt(LocalDateTime.now(clock));
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
        // 5 starters.
        //
        // §3.20 — ⚠ THE PREDICATE IS tookFloor(), NOT onFloorPossessions == 0, AND
        // THE DIFFERENCE IS A REAL BUG THIS FIXES. The old filter read "never
        // checked in" off the minutes counter, but RotationState.advancePossession
        // drains for the five on the floor at the TOP of the call and substitutes
        // AFTERWARDS — so a substituted-in player could take the floor, SCORE, and
        // still hold a count of 0, at which point his row (and his points) were
        // silently dropped. Measured at ~1 game in 1200: box score 237 against an
        // event log and final score that both read 240.
        // ⚠ Caught ONLY by §3.19's points identity; no test failed. Today the
        // damage is ~0.001 on FGA, but Phase 4's leaderboards read this table.
        // ⚠ Do NOT "simplify" this back to the possession count: the two answer
        // different questions, and onFloorPossessions must stay the pure minutes
        // denominator (a row with points and 0 minutes is the other wrong answer).
        List<PlayerGameState> allPlayers = new ArrayList<>(homeSquad);
        allPlayers.addAll(awaySquad);
        int totalGameMinutes = SimConfig.PERIODS * SimConfig.MINUTES_PER_PERIOD
                + Math.max(0, data.getPeriods() - SimConfig.PERIODS) * SimConfig.OT_MINUTES;
        int homeOnFloorPossessions = totalOnFloorPossessions(homeSquad);
        int awayOnFloorPossessions = totalOnFloorPossessions(awaySquad);
        for (PlayerGameState p : allPlayers) {
            if (!p.tookFloor()) {
                continue;
            }
            BoxScoreSummarizer.Line line = summary.lineFor(p.getPlayerId());
            BoxScoreEntity bs = new BoxScoreEntity();
            bs.setId(UUID.randomUUID().toString());
            bs.setGameId(gameId);
            bs.setPlayerId(p.getPlayerId());
            bs.setTeamId(p.getTeamId());
            bs.setStarted(p.startedGame());
            bs.setPoints(line.points());
            bs.setOffensiveRebounds(line.offensiveRebounds());
            bs.setDefensiveRebounds(line.defensiveRebounds());
            bs.setAssists(line.assists());
            bs.setSteals(line.steals());
            bs.setBlocks(line.blocks());
            bs.setTurnovers(line.turnovers());
            bs.setFouls(line.fouls());
            bs.setTechnicalFouls(line.technicalFouls());
            // §3.5 (Decision A): minutes are a possession-share projection. The
            // team is 5-on-the-floor every possession, so team on-floor possessions
            // sum to 5 × (team possessions); a player's minutes are their share of
            // that × the team's total minutes (5 × game minutes). No game clock.
            int teamPossessions = p.getTeamId().equals(homeTeamId)
                    ? homeOnFloorPossessions : awayOnFloorPossessions;
            int minutes = teamPossessions == 0 ? 0
                    : (int) Math.round(
                            (double) p.getOnFloorPossessions() / teamPossessions
                                    * 5.0 * totalGameMinutes);
            bs.setMinutes(minutes);
            bs.setFieldGoalsAttempted(line.fieldGoalsAttempted());
            bs.setFieldGoalsMade(line.fieldGoalsMade());
            bs.setThreePointersAttempted(line.threePointersAttempted());
            bs.setThreePointersMade(line.threePointersMade());
            bs.setFreeThrowsAttempted(line.freeThrowsAttempted());
            bs.setFreeThrowsMade(line.freeThrowsMade());
            boxScoreRepo.save(bs);
        }

        return new SimResult(gameId, homeTeamId, awayTeamId,
                homeScore, awayScore,
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

    /** #045 B: throws if the event log and the engine's counters disagree anywhere. */
    static void verifyAgainstCounters(BoxScoreSummarizer.Summary summary,
                                      List<PlayerGameState> homeSquad,
                                      List<PlayerGameState> awaySquad,
                                      GameData data) {
        Map<String, PlayerGameState> byId = new HashMap<>();
        homeSquad.forEach(p -> byId.put(p.getPlayerId(), p));
        awaySquad.forEach(p -> byId.put(p.getPlayerId(), p));

        for (String creditedId : summary.lines().keySet()) {
            PlayerGameState p = byId.get(creditedId);
            if (p == null || !p.tookFloor()) {
                throw new IllegalStateException("Box-score guard: an event credits player "
                        + creditedId + ", who never took the floor and has no line");
            }
        }
        for (PlayerGameState p : byId.values()) {
            BoxScoreSummarizer.Line line = summary.lineFor(p.getPlayerId());
            for (BoxScoreSummarizer.Column c : BoxScoreSummarizer.Column.values()) {
                int derived = c.derived(line);
                int counted = c.counted(p);
                if (derived != counted) {
                    throw new IllegalStateException("Box-score guard: player " + p.getPlayerId()
                            + " " + c.columnName() + " derived " + derived
                            + " from the event log, counted " + counted);
                }
            }
        }
        checkTeamPoints("home", data.getHomeTeamId(),
                summary.points(playerIds(homeSquad)), data.getHomeScore());
        checkTeamPoints("away", data.getAwayTeamId(),
                summary.points(playerIds(awaySquad)), data.getAwayScore());
    }

    private static void checkTeamPoints(String side, String teamId, int derived, int counted) {
        if (derived != counted) {
            throw new IllegalStateException("Box-score guard: " + side + " team " + teamId
                    + " points derived " + derived + " from the event log, scored " + counted);
        }
    }

    private static List<String> playerIds(List<PlayerGameState> squad) {
        return squad.stream().map(PlayerGameState::getPlayerId).toList();
    }

    private int totalOnFloorPossessions(List<PlayerGameState> squad) {
        return squad.stream().mapToInt(PlayerGameState::getOnFloorPossessions).sum();
    }
}
