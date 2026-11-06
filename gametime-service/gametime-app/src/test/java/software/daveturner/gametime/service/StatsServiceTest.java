package software.daveturner.gametime.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.exception.ResourceBadRequestException;
import software.daveturner.gametime.exception.ResourceNotFoundException;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.repo.BoxScoreRepo;
import software.daveturner.gametime.repo.GameRepo;
import software.daveturner.gametime.repo.PlayerTeamRepo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A hand-built league on teams no other test touches (SEA, KC, STL, MIN). Values are
 * extreme so stray committed games from other test classes cannot outrank them.
 * SEA and KC play 30 games, so the qualifier needs 21 games (70%) or 109.8 FGM.
 */
@SpringBootTest
@Transactional
class StatsServiceTest {

    private static final LocalDateTime DAY0 = LocalDateTime.of(2030, 1, 1, 19, 0);

    @Autowired
    StatsService stats;
    @Autowired
    GameRepo gameRepo;
    @Autowired
    BoxScoreRepo boxScoreRepo;
    @Autowired
    PlayerTeamRepo playerTeamRepo;

    private String a1, a2, a3, b1, b2, t, idle;

    @BeforeEach
    void seed() {
        List<String> sea = roster("SEA");
        List<String> kc = roster("KC");
        a1 = sea.get(0);
        a2 = sea.get(1);
        a3 = sea.get(2);
        b1 = kc.get(0);
        b2 = kc.get(1);
        t = kc.get(2);
        idle = roster("OKL").get(0);

        for (int i = 0; i < 30; i++) {
            boolean seaHome = i % 2 == 0;
            String g = game(seaHome ? "SEA" : "KC", seaHome ? "KC" : "SEA", i, seaHome ? 400 : 12, seaHome ? 12 : 400);
            // a1: every game. a2: 21 games (exactly 70%). a3: 20 games (one short).
            row(g, a1, "SEA", true, 200, 80, 100, 10, 20, 30, 40, 0);
            if (i < 21) {
                row(g, a2, "SEA", true, 200, 80, 100, 10, 20, 30, 40, 0);
            }
            if (i < 20) {
                row(g, a3, "SEA", false, 300, 81, 100, 0, 0, 0, 0, 0);
            }
            row(g, b1, "KC", true, 10, 3, 3, 0, 0, 4, 4, 2);
            if (i == 0) {
                row(g, b2, "KC", false, 0, 0, 0, 0, 0, 0, 0, 70);
            }
            if (i < 2) {
                row(g, t, "KC", false, 2, 1, 2, 0, 0, 0, 0, 0);
            }
        }
        // t is traded to STL and plays three more games, the last ones in the league.
        for (int i = 0; i < 3; i++) {
            String g = game("STL", "MIN", 100 + i, 5, 0);
            row(g, t, "STL", true, 5, 2, 4, 1, 2, 0, 0, i == 2 ? 100 : 0);
        }
    }

    // --- player ---

    @Test
    void playerStatsSumTotalsAverageByGamesAndComputePercentagesFromTotals() {
        PlayerStats s = stats.getPlayerStats(a2);
        assertEquals(21, s.getGamesPlayed());
        assertEquals(21, s.getGamesStarted());
        assertEquals(4200, s.getTotals().getPoints());
        assertEquals(200.0, s.getPerGame().getPoints());
        assertEquals(0.8, s.getShooting().getFieldGoalPct(), 1e-12);
        assertEquals(0.5, s.getShooting().getThreePointPct(), 1e-12);
        assertEquals(0.75, s.getShooting().getFreeThrowPct(), 1e-12);
        assertEquals(200.0 / (2 * (100 + 0.44 * 40)), s.getShooting().getTrueShootingPct(), 1e-12);
        assertEquals((80 + 0.5 * 10) / 100.0, s.getShooting().getEffectiveFieldGoalPct(), 1e-12);
    }

    @Test
    void aTradedPlayersTotalsSpanBothTeams() {
        PlayerStats s = stats.getPlayerStats(t);
        assertEquals(5, s.getGamesPlayed());
        assertEquals(2 * 2 + 3 * 5, s.getTotals().getPoints());
    }

    @Test
    void aPlayerWithNoGamesHasZeroTotalsAndNullAverages() {
        PlayerStats s = stats.getPlayerStats(idle);
        assertEquals(0, s.getGamesPlayed());
        assertEquals(0, s.getTotals().getPoints());
        assertNull(s.getPerGame());
        assertNull(s.getShooting().getFieldGoalPct());
        assertNull(s.getShooting().getTrueShootingPct());
    }

    @Test
    void unknownPlayerIs404() {
        assertThrows(ResourceNotFoundException.class, () -> stats.getPlayerStats("nope"));
        assertThrows(ResourceNotFoundException.class, () -> stats.getPlayerGameLog("nope"));
    }

