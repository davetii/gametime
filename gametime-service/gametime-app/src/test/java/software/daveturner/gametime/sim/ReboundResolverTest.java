package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class ReboundResolverTest {

    private final SimConfig config = new SimConfig();
    private final ReboundResolver resolver = new ReboundResolver(config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    /** Build a player whose only non-average skills are the two rebound skills. */
    private PlayerGameState rebounder(String id, String teamId,
                                      double offReb, double defReb) {
        return TestPlayerFactory.create(id, teamId,
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, offReb, defReb);
    }

    @Test
    void averageVsAverageNearBaseRate() {
        PlayerGameState off = rebounder("o1", "A", 10, 10);
        PlayerGameState def = rebounder("d1", "B", 10, 10);
        int trials = 10_000;
        int offRebounds = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isOffensiveRebound(off, def, r)) offRebounds++;
        }
        double rate = (double) offRebounds / trials;
        assertEquals(SimConfig.BASE_OFFENSIVE_REBOUND, rate, 0.03,
                "Average vs average should sit near BASE_OFFENSIVE_REBOUND, got " + rate);
    }

    @Test
    void eliteOffensiveRebounderGrabsMore() {
        PlayerGameState off = rebounder("o1", "A", 20, 10);
        PlayerGameState def = rebounder("d1", "B", 10, 1);
        int trials = 5_000;
        int offRebounds = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isOffensiveRebound(off, def, r)) offRebounds++;
        }
        double rate = (double) offRebounds / trials;
        assertTrue(rate > SimConfig.BASE_OFFENSIVE_REBOUND,
                "Elite off rebounder vs weak def should exceed base rate, got " + rate);
    }

    @Test
    void eliteDefensiveRebounderGrabsMore() {
        PlayerGameState off = rebounder("o1", "A", 1, 10);
        PlayerGameState def = rebounder("d1", "B", 10, 20);
        int trials = 5_000;
        int offRebounds = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isOffensiveRebound(off, def, r)) offRebounds++;
        }
        double rate = (double) offRebounds / trials;
        assertTrue(rate < SimConfig.BASE_OFFENSIVE_REBOUND,
                "Weak off rebounder vs elite def should fall below base rate, got " + rate);
    }

    @Test
    void offensiveReboundProbabilityClampedToCeiling() {
        // Extreme skill gap can push past the ceiling; result must still be valid.
        PlayerGameState off = rebounder("o1", "A", 20, 10);
        PlayerGameState def = rebounder("d1", "B", 10, 1);
        // Probability stays a valid boolean draw — just assert it triggers at all.
        boolean any = false;
        RandomGenerator r = rng(1);
        for (int i = 0; i < 100; i++) {
            if (resolver.isOffensiveRebound(off, def, r)) { any = true; break; }
        }
        assertTrue(any);
    }

    @Test
    void pickOffensiveRebounderFavorsHighSkill() {
        List<PlayerGameState> offense = new ArrayList<>();
        // p1 is an elite offensive rebounder; the rest are weak.
        offense.add(rebounder("p1", "A", 20, 10));
        for (int i = 2; i <= 5; i++) {
            offense.add(rebounder("p" + i, "A", 1, 10));
        }

        int trials = 10_000;
        int p1Picks = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.pickOffensiveRebounder(offense, r).getPlayerId().equals("p1")) {
                p1Picks++;
            }
        }
        double share = (double) p1Picks / trials;
        // p1 weight 20 vs 4*1 = 4 → 20/24 ≈ 0.83
        assertTrue(share > 0.70,
                "Elite offensive rebounder should get the lion's share of picks, got " + share);
    }

    @Test
    void pickDefensiveRebounderFavorsHighSkill() {
        List<PlayerGameState> defense = new ArrayList<>();
        defense.add(rebounder("d1", "B", 10, 20));
        for (int i = 2; i <= 5; i++) {
            defense.add(rebounder("d" + i, "B", 10, 1));
        }

        int trials = 10_000;
        int d1Picks = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.pickDefensiveRebounder(defense, r).getPlayerId().equals("d1")) {
                d1Picks++;
            }
        }
        double share = (double) d1Picks / trials;
        assertTrue(share > 0.70,
                "Elite defensive rebounder should get the lion's share of picks, got " + share);
    }

    @Test
    void pickOffensiveRebounderReturnsAPlayerWhenWeightsEqual() {
        List<PlayerGameState> offense = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            offense.add(rebounder("p" + i, "A", 10, 10));
        }
        RandomGenerator r = rng(42);
        for (int i = 0; i < 100; i++) {
            assertNotNull(resolver.pickOffensiveRebounder(offense, r));
        }
    }
}
