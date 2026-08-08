package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class ShotSelectorTest {

    private final ShotSelector selector = new ShotSelector();

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Test
    void pickShooterFavorsHigherOffensiveSkill() {
        PlayerGameState star = TestPlayerFactory.create("star", "A", 20.0);
        PlayerGameState role = TestPlayerFactory.create("role", "A", 5.0);
        List<PlayerGameState> players = List.of(star, role);

        int starPicked = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            if (selector.pickShooter(players, r) == star) starPicked++;
        }
        assertTrue(starPicked > 700, "Star should shoot much more, got " + starPicked);
    }

    @Test
    void pickShooterReturnsValidPlayer() {
        List<PlayerGameState> players = List.of(
                TestPlayerFactory.create("p1", "A", 10.0),
                TestPlayerFactory.create("p2", "A", 10.0),
                TestPlayerFactory.create("p3", "A", 10.0));
        PlayerGameState chosen = selector.pickShooter(players, rng(42));
        assertTrue(players.contains(chosen));
    }

    @Test
    void pickShotTypeReturnsValidType() {
        PlayerGameState player = TestPlayerFactory.create("p1", "A", 10.0);
        ShotType type = selector.pickShotType(player, rng(42));
        assertNotNull(type);
    }

    @Test
    void pickShotTypeFavorsStrongestSkill() {
        // Player with elite longRange, weak everything else
        PlayerGameState sniper = TestPlayerFactory.create("s1", "A",
                1, 1, 1, 1, 20.0, 10, 10, 10, 10, 10, 10, 10, 10);
        int threes = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            if (selector.pickShotType(sniper, r) == ShotType.THREE) threes++;
        }
        assertTrue(threes > 600, "Sniper should shoot many 3s, got " + threes);
    }

    @Test
    void pickShotTypeCoversAllTypes() {
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        boolean[] seen = new boolean[ShotType.values().length];
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            ShotType type = selector.pickShotType(balanced, r);
            seen[type.ordinal()] = true;
        }
        for (ShotType t : ShotType.values()) {
            assertTrue(seen[t.ordinal()], "Should see " + t + " at least once");
        }
    }

    @Test
    void pickDefenderReturnsValidDefender() {
        List<PlayerGameState> defenders = List.of(
                TestPlayerFactory.create("d1", "B", 10.0),
                TestPlayerFactory.create("d2", "B", 10.0));
        PlayerGameState chosen = selector.pickDefender(defenders, rng(42));
        assertTrue(defenders.contains(chosen));
    }

    // --- §3.4 shot-mix lean (offensiveScheme) ---

    @Test
    void shotMixLeanShiftsDrawTowardPerimeterAndThree() {
        // A balanced shooter: a >1 lean should take more PERIMETER/THREE shots
        // than the neutral (1.0) draw over the same seed stream.
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        int neutralJumpers = 0;
        int leanedJumpers = 0;
        RandomGenerator r1 = rng(42);
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < 5_000; i++) {
            if (isJumper(selector.pickShotType(balanced, 1.0, r1))) neutralJumpers++;
            if (isJumper(selector.pickShotType(balanced, 1.6, r2))) leanedJumpers++;
        }
        assertTrue(leanedJumpers > neutralJumpers,
                "A >1 shot-mix lean should produce more jumpers: leaned=" + leanedJumpers
                        + " neutral=" + neutralJumpers);
    }

    @Test
    void shotMixLeanOfOneMatchesUnmodifiedDraw() {
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        assertEquals(
                selector.pickShotType(balanced, rng(7)),
                selector.pickShotType(balanced, 1.0, rng(7)),
                "Lean 1.0 must match the unmodified draw for the same seed");
    }

    private boolean isJumper(ShotType t) {
        return t == ShotType.PERIMETER || t == ShotType.THREE;
    }
}
