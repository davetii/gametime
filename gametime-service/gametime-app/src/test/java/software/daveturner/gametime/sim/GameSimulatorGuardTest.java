package software.daveturner.gametime.sim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSimulatorGuardTest {

    private PlayerGameState h1;
    private PlayerGameState a1;
    private PlayerGameState a2;
    private List<PlayerGameState> home;
    private List<PlayerGameState> away;
    private GameData data;
    private final List<GameData.EventRecord> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        h1 = TestPlayerFactory.create("h1", "H", 10);
        a1 = TestPlayerFactory.create("a1", "A", 10);
        a2 = TestPlayerFactory.create("a2", "A", 10);
        h1.markTookFloor();
        a1.markTookFloor();
        home = List.of(h1);
        away = List.of(a1, a2);
        data = new GameData();
        data.setHomeTeamId("H");
        data.setAwayTeamId("A");

        // h1 makes a three; the engine counts it on both sides.
        events.add(new GameData.EventRecord("H", "A", 1, 1, PlayType.SHOT, "MADE_3PT",
                "h1", null, null, null));
        h1.recordFieldGoalAttempt();
        h1.recordThreePointAttempt();
        h1.recordFieldGoalMade(3);
        h1.recordThreePointMade();
        data.addScore("H", 3);
    }

    private void verify() {
        GameSimulator.verifyAgainstCounters(BoxScoreSummarizer.summarize(events), home, away, data);
    }

    @Test
    void agreeingLogAndCountersPass() {
        assertDoesNotThrow(this::verify);
    }

    @Test
    void aDesyncedCounterThrowsNamingPlayerColumnAndBothValues() {
        h1.recordAssist();
        IllegalStateException e = assertThrows(IllegalStateException.class, this::verify);
        assertTrue(e.getMessage().contains("h1"), e.getMessage());
        assertTrue(e.getMessage().contains("assists derived 0"), e.getMessage());
        assertTrue(e.getMessage().contains("counted 1"), e.getMessage());
    }

    @Test
    void aDoctoredEventListThrows() {
        events.add(new GameData.EventRecord("A", "H", 1, 2, PlayType.REBOUND, "DEFENSIVE",
                "a1", null, null, null));
        IllegalStateException e = assertThrows(IllegalStateException.class, this::verify);
        assertTrue(e.getMessage().contains("defensive_rebounds"), e.getMessage());
    }

    @Test
    void anEventCreditingAPlayerWhoNeverTookTheFloorThrows() {
        events.add(new GameData.EventRecord("A", "H", 1, 2, PlayType.REBOUND, "DEFENSIVE",
                "a2", null, null, null));
        a2.recordDefensiveRebound();
        IllegalStateException e = assertThrows(IllegalStateException.class, this::verify);
        assertTrue(e.getMessage().contains("a2"), e.getMessage());
        assertTrue(e.getMessage().contains("never took the floor"), e.getMessage());
    }

    @Test
    void anEventCreditingAPlayerOutsideBothSquadsThrows() {
        events.add(new GameData.EventRecord("A", "H", 1, 2, PlayType.REBOUND, "DEFENSIVE",
                "x9", null, null, null));
        assertThrows(IllegalStateException.class, this::verify);
    }

    @Test
    void aTeamScoreThatDisagreesWithDerivedPointsThrows() {
        data.addScore("A", 2);
        IllegalStateException e = assertThrows(IllegalStateException.class, this::verify);
        assertTrue(e.getMessage().contains("away team A points derived 0"), e.getMessage());
    }

    @Test
    void aHomeScoreThatDisagreesThrows() {
        data.addScore("H", 1);
        IllegalStateException e = assertThrows(IllegalStateException.class, this::verify);
        assertTrue(e.getMessage().contains("home team H"), e.getMessage());
    }
}
