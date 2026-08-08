package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.model.LineupRole;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §3.5 (decisions.md #023): the rotation manager — energy drain/recover, the
 * between-possession substitution check (starter-priority, rotationDepth-gated,
 * rested-return), foul-out forced subs, and the never-below-5 invariant. All
 * deterministic given state; these tests consume no RNG.
 */
class RotationStateTest {

    private final SimConfig config = new SimConfig();

    private PlayerGameState player(String id, LineupRole role, Integer rotationOrder,
                                   int endurance, int energy) {
        return TestPlayerFactory.createRotationPlayer(id, "T", 10.0, role, rotationOrder,
                endurance, energy);
    }

    /** A squad of 5 starters + n bench players (rotationOrder 1..n), all avg. */
    private List<PlayerGameState> squad(int benchCount) {
        List<PlayerGameState> squad = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            squad.add(player("S" + i, LineupRole.STARTER, null, 10, 10));
        }
        for (int i = 1; i <= benchCount; i++) {
            squad.add(player("B" + i, LineupRole.ROTATION, i, 10, 10));
        }
        return squad;
    }

    private RotationState rotation(List<PlayerGameState> squad, CoachModifiers mods) {
        return new RotationState(squad, mods, config);
    }

    // --- Construction / invariant ---

    @Test
    void onFloorIsTheFiveStartersAtTipoff() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        assertEquals(5, r.onFloor().size());
        assertTrue(r.onFloor().containsAll(squad.subList(0, 5)),
                "The five starters start on the floor");
    }

    @Test
    void requiresAtLeastFivePlayers() {
        List<PlayerGameState> tooFew = squad(0).subList(0, 4);
        assertThrows(IllegalArgumentException.class,
                () -> rotation(new ArrayList<>(tooFew), CoachModifiers.neutral()));
    }

    @Test
    void onFloorStaysExactlyFiveAcrossManyPossessions() {
        RotationState r = rotation(squad(6), CoachModifiers.neutral());
        for (int i = 0; i < 500; i++) {
            r.advancePossession();
            assertEquals(5, r.onFloor().size(), "on-floor must always be exactly 5");
        }
    }

    // --- Energy drain / recovery ---

    @Test
    void onFloorPlayersDrainAndBenchRecovers() {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter = squad.get(0);
        PlayerGameState benchDeep = squad.get(squad.size() - 1); // never subs in early

        double startEnergy = starter.getCurrentEnergy();
        r.advancePossession();
        assertTrue(starter.getCurrentEnergy() < startEnergy, "on-floor starter drains");
        // A deep-bench player is at or above full (recovery caps at MAX_ENERGY).
        assertEquals(SimConfig.MAX_ENERGY, benchDeep.getCurrentEnergy(), 0.001);
    }

    @Test
    void higherEnduranceDrainsSlower() {
        PlayerGameState iron = player("iron", LineupRole.STARTER, null, 20, 10);
        PlayerGameState frail = player("frail", LineupRole.STARTER, null, 3, 10);
        List<PlayerGameState> squad = new ArrayList<>(List.of(
                iron, frail,
                player("S3", LineupRole.STARTER, null, 10, 10),
                player("S4", LineupRole.STARTER, null, 10, 10),
                player("S5", LineupRole.STARTER, null, 10, 10)));
        RotationState r = rotation(squad, CoachModifiers.neutral()); // no bench → no subs
        for (int i = 0; i < 10; i++) {
            r.advancePossession();
        }
        assertTrue(iron.getCurrentEnergy() > frail.getCurrentEnergy(),
                "high-endurance player retains more energy: iron=" + iron.getCurrentEnergy()
                        + " frail=" + frail.getCurrentEnergy());
    }

    @Test
    void fatigueFactorFallsWithEnergyButStaysModest() {
        PlayerGameState p = player("p", LineupRole.STARTER, null, 1, 1);
        List<PlayerGameState> squad = new ArrayList<>(List.of(p,
                player("S2", LineupRole.STARTER, null, 10, 10),
                player("S3", LineupRole.STARTER, null, 10, 10),
                player("S4", LineupRole.STARTER, null, 10, 10),
                player("S5", LineupRole.STARTER, null, 10, 10)));
        RotationState r = rotation(squad, CoachModifiers.neutral());
        for (int i = 0; i < 100; i++) {
            r.advancePossession();
        }
        double f = p.fatigueFactor();
        assertTrue(f < 1.0, "a drained player's fatigue factor is below 1.0");
        assertTrue(f >= 1.0 - SimConfig.FATIGUE_MAX_PENALTY - 1e-9,
                "fatigue is a modest thumb, never below 1 − FATIGUE_MAX_PENALTY: " + f);
    }

    // --- Fatigue substitution ---

    @Test
    void tiredStarterIsEventuallySubbedForFreshBench() {
        List<PlayerGameState> squad = squad(4);
        // A very aggressive coach pulls tired players earlier.
        CoachModifiers aggressive = coachMods(10, 20);
        RotationState r = rotation(squad, aggressive);
        PlayerGameState starter1 = squad.get(0);

        boolean starterWasBenched = false;
        for (int i = 0; i < 200 && !starterWasBenched; i++) {
            r.advancePossession();
            starterWasBenched = !r.onFloor().contains(starter1);
        }
        assertTrue(starterWasBenched, "a tired starter should eventually be subbed out");
    }

    @Test
    void restedPlayerBecomesEligibleToReturn() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState starter1 = squad.get(0);

        // Run long enough that starter1 gets pulled, rests, and returns.
        boolean pulled = false;
        boolean returned = false;
        for (int i = 0; i < 2000; i++) {
            r.advancePossession();
            if (!r.onFloor().contains(starter1)) {
                pulled = true;
            } else if (pulled) {
                returned = true;
                break;
            }
        }
        assertTrue(pulled, "starter should be pulled");
        assertTrue(returned, "a rested starter should return to the floor");
    }

    @Test
    void deeperRotationUsesMoreBenchPlayers() {
        // Tight rotation (low rotationDepth) vs deep rotation (high) over the same
        // run: the deep coach should give minutes to more distinct players.
        int tightPlayers = distinctPlayersUsed(coachMods(1, 20));
        int deepPlayers = distinctPlayersUsed(coachMods(20, 20));
        assertTrue(deepPlayers >= tightPlayers,
                "a deeper rotation uses at least as many players: deep=" + deepPlayers
                        + " tight=" + tightPlayers);
        assertTrue(deepPlayers > 5, "a deep rotation should use bench players");
    }

    private int distinctPlayersUsed(CoachModifiers mods) {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, mods);
        java.util.Set<PlayerGameState> used = new java.util.HashSet<>(r.onFloor());
        for (int i = 0; i < 400; i++) {
            r.advancePossession();
            used.addAll(r.onFloor());
        }
        return used.size();
    }

    // --- Foul-outs (Decision F) ---

    @Test
    void fouledOutPlayerIsForcedOffTheFloor() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter1 = squad.get(0);
        for (int f = 0; f < SimConfig.FOUL_OUT_LIMIT; f++) {
            starter1.recordFoul();
        }
        assertTrue(starter1.isFouledOut());

        r.advancePossession();
        assertFalse(r.onFloor().contains(starter1),
                "a fouled-out player must be forced off the floor");
        assertEquals(5, r.onFloor().size());
    }

    @Test
    void fouledOutPlayerNeverReturns() {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter1 = squad.get(0);
        for (int f = 0; f < SimConfig.FOUL_OUT_LIMIT; f++) {
            starter1.recordFoul();
        }
        for (int i = 0; i < 500; i++) {
            r.advancePossession();
            assertFalse(r.onFloor().contains(starter1),
                    "a fouled-out player is permanently ineligible");
        }
    }

    @Test
    void floorNeverDropsBelowFiveWhenWholeRosterFoulsOut() {
        // Degenerate path: a minimal 5-man squad where everyone fouls out. The
        // least-fouled available player must stay on so the floor holds at 5.
        List<PlayerGameState> squad = squad(0); // exactly 5, no bench
        RotationState r = rotation(squad, CoachModifiers.neutral());
        for (PlayerGameState p : squad) {
            for (int f = 0; f < SimConfig.FOUL_OUT_LIMIT; f++) {
                p.recordFoul();
            }
        }
        r.advancePossession();
        assertEquals(5, r.onFloor().size(),
                "floor never drops below 5 even when everyone has fouled out");
    }

    @Test
    void forcedSubDrawsFromFullRosterBeyondRotationDepth() {
        // A tight rotation (depth ~1) but many foul-outs must still reach deep bench
        // players — forced subs use the full roster, not the rotationDepth window.
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, coachMods(1, 10)); // tight depth
        // Foul out all five starters.
        for (int i = 0; i < 5; i++) {
            for (int f = 0; f < SimConfig.FOUL_OUT_LIMIT; f++) {
                squad.get(i).recordFoul();
            }
        }
        r.advancePossession();
        assertEquals(5, r.onFloor().size());
        for (PlayerGameState p : r.onFloor()) {
            assertFalse(p.isFouledOut(), "forced subs must replace fouled-out starters");
        }
    }

    private CoachModifiers coachMods(int rotationDepth, int subAggressiveness) {
        software.daveturner.gametime.model.Coach c = new software.daveturner.gametime.model.Coach();
        c.setPace(10);
        c.setOffensiveScheme(10);
        c.setDefensiveScheme(10);
        c.setRotationDepth(rotationDepth);
        c.setSubstitutionAggressiveness(subAggressiveness);
        return CoachModifiers.from(c, config);
    }
}