    @Test
    void gameLogIsInPlayedAtOrderWithTheGameContext() {
        List<GameLogEntry> log = stats.getPlayerGameLog(t);
        assertEquals(5, log.size());
        assertEquals(List.of("KC", "KC", "STL", "STL", "STL"),
                log.stream().map(GameLogEntry::getTeamId).toList());
        for (int i = 1; i < log.size(); i++) {
            assertTrue(log.get(i - 1).getPlayedAt().isBefore(log.get(i).getPlayedAt()));
        }
        GameLogEntry first = log.get(0);
        assertFalse(first.getHome());
        assertEquals("SEA", first.getOpponentTeamId());
        assertEquals(12, first.getTeamScore());
        assertEquals(400, first.getOpponentScore());
        assertFalse(first.getStarted());
        GameLogEntry last = log.get(4);
        assertTrue(last.getHome());
        assertEquals("MIN", last.getOpponentTeamId());
        assertEquals(100, last.getBoxScore().getTechnicalFouls());
    }

    // --- team ---

    @Test
    void teamStatsSplitTheTeamFromItsOpponents() {
        TeamStats s = stats.getTeamStats("SEA");
        int seaPoints = 30 * 200 + 21 * 200 + 20 * 300;
        int kcPoints = 30 * 10 + 2 * 2;
        assertEquals(30, s.getGamesPlayed());
        assertEquals(seaPoints, s.getTotals().getPoints());
        assertEquals(kcPoints, s.getOpponentTotals().getPoints());
        assertEquals(seaPoints / 30.0, s.getPerGame().getPoints(), 1e-9);
        assertEquals(kcPoints / 30.0, s.getOpponentPerGame().getPoints(), 1e-9);
        assertEquals(3000, s.getPossessions());
        assertEquals(100.0, s.getPace(), 1e-9);
        assertEquals(seaPoints * 100.0 / 3000, s.getOffensiveRating(), 1e-9);
        assertEquals(kcPoints * 100.0 / 3000, s.getDefensiveRating(), 1e-9);
        assertEquals(s.getOffensiveRating() - s.getDefensiveRating(), s.getNetRating(), 1e-9);
    }

    @Test
    void aTradedPlayersLinesCountForTheTeamHePlayedFor() {
        assertEquals(15, stats.getTeamStats("STL").getTotals().getPoints());
        assertEquals(30 * 10 + 2 * 2, stats.getTeamStats("KC").getTotals().getPoints());
    }

    @Test
    void unknownTeamIs404() {
        assertThrows(ResourceNotFoundException.class, () -> stats.getTeamStats("NOPE"));
    }

    // --- leaders ---

    @Test
    void pointsLeadersRankThePerGameAverageAndTheQualifierIsSeventyPercent() {
        LeaderBoard board = stats.getLeaders(LeaderCategory.POINTS, 2);
        assertEquals(LeaderBoard.BasisEnum.PER_GAME, board.getBasis());
        List<LeaderEntry> e = board.getEntries();
        assertEquals(2, e.size());
        // a3 averages 300 but has 20 games, one short of 21: not ranked.
        assertTrue(e.stream().noneMatch(x -> x.getPlayerId().equals(a3)));
        // a1 and a2 tie at 200: shared rank, more games first.
        assertEquals(a1, e.get(0).getPlayerId());
        assertEquals(a2, e.get(1).getPlayerId());
        assertEquals(1, e.get(0).getRank());
        assertEquals(1, e.get(1).getRank());
        assertEquals(200.0, e.get(1).getValue());
        assertEquals(21, e.get(1).getGamesPlayed());
        assertEquals(4200, e.get(1).getTotals().getPoints());
        assertEquals("SEA", e.get(0).getTeamId());
        assertNotNull(e.get(0).getPlayerName());
    }

    @Test
    void technicalFoulsRankTheTotalWithoutAQualifier() {
        LeaderBoard board = stats.getLeaders(LeaderCategory.TECHNICAL_FOULS, 3);
        assertEquals(LeaderBoard.BasisEnum.TOTAL, board.getBasis());
        List<LeaderEntry> e = board.getEntries();
        assertEquals(List.of(t, b2, b1), e.stream().map(LeaderEntry::getPlayerId).toList());
        assertEquals(100.0, e.get(0).getValue());
        assertEquals(List.of(1, 2, 3), e.stream().map(LeaderEntry::getRank).toList());
        // The team of t's most recent game, not his first.
        assertEquals("STL", e.get(0).getTeamId());
    }

