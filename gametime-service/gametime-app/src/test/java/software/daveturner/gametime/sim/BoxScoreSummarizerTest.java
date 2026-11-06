package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.daveturner.gametime.sim.BoxScoreSummarizer.*;

class BoxScoreSummarizerTest {

    private static final String H = "H";
    private static final String A = "A";

    private final List<GameData.EventRecord> events = new ArrayList<>();
    private int seq = 1;

    private void add(PlayType type, String outcome, String primary) {
        add(type, outcome, primary, null, null, null);
    }

    private void add(PlayType type, String outcome, String primary,
                     String assist, String committing, String opponent) {
        events.add(new GameData.EventRecord(H, A, 1, seq++, type, outcome,
                primary, assist, committing, opponent));
    }

    private Line line(String playerId) {
        return summarize(events).lineFor(playerId);
    }

    // --- points / FGA / FGM / 3PA / 3PM ---

    @Test
    void madeTwoIsTwoPointsOneAttemptOneMake() {
        add(PlayType.SHOT, "MADE_2PT_DRIVE", "h1");
        Line l = line("h1");
        assertEquals(2, l.points());
        assertEquals(1, l.fieldGoalsAttempted());
        assertEquals(1, l.fieldGoalsMade());
        assertEquals(0, l.threePointersAttempted());
        assertEquals(0, l.threePointersMade());
    }

    @Test
    void madeThreeIsThreePointsAndCountsTheThreeColumns() {
        add(PlayType.SHOT, "MADE_3PT", "h1");
        Line l = line("h1");
        assertEquals(3, l.points());
        assertEquals(1, l.fieldGoalsAttempted());
        assertEquals(1, l.fieldGoalsMade());
        assertEquals(1, l.threePointersAttempted());
        assertEquals(1, l.threePointersMade());
    }

    @Test
    void missedShotsAreAttemptsWithoutMakesOrPoints() {
        add(PlayType.SHOT, "MISSED_2PT_POST", "h1");
        add(PlayType.SHOT, "MISSED_3PT", "h1");
        Line l = line("h1");
        assertEquals(0, l.points());
        assertEquals(2, l.fieldGoalsAttempted());
        assertEquals(0, l.fieldGoalsMade());
        assertEquals(1, l.threePointersAttempted());
        assertEquals(0, l.threePointersMade());
    }

    @Test
    void blockedThreeIsAnAttemptAndAThreeAttemptButNoMakeAndCreditsTheBlocker() {
        add(PlayType.SHOT, "BLOCKED_3PT", "h1", null, null, "a1");
        Line shooter = line("h1");
        assertEquals(1, shooter.fieldGoalsAttempted());
        assertEquals(1, shooter.threePointersAttempted());
        assertEquals(0, shooter.fieldGoalsMade());
        assertEquals(0, shooter.threePointersMade());
        assertEquals(0, shooter.points());
        assertEquals(1, line("a1").blocks());
        assertEquals(0, shooter.blocks());
    }

    @Test
    void blockedTwoCreditsTheBlocker() {
        add(PlayType.SHOT, "BLOCKED_2PT_DRIVE", "h1", null, null, "a2");
        assertEquals(1, line("h1").fieldGoalsAttempted());
        assertEquals(0, line("h1").threePointersAttempted());
        assertEquals(1, line("a2").blocks());
    }

    // --- free throws ---

    @Test
    void everyFreeThrowIsAnAttemptAndEachMakeIsOnePoint() {
        add(PlayType.FREE_THROW, "MADE_SHOOTING", "h1");
        add(PlayType.FREE_THROW, "MISSED_BONUS", "h1");
        add(PlayType.FREE_THROW, "MADE_TECHNICAL", "h1");
        add(PlayType.FREE_THROW, "MADE_FLAGRANT", "h1");
        Line l = line("h1");
        assertEquals(4, l.freeThrowsAttempted());
        assertEquals(3, l.freeThrowsMade());
        assertEquals(3, l.points());
        assertEquals(0, l.fieldGoalsAttempted());
    }

    // --- rebounds ---

    @Test
    void offensiveAndDefensiveReboundsCreditThePrimary() {
        add(PlayType.REBOUND, "OFFENSIVE", "h1");
        add(PlayType.REBOUND, "DEFENSIVE", "a1");
        add(PlayType.REBOUND, "DEFENSIVE", "a1");
        assertEquals(1, line("h1").offensiveRebounds());
        assertEquals(0, line("h1").defensiveRebounds());
        assertEquals(2, line("a1").defensiveRebounds());
    }

    @Test
    void outOfBoundsReboundCreditsNoOne() {
        add(PlayType.REBOUND, "OUT_OF_BOUNDS_OFFENSE", null);
        add(PlayType.REBOUND, "OUT_OF_BOUNDS_DEFENSE", null);
        assertTrue(summarize(events).lines().isEmpty());
    }

    // --- assists / steals / turnovers ---

    @Test
    void assistCreditsTheAssisterNotTheShooter() {
        add(PlayType.SHOT, "MADE_2PT_PERIMETER", "h1", "h2", null, null);
        assertEquals(1, line("h2").assists());
        assertEquals(0, line("h2").fieldGoalsAttempted());
        assertEquals(0, line("h1").assists());
    }

