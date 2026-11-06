package software.daveturner.gametime.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.BoxScoreEntity;
import software.daveturner.gametime.entity.GameEntity;
import software.daveturner.gametime.entity.PlayerEntity;
import software.daveturner.gametime.exception.ResourceBadRequestException;
import software.daveturner.gametime.exception.ResourceNotFoundException;
import software.daveturner.gametime.mapper.EntityMapper;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.repo.PlayerRepo;
import software.daveturner.gametime.repo.TeamRepo;
import software.daveturner.gametime.service.StatsMath.Sums;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Service
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    /** The select list {@link Sums#fromRow} reads, in its field order. */
    static final String SUMS = "count(b), sum(case when b.started = true then 1 else 0 end),"
            + " sum(b.points), sum(b.offensiveRebounds), sum(b.defensiveRebounds), sum(b.assists),"
            + " sum(b.steals), sum(b.blocks), sum(b.turnovers), sum(b.fouls), sum(b.technicalFouls),"
            + " sum(b.minutes), sum(b.fieldGoalsAttempted), sum(b.fieldGoalsMade),"
            + " sum(b.threePointersAttempted), sum(b.threePointersMade),"
            + " sum(b.freeThrowsAttempted), sum(b.freeThrowsMade)";

    private static final String IN_SCOPE =
            " from BoxScoreEntity b join GameEntity g on g.id = b.gameId where g.status = :status";

    private final EntityManager em;
    private final PlayerRepo playerRepo;
    private final TeamRepo teamRepo;
    private final EntityMapper mapper;
    private final StatsConfig.Leaders leaders;
    private final Clock clock;
    private final StatsScope scope = StatsScope.allFinalGames();

    public StatsServiceImpl(EntityManager em, PlayerRepo playerRepo, TeamRepo teamRepo,
                            EntityMapper mapper, StatsConfig config, Clock clock) {
        this.em = em;
        this.playerRepo = playerRepo;
        this.teamRepo = teamRepo;
        this.mapper = mapper;
        this.leaders = config.leaders();
        this.clock = clock;
    }

    // --- player ---

    @Override
    public PlayerStats getPlayerStats(String playerId) {
        if (!playerRepo.existsById(playerId)) {
            throw new ResourceNotFoundException();
        }
        Object[] row = em.createQuery("select " + SUMS + IN_SCOPE + " and b.playerId = :playerId",
                        Object[].class)
                .setParameter("status", scope.status())
                .setParameter("playerId", playerId)
                .getSingleResult();
        Sums s = Sums.fromRow(row, 0);
        PlayerStats stats = new PlayerStats();
        stats.setPlayerId(playerId);
        stats.setGamesPlayed((int) s.games());
        stats.setGamesStarted((int) s.started());
        stats.setTotals(StatsMath.totals(s));
        stats.setPerGame(StatsMath.perGame(s, s.games()));
        stats.setShooting(StatsMath.shooting(s));
        return stats;
    }

    @Override
    public List<GameLogEntry> getPlayerGameLog(String playerId) {
        if (!playerRepo.existsById(playerId)) {
            throw new ResourceNotFoundException();
        }
        List<Object[]> rows = em.createQuery("select b, g" + IN_SCOPE
                        + " and b.playerId = :playerId order by g.playedAt, g.id", Object[].class)
                .setParameter("status", scope.status())
                .setParameter("playerId", playerId)
                .getResultList();
        List<GameLogEntry> log = new ArrayList<>();
        for (Object[] r : rows) {
            BoxScoreEntity b = (BoxScoreEntity) r[0];
            GameEntity g = (GameEntity) r[1];
            boolean home = g.getHomeTeamId().equals(b.getTeamId());
            GameLogEntry e = new GameLogEntry();
            e.setGameId(g.getId());
            e.setPlayedAt(offset(g.getPlayedAt()));
            e.setTeamId(b.getTeamId());
            e.setOpponentTeamId(home ? g.getAwayTeamId() : g.getHomeTeamId());
            e.setHome(home);
            e.setTeamScore(home ? g.getHomeScore() : g.getAwayScore());
            e.setOpponentScore(home ? g.getAwayScore() : g.getHomeScore());
            e.setStarted(b.getStarted());
            e.setBoxScore(mapper.entityToBoxScore(b));
            log.add(e);
        }
        return log;
    }

    // --- team ---

    @Override
    public TeamStats getTeamStats(String teamId) {
        if (!teamRepo.existsById(teamId)) {
            throw new ResourceNotFoundException();
        }
        Sums team = Sums.fromRow(em.createQuery("select " + SUMS + IN_SCOPE + " and b.teamId = :teamId",
                        Object[].class)
                .setParameter("status", scope.status())
                .setParameter("teamId", teamId)
                .getSingleResult(), 0);
        Sums opponents = Sums.fromRow(em.createQuery("select " + SUMS + IN_SCOPE
                        + " and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)"
                        + " and b.teamId <> :teamId", Object[].class)
                .setParameter("status", scope.status())
                .setParameter("teamId", teamId)
                .getSingleResult(), 0);
        Object[] games = em.createQuery("select count(g),"
                        + " sum(case when g.homeTeamId = :teamId then g.homePossessions else g.awayPossessions end),"
                        + " sum(g.periods)"
                        + " from GameEntity g where g.status = :status"
                        + " and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)", Object[].class)
                .setParameter("status", scope.status())
                .setParameter("teamId", teamId)
                .getSingleResult();
        long gamesPlayed = ((Number) games[0]).longValue();
        Long possessions = games[1] == null ? null : ((Number) games[1]).longValue();
        long periods = games[2] == null ? 0 : ((Number) games[2]).longValue();

        TeamStats stats = new TeamStats();
        stats.setTeamId(teamId);
        stats.setGamesPlayed((int) gamesPlayed);
        stats.setPossessions(possessions == null ? null : possessions.intValue());
        stats.setPace(StatsMath.pace(possessions, StatsMath.gameMinutes(gamesPlayed, periods)));
        stats.setOffensiveRating(StatsMath.rating(team.points(), possessions));
        stats.setDefensiveRating(StatsMath.rating(opponents.points(), possessions));
        stats.setNetRating(StatsMath.net(stats.getOffensiveRating(), stats.getDefensiveRating()));
        stats.setTotals(StatsMath.totals(team));
        stats.setPerGame(StatsMath.perGame(team, gamesPlayed));
        stats.setOpponentTotals(StatsMath.totals(opponents));
        stats.setOpponentPerGame(StatsMath.perGame(opponents, gamesPlayed));
        return stats;
    }

    // --- league ---

    private record LeaderRule(LeaderBoard.BasisEnum basis, Function<Sums, Double> value,
                              ToLongFunction<Sums> makes, double makesPerTeamGame) {

        static LeaderRule perGame(ToLongFunction<Sums> total) {
            return new LeaderRule(LeaderBoard.BasisEnum.PER_GAME,
                    s -> (double) total.applyAsLong(s) / s.games(), null, 0);
        }

        static LeaderRule percentage(Function<Sums, Double> pct, ToLongFunction<Sums> makes, double perGame) {
            return new LeaderRule(LeaderBoard.BasisEnum.PERCENTAGE, pct, makes, perGame);
        }
    }

    private LeaderRule rule(LeaderCategory category) {
        return switch (category) {
            case POINTS -> LeaderRule.perGame(Sums::points);
            case REBOUNDS -> LeaderRule.perGame(Sums::rebounds);
            case OFFENSIVE_REBOUNDS -> LeaderRule.perGame(Sums::offensiveRebounds);
            case DEFENSIVE_REBOUNDS -> LeaderRule.perGame(Sums::defensiveRebounds);
            case ASSISTS -> LeaderRule.perGame(Sums::assists);
            case STEALS -> LeaderRule.perGame(Sums::steals);
            case BLOCKS -> LeaderRule.perGame(Sums::blocks);
            case TURNOVERS -> LeaderRule.perGame(Sums::turnovers);
            case MINUTES -> LeaderRule.perGame(Sums::minutes);
            case FOULS -> LeaderRule.perGame(Sums::fouls);
            case TECHNICAL_FOULS -> new LeaderRule(LeaderBoard.BasisEnum.TOTAL,
                    s -> (double) s.technicalFouls(), null, 0);
            case FIELD_GOAL_PCT -> LeaderRule.percentage(StatsMath::fieldGoalPct,
                    Sums::fieldGoalsMade, leaders.minFgmPerGame());
            case THREE_POINT_PCT -> LeaderRule.percentage(StatsMath::threePointPct,
                    Sums::threePointersMade, leaders.min3pmPerGame());
            case FREE_THROW_PCT -> LeaderRule.percentage(StatsMath::freeThrowPct,
                    Sums::freeThrowsMade, leaders.minFtmPerGame());
            case TRUE_SHOOTING_PCT -> LeaderRule.percentage(StatsMath::trueShootingPct,
                    Sums::fieldGoalsMade, leaders.minFgmPerGame());
            case EFFECTIVE_FIELD_GOAL_PCT -> LeaderRule.percentage(StatsMath::effectiveFieldGoalPct,
                    Sums::fieldGoalsMade, leaders.minFgmPerGame());
        };
    }

    private record Ranked(String playerId, Sums sums, double value) {
    }

    @Override
    public LeaderBoard getLeaders(LeaderCategory category, Integer limit) {
        int n = limit(limit);
        LeaderRule rule = rule(category);
        long teamGames = teamGames();

        List<Ranked> qualified = new ArrayList<>();
        for (Object[] row : em.createQuery("select b.playerId, " + SUMS + IN_SCOPE + " group by b.playerId",
                        Object[].class)
                .setParameter("status", scope.status())
                .getResultList()) {
            Sums s = Sums.fromRow(row, 1);
            if (!qualifies(rule, s, teamGames)) {
                continue;
            }
            Double value = rule.value().apply(s);
            if (value != null) {
                qualified.add(new Ranked((String) row[0], s, value));
            }
        }
        qualified.sort(Comparator.comparingDouble(Ranked::value).reversed()
                .thenComparing(Comparator.comparingLong((Ranked r) -> r.sums().games()).reversed())
                .thenComparing(Ranked::playerId));
        List<Ranked> top = qualified.subList(0, Math.min(n, qualified.size()));

        List<String> ids = top.stream().map(Ranked::playerId).toList();
        Map<String, String> names = names(ids);
        Map<String, String> teams = latestTeams(ids);
        List<LeaderEntry> entries = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Ranked r = top.get(i);
            LeaderEntry e = new LeaderEntry();
            e.setRank(i > 0 && top.get(i - 1).value() == r.value() ? entries.get(i - 1).getRank() : i + 1);
            e.setPlayerId(r.playerId());
            e.setPlayerName(names.get(r.playerId()));
            e.setTeamId(teams.get(r.playerId()));
            e.setGamesPlayed((int) r.sums().games());
            e.setValue(r.value());
            e.setTotals(StatsMath.totals(r.sums()));
            entries.add(e);
        }
        LeaderBoard board = new LeaderBoard();
        board.setCategory(category);
        board.setBasis(rule.basis());
        board.setEntries(entries);
        return board;
    }

    private boolean qualifies(LeaderRule rule, Sums s, long teamGames) {
        // Tolerance so 0.70 × 10 team games admits exactly 7 games.
        double epsilon = 1e-9;
        return switch (rule.basis()) {
            case TOTAL -> true;
            case PER_GAME -> s.games() >= leaders.minGamesFraction() * teamGames - epsilon;
            case PERCENTAGE -> rule.makes().applyAsLong(s) >= rule.makesPerTeamGame() * teamGames - epsilon;
        };
    }

    /** The most FINAL games any one team has in scope — the qualifiers' base (#045 J). */
    long teamGames() {
        return em.createQuery("select count(distinct b.gameId)" + IN_SCOPE
                        + " and b.teamId is not null group by b.teamId", Long.class)
                .setParameter("status", scope.status())
                .getResultList().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    @Override
    public GameHighs getGameHighs(GameHighCategory category, Integer limit) {
        int n = limit(limit);
        String expr = switch (category) {
            case POINTS -> "b.points";
            case REBOUNDS -> "(b.offensiveRebounds + b.defensiveRebounds)";
            case OFFENSIVE_REBOUNDS -> "b.offensiveRebounds";
            case DEFENSIVE_REBOUNDS -> "b.defensiveRebounds";
            case ASSISTS -> "b.assists";
            case STEALS -> "b.steals";
            case BLOCKS -> "b.blocks";
            case TURNOVERS -> "b.turnovers";
            case MINUTES -> "b.minutes";
            case FOULS -> "b.fouls";
            case TECHNICAL_FOULS -> "b.technicalFouls";
            case FIELD_GOALS_MADE -> "b.fieldGoalsMade";
            case THREE_POINTERS_MADE -> "b.threePointersMade";
            case FREE_THROWS_MADE -> "b.freeThrowsMade";
        };
        List<Object[]> rows = em.createQuery("select b, g, " + expr + IN_SCOPE
                        + " and " + expr + " is not null"
                        + " order by " + expr + " desc, g.playedAt, g.id, b.playerId", Object[].class)
                .setParameter("status", scope.status())
                .setMaxResults(n)
                .getResultList();

        Map<String, String> names = names(rows.stream()
                .map(r -> ((BoxScoreEntity) r[0]).getPlayerId()).distinct().toList());
        List<GameHighEntry> entries = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            BoxScoreEntity b = (BoxScoreEntity) rows.get(i)[0];
            GameEntity g = (GameEntity) rows.get(i)[1];
            int value = ((Number) rows.get(i)[2]).intValue();
            GameHighEntry e = new GameHighEntry();
            e.setRank(i > 0 && entries.get(i - 1).getValue() == value ? entries.get(i - 1).getRank() : i + 1);
            e.setPlayerId(b.getPlayerId());
            e.setPlayerName(names.get(b.getPlayerId()));
            e.setTeamId(b.getTeamId());
            e.setOpponentTeamId(g.getHomeTeamId().equals(b.getTeamId()) ? g.getAwayTeamId() : g.getHomeTeamId());
            e.setGameId(g.getId());
            e.setPlayedAt(offset(g.getPlayedAt()));
            e.setValue(value);
            entries.add(e);
        }
        GameHighs highs = new GameHighs();
        highs.setCategory(category);
        highs.setEntries(entries);
        return highs;
    }

    // --- helpers ---

    private int limit(Integer limit) {
        int n = limit == null ? leaders.defaultLimit() : limit;
        if (n < 1 || n > leaders.maxLimit()) {
            throw new ResourceBadRequestException("limit must be between 1 and " + leaders.maxLimit());
        }
        return n;
    }

    private Map<String, String> names(List<String> playerIds) {
        Map<String, String> names = new HashMap<>();
        for (PlayerEntity p : playerRepo.findAllById(playerIds)) {
            names.put(p.getId(), p.getFirstName() + " " + p.getLastName());
        }
        return names;
    }

    /** Each player's team in his most recent game in scope. */
    private Map<String, String> latestTeams(List<String> playerIds) {
        Map<String, String> teams = new HashMap<>();
        if (playerIds.isEmpty()) {
            return teams;
        }
        for (Object[] r : em.createQuery("select b.playerId, b.teamId" + IN_SCOPE
                        + " and b.playerId in :ids order by g.playedAt desc, g.id desc", Object[].class)
                .setParameter("status", scope.status())
                .setParameter("ids", playerIds)
                .getResultList()) {
            teams.putIfAbsent((String) r[0], (String) r[1]);
        }
        return teams;
    }

    private OffsetDateTime offset(LocalDateTime playedAt) {
        return playedAt == null ? null : playedAt.atZone(clock.getZone()).toOffsetDateTime();
    }
}