    @Test
    void percentageLeadersQualifyOnMakesPerTeamGameNotOnGames() {
        LeaderBoard board = stats.getLeaders(LeaderCategory.FIELD_GOAL_PCT, 3);
        assertEquals(LeaderBoard.BasisEnum.PERCENTAGE, board.getBasis());
        List<LeaderEntry> e = board.getEntries();
        // a3: 20 games but 1620 FGM, so he qualifies and leads at 81%.
        // b1 shoots 100% but 90 FGM < 3.66 × 30.
        assertEquals(a3, e.get(0).getPlayerId());
        assertEquals(0.81, e.get(0).getValue(), 1e-12);
        assertTrue(e.stream().noneMatch(x -> x.getPlayerId().equals(b1)));
    }

    @Test
    void everyLeaderCategoryAnswers() {
        for (LeaderCategory c : LeaderCategory.values()) {
            LeaderBoard board = stats.getLeaders(c, 100);
            assertEquals(c, board.getCategory());
            assertNotNull(board.getEntries(), c.toString());
        }
    }

    @Test
    void limitDefaultsToTheConfiguredValueAndRejectsOutOfRange() {
        assertTrue(stats.getLeaders(LeaderCategory.MINUTES, null).getEntries().size() <= 10);
        assertThrows(ResourceBadRequestException.class, () -> stats.getLeaders(LeaderCategory.POINTS, 0));
        assertThrows(ResourceBadRequestException.class, () -> stats.getLeaders(LeaderCategory.POINTS, 101));
        assertThrows(ResourceBadRequestException.class, () -> stats.getGameHighs(GameHighCategory.POINTS, 0));
    }

    // --- game highs ---

    @Test
    void gameHighsRankSingleGamesAndBreakTiesByEarliestDate() {
        GameHighs highs = stats.getGameHighs(GameHighCategory.POINTS, 3);
        List<GameHighEntry> e = highs.getEntries();
        assertEquals(3, e.size());
        assertTrue(e.stream().allMatch(x -> x.getPlayerId().equals(a3) && x.getValue() == 300));
        assertTrue(e.stream().allMatch(x -> x.getRank() == 1));
        assertTrue(e.get(0).getPlayedAt().isBefore(e.get(1).getPlayedAt()));
        assertEquals("SEA", e.get(0).getTeamId());
        assertEquals("KC", e.get(0).getOpponentTeamId());
        assertEquals("SEA", e.get(1).getTeamId());
        assertEquals("KC", e.get(1).getOpponentTeamId());
    }

    @Test
    void gameHighRanksStepPastATie() {
        GameHighs highs = stats.getGameHighs(GameHighCategory.TECHNICAL_FOULS, 3);
        List<GameHighEntry> e = highs.getEntries();
        assertEquals(List.of(100, 70, 2), e.stream().map(GameHighEntry::getValue).toList());
        assertEquals(List.of(1, 2, 3), e.stream().map(GameHighEntry::getRank).toList());
        assertEquals("MIN", e.get(0).getOpponentTeamId());
    }

    @Test
    void everyGameHighCategoryAnswers() {
        for (GameHighCategory c : GameHighCategory.values()) {
            assertEquals(c, stats.getGameHighs(c, 5).getCategory());
        }
    }

    // --- fixture ---

    private List<String> roster(String teamId) {
        return playerTeamRepo.findByTeamId(teamId).stream()
                .map(PlayerTeamEntity::getPlayerId).sorted().toList();
    }

    private String game(String home, String away, int day, int homeScore, int awayScore) {
        GameEntity g = new GameEntity();
        g.setId(UUID.randomUUID().toString());
        g.setHomeTeamId(home);
        g.setAwayTeamId(away);
        g.setStatus(GameStatus.FINAL);
        g.setHomeScore(homeScore);
        g.setAwayScore(awayScore);
        g.setPeriods(4);
        g.setHomePossessions(100);
        g.setAwayPossessions(100);
        g.setPlayedAt(DAY0.plusDays(day));
        gameRepo.save(g);
        return g.getId();
    }

    private void row(String gameId, String playerId, String teamId, boolean started, int points,
                     int fgm, int fga, int tpm, int tpa, int ftm, int fta, int technicals) {
        BoxScoreEntity b = new BoxScoreEntity();
        b.setId(UUID.randomUUID().toString());
        b.setGameId(gameId);
        b.setPlayerId(playerId);
        b.setTeamId(teamId);
        b.setStarted(started);
        b.setPoints(points);
        b.setFieldGoalsMade(fgm);
        b.setFieldGoalsAttempted(fga);
        b.setThreePointersMade(tpm);
        b.setThreePointersAttempted(tpa);
        b.setFreeThrowsMade(ftm);
        b.setFreeThrowsAttempted(fta);
        b.setTechnicalFouls(technicals);
        b.setOffensiveRebounds(1);
        b.setDefensiveRebounds(2);
        b.setAssists(1);
        b.setSteals(1);
        b.setBlocks(1);
        b.setTurnovers(1);
        b.setFouls(1);
        b.setMinutes(30);
        boxScoreRepo.save(b);
    }
}
