package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class ShotResolverTest {

    private final SimConfig config = new SimConfig();
    private final ShotResolver resolver = new ShotResolver(config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Test
    void averageVsAverageProducesMakeRateNearBase() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        int trials = 10_000;
        int makes = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isMade(ShotType.PERIMETER, shooter, defender, r)) makes++;
        }
        double rate = (double) makes / trials;
        assertEquals(SimConfig.BASE_PERIMETER, rate, 0.03);
    }

    @Test
    void eliteShooterVsWeakDefenderMakesMostShots() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        int trials = 1_000;
        int makes = 0;
        RandomGenerator r = rng(99);
        for (int i = 0; i < trials; i++) {
            if (resolver.isMade(ShotType.DRIVE, shooter, defender, r)) makes++;
        }
        double rate = (double) makes / trials;
        assertTrue(rate > 0.80, "Elite vs weak should make > 80%, got " + rate);
    }

    @Test
    void weakShooterVsEliteDefenderMakesFewShots() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 1.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 20.0);
        int trials = 1_000;
        int makes = 0;
        RandomGenerator r = rng(77);
        for (int i = 0; i < trials; i++) {
            if (resolver.isMade(ShotType.THREE, shooter, defender, r)) makes++;
        }
        double rate = (double) makes / trials;
        assertTrue(rate < 0.15, "Weak vs elite should make < 15%, got " + rate);
    }

    @ParameterizedTest
    @EnumSource(ShotType.class)
    void baseProbabilityReturnsPositiveForAllTypes(ShotType type) {
        double base = resolver.baseProbability(type);
        assertTrue(base > 0 && base < 1);
    }

    @ParameterizedTest
    @EnumSource(ShotType.class)
    void defenseSkillReturnsReasonableValue(ShotType type) {
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        double skill = resolver.defenseSkillForShot(type, defender);
        assertTrue(skill >= 1.0 && skill <= 20.0);
    }

    @Test
    void deterministicWithSameSeed() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 12.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 8.0);
        boolean result1 = resolver.isMade(ShotType.THREE, shooter, defender, rng(123));
        boolean result2 = resolver.isMade(ShotType.THREE, shooter, defender, rng(123));
        assertEquals(result1, result2);
    }
}
