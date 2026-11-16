package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class PossessionTest {

    private final SimConfig config = SimConfig.baseline();

    @Test
    void eachEmitTakesTheNextSequenceNumberInTheFixedOrientation() {
        GameData data = new GameData();
        Possession p = new Possession(data, "OFF", "DEF", List.of(), List.of(), 2, 10,
                RandomGenerator.of("L64X128MixRandom"));

        p.emit(PlayType.SHOT, "MISSED_3PT", "a");
        p.emit(PlayType.SHOT, "MADE_3PT", "a", "b");
        p.emit(PlayType.FOUL, "SHOOTING_FOUL", "c", null, "DEF");
        p.emit(PlayType.FOUL, "AND_ONE", "c", null, "DEF", "a");

        assertEquals(14, p.sequence());
        List<GameData.EventRecord> events = data.getEvents();
        for (int i = 0; i < events.size(); i++) {
            GameData.EventRecord e = events.get(i);
            assertEquals(10 + i, e.sequence());
            assertEquals("OFF", e.offTeamId());
            assertEquals("DEF", e.defTeamId());
            assertEquals(2, e.period());
        }
        assertEquals("b", events.get(1).assistPlayerId());
        assertEquals("DEF", events.get(2).committingTeamId());
        assertEquals("a", events.get(3).opponentPlayerId());
    }

    @Test
    void ofReadsTeamIdsAndTheCurrentOnFloorFives() {
        List<PlayerGameState> home = five("H");
        List<PlayerGameState> away = five("A");
        TeamContext homeCtx = new TeamContext("H",
                new RotationState(home, CoachModifiers.neutral(), config), CoachModifiers.neutral());
        TeamContext awayCtx = new TeamContext("A",
                new RotationState(away, CoachModifiers.neutral(), config), CoachModifiers.neutral());
        GameData data = new GameData();
        RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        Possession p = Possession.of(data, homeCtx, awayCtx, 3, 7, rng);

        assertSame(data, p.data());
        assertEquals("H", p.offTeamId());
        assertEquals("A", p.defTeamId());
        assertSame(homeCtx.onFloor(), p.offense());
        assertSame(awayCtx.onFloor(), p.defense());
        assertEquals(3, p.period());
        assertEquals(7, p.sequence());
        assertSame(rng, p.rng());
    }

    private List<PlayerGameState> five(String teamId) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + "-p" + i, teamId, 10));
        }
        return players;
    }
}
