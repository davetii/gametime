package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.entity.PlayType;

import static org.junit.jupiter.api.Assertions.*;

class GameDataTest {

    @Test
    void addScoreTracksHomeAndAway() {
        GameData data = new GameData();
        data.setHomeTeamId("H");
        data.setAwayTeamId("A");

        data.addScore("H", 2);
        data.addScore("H", 3);
        data.addScore("A", 2);

        assertEquals(5, data.getHomeScore());
        assertEquals(2, data.getAwayScore());
    }

    @Test
    void addEventAccumulatesEvents() {
        GameData data = new GameData();
        data.addEvent("H", "A", 1, 1, PlayType.SHOT, "MADE_2PT_DRIVE", "p1");
        data.addEvent("A", "H", 1, 2, PlayType.TURNOVER, "STOLEN", "p2");

        assertEquals(2, data.getEvents().size());
        assertEquals(PlayType.SHOT, data.getEvents().get(0).playType());
        assertEquals(PlayType.TURNOVER, data.getEvents().get(1).playType());
    }

    @Test
    void periodsGetterAndSetter() {
        GameData data = new GameData();
        data.setPeriods(5);
        assertEquals(5, data.getPeriods());
    }

    @Test
    void teamIdGettersAndSetters() {
        GameData data = new GameData();
        data.setHomeTeamId("BOS");
        data.setAwayTeamId("LA");
        assertEquals("BOS", data.getHomeTeamId());
        assertEquals("LA", data.getAwayTeamId());
    }

    // ---------- §3.10: the derived penalty predicate (decisions.md #028 A1) ----------

    /** Add one FOUL committed by {@code teamId} in {@code period}. */
    private void addFoul(GameData data, String teamId, int period, String outcome) {
        data.addEvent("H", "A", period, 1, PlayType.FOUL, outcome, "p1", null, teamId);
    }

    @Test
    void committingTeamIdIsCarriedOnTheEvent() {
        GameData data = new GameData();
        addFoul(data, "H", 1, "SHOOTING_FOUL");
        assertEquals("H", data.getEvents().get(0).committingTeamId());
    }

    @Test
    void committingTeamIdIsNullOnTheShorterOverloads() {
        GameData data = new GameData();
        data.addEvent("H", "A", 1, 1, PlayType.SHOT, "MADE_3PT", "p1");
        data.addEvent("H", "A", 1, 2, PlayType.SHOT, "MADE_3PT", "p1", "p2");
        assertNull(data.getEvents().get(0).committingTeamId());
        assertNull(data.getEvents().get(1).committingTeamId());
    }

    @Test
    void notInBonusBelowTheThreshold() {
        GameData data = new GameData();
        for (int i = 0; i < SimConfig.BONUS_FOULS_PER_PERIOD - 1; i++) {
            addFoul(data, "H", 1, "SHOOTING_FOUL");
        }
        assertEquals(SimConfig.BONUS_FOULS_PER_PERIOD - 1, data.periodFoulCount("H", 1));
        assertFalse(data.isInBonus("H", 1), "One foul short of the limit is NOT the bonus");
    }

    @Test
    void theNthFoulItselfPutsTheTeamInTheBonus() {
        // EMIT-THEN-COUNT (#028 A1, the crux): the foul that REACHES the threshold
        // is the one that awards — "in the bonus" means the count HAS reached the
        // limit, so the predicate is >= and the current foul is already in the log.
        GameData data = new GameData();
        for (int i = 0; i < SimConfig.BONUS_FOULS_PER_PERIOD - 1; i++) {
            addFoul(data, "H", 1, "SHOOTING_FOUL");
        }
        assertFalse(data.isInBonus("H", 1));

        addFoul(data, "H", 1, "REBOUNDING_FOUL_DEFENSE"); // the Nth
        assertTrue(data.isInBonus("H", 1),
                "The Nth foul must itself put the team in the bonus (emit-then-count)");
    }

    @Test
    void staysInBonusAboveTheThreshold() {
        GameData data = new GameData();
        for (int i = 0; i < SimConfig.BONUS_FOULS_PER_PERIOD + 3; i++) {
            addFoul(data, "H", 1, "SHOOTING_FOUL");
        }
        assertTrue(data.isInBonus("H", 1));
    }

    @Test
    void bothFoulTypesCountTowardOneUnifiedTally() {
        // #028 A1: all fouls count — shooting AND rebounding, not split per type.
        GameData data = new GameData();
        addFoul(data, "H", 1, "SHOOTING_FOUL");
        addFoul(data, "H", 1, "REBOUNDING_FOUL_DEFENSE");
        addFoul(data, "H", 1, "REBOUNDING_FOUL_OFFENSE");
        assertEquals(3, data.periodFoulCount("H", 1));
    }

    @Test
    void foulsAreGroupedByCommittingTeamNotPossessionOrientation() {
        // The whole reason committing_team_id exists (#028 A2/D): an offensive
        // over-the-back is committed by the OFFENSE, so grouping by offense/defense
        // orientation would attribute it to the wrong team.
        GameData data = new GameData();
        addFoul(data, "H", 1, "REBOUNDING_FOUL_DEFENSE");
        addFoul(data, "A", 1, "REBOUNDING_FOUL_OFFENSE");
        addFoul(data, "A", 1, "SHOOTING_FOUL");

        assertEquals(1, data.periodFoulCount("H", 1));
        assertEquals(2, data.periodFoulCount("A", 1));
    }

    @Test
    void onlyFoulsInTheSamePeriodCount() {
        // No reset logic exists BECAUSE the predicate filters by period (#028 A1).
        GameData data = new GameData();
        for (int i = 0; i < SimConfig.BONUS_FOULS_PER_PERIOD; i++) {
            addFoul(data, "H", 1, "SHOOTING_FOUL");
        }
        assertTrue(data.isInBonus("H", 1));
        assertFalse(data.isInBonus("H", 2),
                "A new period starts clean with no reset logic — the filter does it");
        assertEquals(0, data.periodFoulCount("H", 2));
    }

    @Test
    void nonFoulEventsNeverCountTowardThePenalty() {
        GameData data = new GameData();
        for (int i = 0; i < 10; i++) {
            data.addEvent("H", "A", 1, i, PlayType.TURNOVER, "OFFENSIVE_FOUL", "p1",
                    null, "H");
        }
        assertEquals(0, data.periodFoulCount("H", 1),
                "A TURNOVER with an OFFENSIVE_FOUL cause is not a FOUL event (#027 B)");
        assertFalse(data.isInBonus("H", 1));
    }

    @Test
    void foulsWithNoCommittingTeamAreIgnored() {
        GameData data = new GameData();
        data.addEvent("H", "A", 1, 1, PlayType.FOUL, "SHOOTING_FOUL", "p1");
        assertEquals(0, data.periodFoulCount("H", 1));
    }
}
