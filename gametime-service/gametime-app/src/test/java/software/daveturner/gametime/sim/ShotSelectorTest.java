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
}
