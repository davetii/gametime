package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class FoulResolverTest {

    private final SimConfig config = new SimConfig();
    private final FoulResolver resolver = new FoulResolver(config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Test
    void foulNeverTriggeredOnPerimeter() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            assertFalse(resolver.isFoul(ShotType.PERIMETER, shooter, defender, r));
        }
    }

    @Test
    void foulNeverTriggeredOnThree() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            assertFalse(resolver.isFoul(ShotType.THREE, shooter, defender, r));
        }
    }

    @Test
    void foulCanTriggerOnDrive() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        boolean anyFoul = false;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            if (resolver.isFoul(ShotType.DRIVE, shooter, defender, r)) {
                anyFoul = true;
                break;
            }
        }
        assertTrue(anyFoul, "Drive should trigger fouls sometimes");
    }

    @Test
    void foulCanTriggerOnPost() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        boolean anyFoul = false;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            if (resolver.isFoul(ShotType.POST, shooter, defender, r)) {
                anyFoul = true;
                break;
            }
        }
        assertTrue(anyFoul, "Post should trigger fouls sometimes");
    }

    @Test
    void eliteFoulDrawerGetsFouledMore() {
        PlayerGameState eliteDrawer = TestPlayerFactory.create("s1", "A",
                10, 10, 10, 10, 10, 10, 10, 20.0, 10, 10, 10, 10, 1.0);
        PlayerGameState foulProne = TestPlayerFactory.create("d1", "B",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20.0);
        int fouls = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            if (resolver.isFoul(ShotType.DRIVE, eliteDrawer, foulProne, r)) fouls++;
        }
        double rate = (double) fouls / 1_000;
        assertTrue(rate > 0.30, "Elite drawer vs foul-prone should foul > 30%, got " + rate);
    }

    @Test
    void freeThrowMadeAtAverageSkillNearBase() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        int trials = 10_000;
        int makes = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isFreeThrowMade(shooter, r)) makes++;
        }
        double rate = (double) makes / trials;
        assertEquals(SimConfig.FT_BASE, rate, 0.03);
    }

    @Test
    void eliteFreeThrowShooterMakesMost() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A",
                10, 10, 10, 10, 10, 10, 20.0, 10, 10, 10, 10, 10, 10);
        int trials = 1_000;
        int makes = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isFreeThrowMade(shooter, r)) makes++;
        }
        double rate = (double) makes / trials;
        assertTrue(rate > 0.85, "Elite FT shooter should make > 85%, got " + rate);
    }

    @Test
    void poorFreeThrowShooterMissesMore() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A",
                10, 10, 10, 10, 10, 10, 1.0, 10, 10, 10, 10, 10, 10);
        int trials = 1_000;
        int makes = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isFreeThrowMade(shooter, r)) makes++;
        }
        double rate = (double) makes / trials;
        assertTrue(rate < 0.65, "Poor FT shooter should make < 65%, got " + rate);
    }

    // --- §3.4 defensive-pressure modifier ---

    @Test
    void higherDefensivePressureRaisesFoulRate() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        int trials = 10_000;
        int neutral = 0;
        int pressured = 0;
        RandomGenerator r1 = rng(42);
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isFoul(ShotType.DRIVE, shooter, defender, 1.0, r1)) neutral++;
            if (resolver.isFoul(ShotType.DRIVE, shooter, defender, 1.5, r2)) pressured++;
        }
        assertTrue(pressured > neutral,
                "Higher defensive pressure should concede more fouls: pressured=" + pressured
                        + " neutral=" + neutral);
    }

    @Test
    void pressureStillNeverFoulsOnJumpShots() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            assertFalse(resolver.isFoul(ShotType.THREE, shooter, defender, 2.0, r),
                    "Even max pressure cannot foul on a three");
        }
    }

    @Test
    void defensivePressureOfOneMatchesUnmodified() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 12.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 8.0);
        assertEquals(
                resolver.isFoul(ShotType.DRIVE, shooter, defender, rng(21)),
                resolver.isFoul(ShotType.DRIVE, shooter, defender, 1.0, rng(21)),
                "Pressure 1.0 must equal the unmodified overload for the same seed");
    }
}