    @Test
    void stealCreditsTheOpponentAndTheTurnoverTheBallHandler() {
        add(PlayType.TURNOVER, "STOLEN", "h1", null, null, "a1");
        assertEquals(1, line("h1").turnovers());
        assertEquals(1, line("a1").steals());
        assertEquals(0, line("a1").turnovers());
    }

    @Test
    void everyTurnoverCauseIsATurnover() {
        for (TurnoverCause cause : TurnoverCause.values()) {
            add(PlayType.TURNOVER, cause.outcome(), "h1",
                    null, null, cause == TurnoverCause.STOLEN ? "a1" : null);
        }
        assertEquals(TurnoverCause.values().length, line("h1").turnovers());
        assertEquals(1, line("a1").steals());
    }

    // --- fouls ---

    @Test
    void personalFoulsCreditTheCommitter() {
        add(PlayType.FOUL, "SHOOTING_FOUL", "a1", null, A, "h1");
        add(PlayType.FOUL, "NON_SHOOTING_FOUL", "a1", null, A, "h1");
        add(PlayType.FOUL, "REBOUNDING_FOUL_DEFENSE", "a1", null, A, null);
        add(PlayType.FOUL, "REBOUNDING_FOUL_OFFENSE", "h1", null, H, null);
        add(PlayType.FOUL, PossessionEngine.FLAGRANT_FOUL_1_OUTCOME, "a1", null, A, "h1");
        add(PlayType.FOUL, PossessionEngine.FLAGRANT_FOUL_2_OUTCOME, "a1", null, A, "h1");
        assertEquals(5, line("a1").fouls());
        assertEquals(1, line("h1").fouls());
        assertEquals(0, line("a1").technicalFouls());
    }

    @Test
    void technicalIsNotAPersonalFoul() {
        add(PlayType.FOUL, GameData.TECHNICAL_FOUL_OUTCOME, "a1", null, A, null);
        assertEquals(1, line("a1").technicalFouls());
        assertEquals(0, line("a1").fouls());
    }

    @Test
    void theChargeIsOneTurnoverAndOneFoulOnTheSamePlayer() {
        add(PlayType.TURNOVER, TurnoverCause.OFFENSIVE_FOUL.outcome(), "h1");
        add(PlayType.FOUL, "OFFENSIVE_FOUL", "h1", null, H, null);
        Line l = line("h1");
        assertEquals(1, l.turnovers());
        assertEquals(1, l.fouls());
        assertEquals(0, l.steals());
    }

    @Test
    void andOneIsAMadeFieldGoalThenAFoulOnTheDefenderThenOneFreeThrow() {
        add(PlayType.SHOT, "MADE_2PT_DRIVE", "h1");
        add(PlayType.FOUL, "AND_ONE", "a1", null, A, "h1");
        add(PlayType.FREE_THROW, "MADE_AND_ONE", "h1");
        Line shooter = line("h1");
        assertEquals(3, shooter.points());
        assertEquals(1, shooter.fieldGoalsAttempted());
        assertEquals(1, shooter.fieldGoalsMade());
        assertEquals(1, shooter.freeThrowsAttempted());
        assertEquals(1, shooter.freeThrowsMade());
        assertEquals(0, shooter.fouls());
        assertEquals(1, line("a1").fouls());
    }

    // --- summary helpers ---

    @Test
    void uncreditedPlayerGetsTheEmptyLine() {
        add(PlayType.SHOT, "MADE_3PT", "h1");
        assertEquals(Line.empty("h9"), summarize(events).lineFor("h9"));
    }

    @Test
    void teamPointsSumTheGivenPlayersLines() {
        add(PlayType.SHOT, "MADE_3PT", "h1");
        add(PlayType.SHOT, "MADE_2PT_POST", "h2");
        add(PlayType.FREE_THROW, "MADE_TECHNICAL", "a1");
        Summary s = summarize(events);
        assertEquals(5, s.points(List.of("h1", "h2", "h3")));
        assertEquals(1, s.points(List.of("a1")));
    }

    @Test
    void aCreditingEventWithNoPlayerIsRejected() {
        add(PlayType.SHOT, "MADE_3PT", null);
        assertThrows(IllegalStateException.class, () -> summarize(events));
    }

    @Test
    void everyColumnReadsItsOwnFieldOfTheLine() {
        Line l = new Line("p", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        int expected = 1;
        for (Column c : Column.values()) {
            assertEquals(expected++, c.derived(l), c.columnName());
        }
    }

    @Test
    void everyColumnReadsItsOwnCounter() {
        PlayerGameState p = TestPlayerFactory.create("p", H, 10);
        p.recordFieldGoalAttempt();
        p.recordFieldGoalMade(3);
        p.recordThreePointAttempt();
        p.recordThreePointMade();
        p.recordFreeThrowAttempt();
        p.recordFreeThrowMade();
        p.recordOffensiveRebound();
        p.recordDefensiveRebound();
        p.recordAssist();
        p.recordSteal();
        p.recordBlock();
        p.recordTurnover();
        p.recordFoul();
        p.recordTechnicalFoul();
        assertEquals(4, Column.POINTS.counted(p));
        for (Column c : Column.values()) {
            if (c != Column.POINTS) {
                assertEquals(1, c.counted(p), c.columnName());
            }
        }
    }
}
