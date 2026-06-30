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

    @Test
    void isStolenReturnsBothOutcomes() {
        boolean foundStolen = false;
        boolean foundNotStolen = false;
        RandomGenerator r = rng(42);
        for (int i = 0; i < 100; i++) {
            if (resolver.isStolen(r)) foundStolen = true;
            else foundNotStolen = true;
            if (foundStolen && foundNotStolen) break;
        }
        assertTrue(foundStolen && foundNotStolen);
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
