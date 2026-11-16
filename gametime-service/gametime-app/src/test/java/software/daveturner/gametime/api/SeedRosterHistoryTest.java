package software.daveturner.gametime.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.entity.*;
import software.daveturner.gametime.repo.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/** Changeset 1.04.7: every seeded roster assignment has one SEED history row. */
@SpringBootTest
class SeedRosterHistoryTest {

    @Autowired
    V1ApiDelegate api;

    @Autowired
    PlayerTeamHistRepo playerTeamHistRepo;

    /** player_id -> team_id, straight from the seed file. */
    private Map<String, String> seedRoster() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/db/roster.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(l -> !l.isBlank())
                    .map(l -> l.split(",", -1))
                    .collect(Collectors.toMap(c -> c[0], c -> c[1]));
        }
    }

    @Test
    void everySeededAssignmentHasExactlyOneSeedHistoryRow() throws IOException {
        Map<String, String> seeded = seedRoster();
        List<PlayerTeamHistEntity> seedRows = StreamSupport
                .stream(playerTeamHistRepo.findAll().spliterator(), false)
                .filter(h -> h.getTransactionType() == TransactionType.SEED)
                .toList();

        assertFalse(seeded.isEmpty());
        assertEquals(seeded.size(), seedRows.size());
        Map<String, String> histTeams = seedRows.stream()
                .collect(Collectors.toMap(PlayerTeamHistEntity::getPlayerId, PlayerTeamHistEntity::getTeamId));
        assertEquals(seeded, histTeams);
        seedRows.forEach(h -> {
            assertEquals("seed-" + h.getPlayerId(), h.getId());
            assertNotNull(h.getTransactionDate());
        });
    }

    @Test
    void aSeededPlayersHistoryStartsWithHisSeedTeam() throws IOException {
        Map.Entry<String, String> seeded = seedRoster().entrySet().iterator().next();

        var history = api.fetchPlayerHistory(seeded.getKey()).getBody();

        assertFalse(history.isEmpty());
        var oldest = history.get(history.size() - 1);
        assertEquals("SEED", oldest.getTransactionType().getValue());
        assertEquals(seeded.getValue(), oldest.getTeamId());
        assertEquals(seeded.getKey(), oldest.getPlayerId());
    }
}
