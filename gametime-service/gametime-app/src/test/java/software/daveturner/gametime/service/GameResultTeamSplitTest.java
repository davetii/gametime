package software.daveturner.gametime.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.daveturner.gametime.entity.LineupRole;
import software.daveturner.gametime.entity.PlayerTeamEntity;
import software.daveturner.gametime.model.BoxScore;
import software.daveturner.gametime.model.GameResult;
import software.daveturner.gametime.repo.BoxScoreRepo;
import software.daveturner.gametime.repo.PlayerTeamHistRepo;
import software.daveturner.gametime.repo.PlayerTeamRepo;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GameResultTeamSplitTest {

    @Autowired
    GametimeService service;
    @Autowired
    PlayerTeamRepo playerTeamRepo;
    @Autowired
    PlayerTeamHistRepo playerTeamHistRepo;
    @Autowired
    BoxScoreRepo boxScoreRepo;

    @Test
    void aReleasedHomePlayersLineStaysInTheHomeBoxScore() {
        GameResult before = service.simulateGame("BOS", "LA", 42L);
        String released = before.getHomeBoxScore().get(0).getPlayerId();

        service.removePlayerFromTeam("BOS", released);
        GameResult after = service.getGame(before.getGame().getId());

        assertTrue(ids(after.getHomeBoxScore()).contains(released));
        assertFalse(ids(after.getAwayBoxScore()).contains(released));
        assertEquals(before.getHomeBoxScore().size(), after.getHomeBoxScore().size());
    }

    @Test
    void aSignedPlayersTeamIdIsTheTeamOfHisLatestHistoryRow() {
        PlayerTeamEntity mover = playerTeamRepo.findByTeamId("BOS").stream()
                .filter(pt -> pt.getLineupRole() == LineupRole.STARTER)
                .findFirst().orElseThrow();
        PlayerTeamEntity displaced = playerTeamRepo.findByTeamId("LA").stream()
                .filter(pt -> pt.getRotationOrder() != null && pt.getLineupRole() != LineupRole.STARTER)
                .min(Comparator.comparingInt(PlayerTeamEntity::getRotationOrder))
                .orElseThrow();
        String moverId = mover.getPlayerId();

        service.removePlayerFromTeam("BOS", moverId);
        service.removePlayerFromTeam("LA", displaced.getPlayerId());
        service.addPlayerToTeam("LA", moverId);
        PlayerTeamEntity signed = playerTeamRepo.findById(moverId).orElseThrow();
        signed.setLineupRole(displaced.getLineupRole());
        signed.setRotationOrder(displaced.getRotationOrder());
        playerTeamRepo.save(signed);

        GameResult result = service.simulateGame("LA", "BOS", 7L);

        String latestTeam = playerTeamHistRepo.findByPlayerIdOrderByTransactionDateDesc(moverId)
                .get(0).getTeamId();
        assertEquals("LA", latestTeam);
        String rowTeam = boxScoreRepo.findByGameId(result.getGame().getId()).stream()
                .filter(r -> r.getPlayerId().equals(moverId))
                .findFirst().orElseThrow().getTeamId();
        assertEquals(latestTeam, rowTeam);
        assertTrue(ids(result.getHomeBoxScore()).contains(moverId));
    }

    private static List<String> ids(List<BoxScore> lines) {
        return lines.stream().map(BoxScore::getPlayerId).toList();
    }
}
