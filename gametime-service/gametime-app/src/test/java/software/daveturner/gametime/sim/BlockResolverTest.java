package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class BlockResolverTest {

    private final SimConfig config = new SimConfig();
    private final BlockResolver resolver = new BlockResolver(config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    private Map<BlockRecovery, Integer> tally(int trials) {
        Map<BlockRecovery, Integer> counts = new EnumMap<>(BlockRecovery.class);
        for (BlockRecovery r : BlockRecovery.values()) {
            counts.put(r, 0);
        }
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            BlockRecovery outcome = resolver.resolveRecovery(r);
            counts.merge(outcome, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void allFourOutcomesAreReachable() {
        Map<BlockRecovery, Integer> counts = tally(50_000);
        for (BlockRecovery r : BlockRecovery.values()) {
            assertTrue(counts.get(r) > 0, r + " should be reachable, got " + counts.get(r));
        }
    }

    @Test
    void recoveryIsDefenseLeaning() {
        // Decision D: defense keeps the ball (RECOVERED_DEFENSE + OOB_DEFENSE) more
        // often than the offense (RECOVERED_OFFENSE + OOB_OFFENSE).
        Map<BlockRecovery, Integer> counts = tally(100_000);
        int defense = counts.get(BlockRecovery.RECOVERED_DEFENSE)
                + counts.get(BlockRecovery.OOB_DEFENSE);
        int offense = counts.get(BlockRecovery.RECOVERED_OFFENSE)
                + counts.get(BlockRecovery.OOB_OFFENSE);
        assertTrue(defense > offense,
                "recovery should be defense-leaning: defense=" + defense + " offense=" + offense);
    }

    @Test
    void weightOrderingHoldsInDistribution() {
        // Decision D ordering: RECOVERED_DEFENSE > RECOVERED_OFFENSE > the two OOB slices.
        Map<BlockRecovery, Integer> counts = tally(200_000);
        assertTrue(counts.get(BlockRecovery.RECOVERED_DEFENSE)
                        > counts.get(BlockRecovery.RECOVERED_OFFENSE),
                "RECOVERED_DEFENSE should be the most common outcome");
        assertTrue(counts.get(BlockRecovery.RECOVERED_OFFENSE)
                        > counts.get(BlockRecovery.OOB_DEFENSE),
                "RECOVERED_OFFENSE should outnumber OOB_DEFENSE");
        assertTrue(counts.get(BlockRecovery.RECOVERED_OFFENSE)
                        > counts.get(BlockRecovery.OOB_OFFENSE),
                "RECOVERED_OFFENSE should outnumber OOB_OFFENSE");
    }

    @Test
    void distributionMatchesConfiguredWeights() {
        int trials = 200_000;
        Map<BlockRecovery, Integer> counts = tally(trials);
        double total = SimConfig.BLOCK_RECOVERED_DEFENSE + SimConfig.BLOCK_RECOVERED_OFFENSE
                + SimConfig.BLOCK_OOB_DEFENSE + SimConfig.BLOCK_OOB_OFFENSE;

        assertEquals(SimConfig.BLOCK_RECOVERED_DEFENSE / total,
                (double) counts.get(BlockRecovery.RECOVERED_DEFENSE) / trials, 0.01);
        assertEquals(SimConfig.BLOCK_RECOVERED_OFFENSE / total,
                (double) counts.get(BlockRecovery.RECOVERED_OFFENSE) / trials, 0.01);
        assertEquals(SimConfig.BLOCK_OOB_DEFENSE / total,
                (double) counts.get(BlockRecovery.OOB_DEFENSE) / trials, 0.01);
        assertEquals(SimConfig.BLOCK_OOB_OFFENSE / total,
                (double) counts.get(BlockRecovery.OOB_OFFENSE) / trials, 0.01);
    }

    @Test
    void offenseRetainsPredicateMatchesOutcomes() {
        assertTrue(BlockRecovery.RECOVERED_OFFENSE.offenseRetains());
        assertTrue(BlockRecovery.OOB_OFFENSE.offenseRetains());
        assertFalse(BlockRecovery.RECOVERED_DEFENSE.offenseRetains());
        assertFalse(BlockRecovery.OOB_DEFENSE.offenseRetains());
    }

    @Test
    void deterministicWithSameSeed() {
        assertEquals(resolver.resolveRecovery(rng(123)), resolver.resolveRecovery(rng(123)));
    }
}
