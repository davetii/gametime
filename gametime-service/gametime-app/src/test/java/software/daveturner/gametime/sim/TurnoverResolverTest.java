package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class TurnoverResolverTest {

    private final SimConfig config = new SimConfig();
    private final TurnoverResolver resolver = new TurnoverResolver(config);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Test
    void averageVsAverageProducesTurnoverRateNearBase() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        List<PlayerGameState> defenders = List.of(
                TestPlayerFactory.create("d1", "B", 10.0),
                TestPlayerFactory.create("d2", "B", 10.0));
        int trials = 10_000;
        int turnovers = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isTurnover(handler, defenders, r)) turnovers++;
        }
        double rate = (double) turnovers / trials;
        assertEquals(SimConfig.BASE_TURNOVER, rate, 0.03);
    }

    @Test
    void poorBallHandlerTurnsOverMoreOften() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A",
                10, 10, 10, 10, 10, 1.0, 10, 10, 10, 10, 10, 10, 10);
        List<PlayerGameState> defenders = List.of(
                TestPlayerFactory.create("d1", "B", 20.0));
        int trials = 1_000;
        int turnovers = 0;
        RandomGenerator r = rng(55);
        for (int i = 0; i < trials; i++) {
            if (resolver.isTurnover(handler, defenders, r)) turnovers++;
        }
        double rate = (double) turnovers / trials;
        assertTrue(rate > 0.30, "Poor handler vs elite D should turn over > 30%, got " + rate);
    }

    @Test
    void eliteBallHandlerTurnsOverRarely() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A",
                10, 10, 10, 10, 10, 20.0, 10, 10, 10, 10, 10, 10, 10);
        List<PlayerGameState> defenders = List.of(
                TestPlayerFactory.create("d1", "B", 1.0));
        int trials = 1_000;
        int turnovers = 0;
        RandomGenerator r = rng(55);
        for (int i = 0; i < trials; i++) {
            if (resolver.isTurnover(handler, defenders, r)) turnovers++;
        }
        double rate = (double) turnovers / trials;
        assertTrue(rate < 0.10, "Elite handler vs weak D should turn over < 10%, got " + rate);
    }

    // --- §3.9 turnover sub-cause draw (decisions.md #027) ---

    private static final double NEUTRAL_PRESSURE = 1.0;

    @Test
    void pickCauseFixedSeedIsDeterministic() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        TurnoverCause first = resolver.pickCause(handler, 10.0, NEUTRAL_PRESSURE, rng(7));
        TurnoverCause second = resolver.pickCause(handler, 10.0, NEUTRAL_PRESSURE, rng(7));
        assertEquals(first, second, "Same seed + inputs must draw the same cause");
    }

    @Test
    void pickCauseAlwaysReturnsAValidCause() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            TurnoverCause cause = resolver.pickCause(handler, 10.0, NEUTRAL_PRESSURE, r);
            assertNotNull(cause);
        }
    }

    @Test
    void pickCauseKeepsStolenDominantAtAverageInputs() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        int trials = 50_000;
        int stolen = 0;
        RandomGenerator r = rng(99);
        for (int i = 0; i < trials; i++) {
            if (resolver.pickCause(handler, 10.0, NEUTRAL_PRESSURE, r) == TurnoverCause.STOLEN) {
                stolen++;
            }
        }
        double share = (double) stolen / trials;
        // At average inputs all leans are ×1.0, so STOLEN's share is its tier weight
        // over the raw total (56 / 100 = 0.56) — kept dominant (#027 B, ~55–60%).
        assertEquals(0.56, share, 0.02,
                "STOLEN should stay dominant (~56%) at average inputs, got " + share);
    }

    @Test
    void pickCauseCoversAllNineCauses() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        java.util.EnumSet<TurnoverCause> seen = java.util.EnumSet.noneOf(TurnoverCause.class);
        RandomGenerator r = rng(123);
        for (int i = 0; i < 100_000 && seen.size() < TurnoverCause.values().length; i++) {
            seen.add(resolver.pickCause(handler, 10.0, NEUTRAL_PRESSURE, r));
        }
        assertEquals(TurnoverCause.values().length, seen.size(),
                "Every cause should be reachable over enough draws; missing "
                        + java.util.EnumSet.complementOf(seen));
    }

    @Test
    void weakOffenseShiftsMixTowardOffensiveFoulAndBadPass() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        int trials = 50_000;
        int strongOffenseUnforced = 0;
        int weakOffenseUnforced = 0;
        RandomGenerator rStrong = rng(2024);
        RandomGenerator rWeak = rng(2024);
        for (int i = 0; i < trials; i++) {
            if (isUnforcedOffenseError(
                    resolver.pickCause(handler, 16.0, NEUTRAL_PRESSURE, rStrong))) {
                strongOffenseUnforced++;
            }
            if (isUnforcedOffenseError(
                    resolver.pickCause(handler, 4.0, NEUTRAL_PRESSURE, rWeak))) {
                weakOffenseUnforced++;
            }
        }
        assertTrue(weakOffenseUnforced > strongOffenseUnforced,
                "A weaker offense should commit more OFFENSIVE_FOUL/BAD_PASS turnovers: weak="
                        + weakOffenseUnforced + " strong=" + strongOffenseUnforced);
    }

    private boolean isUnforcedOffenseError(TurnoverCause cause) {
        return cause == TurnoverCause.OFFENSIVE_FOUL || cause == TurnoverCause.BAD_PASS;
    }

    @Test
    void poorHandlerAndPressureShiftMixTowardShotClockViolation() {
        PlayerGameState smartHandler = TestPlayerFactory.create("h1", "A", 18.0);
        PlayerGameState poorHandler = TestPlayerFactory.create("h2", "A", 3.0);
        int trials = 50_000;
        int calmSmart = 0;
        int pressuredPoor = 0;
        RandomGenerator rCalm = rng(555);
        RandomGenerator rPressured = rng(555);
        for (int i = 0; i < trials; i++) {
            if (resolver.pickCause(smartHandler, 10.0, 1.0, rCalm)
                    == TurnoverCause.SHOT_CLOCK_VIOLATION) {
                calmSmart++;
            }
            if (resolver.pickCause(poorHandler, 10.0, 1.4, rPressured)
                    == TurnoverCause.SHOT_CLOCK_VIOLATION) {
                pressuredPoor++;
            }
        }
        assertTrue(pressuredPoor > calmSmart,
                "A low-acumen handler vs a pressuring scheme should draw more shot-clock "
                        + "violations: pressured=" + pressuredPoor + " calm=" + calmSmart);
    }

    @Test
    void pickStealerReturnsValidDefender() {
        List<PlayerGameState> defenders = List.of(
                TestPlayerFactory.create("d1", "B", 10.0),
                TestPlayerFactory.create("d2", "B", 10.0),
                TestPlayerFactory.create("d3", "B", 10.0));
        PlayerGameState stealer = resolver.pickStealer(defenders, rng(42));
        assertTrue(defenders.contains(stealer));
    }

    @Test
    void pickStealerFavorsHighStealingSkill() {
        PlayerGameState weak1 = TestPlayerFactory.create("d1", "B",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 1.0, 10);
        PlayerGameState elite = TestPlayerFactory.create("d2", "B",
                10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20.0, 10);
        List<PlayerGameState> defenders = List.of(weak1, elite);
        int elitePicked = 0;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 1_000; i++) {
            if (resolver.pickStealer(defenders, r) == elite) elitePicked++;
        }
        assertTrue(elitePicked > 800, "Elite stealer should be picked most often");
    }

    // --- §3.4 defensive-pressure modifier ---

    @Test
    void higherDefensivePressureRaisesTurnoverRate() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 10.0);
        List<PlayerGameState> defenders = List.of(TestPlayerFactory.create("d1", "B", 10.0));
        int trials = 10_000;
        int neutral = 0;
        int pressured = 0;
        RandomGenerator r1 = rng(42);
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isTurnover(handler, defenders, 1.0, r1)) neutral++;
            if (resolver.isTurnover(handler, defenders, 1.5, r2)) pressured++;
        }
        assertTrue(pressured > neutral,
                "Higher defensive pressure should force more turnovers: pressured=" + pressured
                        + " neutral=" + neutral);
    }

    @Test
    void defensivePressureOfOneMatchesUnmodified() {
        PlayerGameState handler = TestPlayerFactory.create("h1", "A", 11.0);
        List<PlayerGameState> defenders = List.of(TestPlayerFactory.create("d1", "B", 9.0));
        assertEquals(
                resolver.isTurnover(handler, defenders, rng(33)),
                resolver.isTurnover(handler, defenders, 1.0, rng(33)),
                "Pressure 1.0 must equal the unmodified overload for the same seed");
    }
}
