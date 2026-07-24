package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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

    // ---------- §3.10: the two-sided rebounding foul (decisions.md #028 A2/C) ----------

    private List<PlayerGameState> five(String teamId, double skill) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + i, teamId, skill));
        }
        return players;
    }

    /** Fraction of misses that draw a rebounding foul, over many trials. */
    private double reboundFoulRate(List<PlayerGameState> offense, List<PlayerGameState> defense,
                                   double pressure, int trials, long seed) {
        RandomGenerator r = rng(seed);
        int fouls = 0;
        for (int i = 0; i < trials; i++) {
            if (resolver.resolveReboundFoul(offense, defense, pressure, r) != null) {
                fouls++;
            }
        }
        return fouls / (double) trials;
    }

    @Test
    void reboundFoulIsRareButHappens() {
        // The whole point of the top-carve (#028 C) is that it stays a THIN slice:
        // most misses go to the board contest untouched.
        double rate = reboundFoulRate(five("A", 10.0), five("B", 10.0), 1.0, 20_000, 42);
        assertTrue(rate > 0.005, "Rebounding fouls must actually happen, was " + rate);
        assertTrue(rate < 0.15, "Rebounding fouls must stay rare, was " + rate);
    }

    @Test
    void sideDrawLeansDefense() {
        // #028 A2: a defensive box-out push dominates; offensive over-the-back is
        // the genuine minority. Guards the LEAN, not an exact ratio.
        RandomGenerator r = rng(7);
        int defense = 0;
        int offense = 0;
        for (int i = 0; i < 40_000; i++) {
            ReboundFoul foul = resolver.resolveReboundFoul(five("A", 10.0), five("B", 10.0), 1.0, r);
            if (foul == null) continue;
            if (foul.side() == ReboundFoul.Side.DEFENSE) defense++; else offense++;
        }
        assertTrue(defense > 0 && offense > 0, "Both sides must be reachable");
        assertTrue(defense > offense * 2,
                "Defense must dominate the side draw, was def=" + defense + " off=" + offense);
    }

    @Test
    void undisciplinedDefenseFoulsMoreOnTheGlass() {
        // foulProne drives the rate (inverted: high foulProne = low discipline).
        double disciplined = reboundFoulRate(five("A", 10.0), five("B", 1.0), 1.0, 20_000, 99);
        double undisciplined = reboundFoulRate(five("A", 10.0), five("B", 19.0), 1.0, 20_000, 99);
        assertTrue(undisciplined > disciplined,
                "An undisciplined (high foulProne) five must foul more on the glass: "
                        + undisciplined + " vs " + disciplined);
    }

    @Test
    void defensivePressureRaisesTheReboundFoulRate() {
        // The coach.md pressure/breakdown trade-off applies here as it does to the
        // shooting foul — an aggressive scheme concedes more contact.
        double neutral = reboundFoulRate(five("A", 10.0), five("B", 10.0), 1.0, 20_000, 5);
        double aggressive = reboundFoulRate(five("A", 10.0), five("B", 10.0), 1.6, 20_000, 5);
        assertTrue(aggressive > neutral,
                "Higher defensivePressure must raise the rate: " + aggressive + " vs " + neutral);
    }

    @Test
    void reboundFoulBaseIsTunableDownward() {
        // Regression guard for the PROB_FLOOR trap (#028 C implementation note):
        // clampProbability floors at 0.02, which on a ~0.03..0.055 base would act
        // as a FLOOR and make the knob tunable only UPWARD — a "turn it down"
        // recalibration would silently do nothing. rareEventProbability must floor
        // at 0 instead, so a tiny base really does yield a tiny rate.
        double tiny = config.rareEventProbability(0.001, 10.0, 10.0,
                SimConfig.REBOUND_FOUL_SENSITIVITY);
        assertEquals(0.001, tiny, 1e-9,
                "A rare-event base below PROB_FLOOR must NOT be floored up to it");
        assertEquals(0.0, config.rareEventProbability(0.0, 10.0, 10.0,
                SimConfig.REBOUND_FOUL_SENSITIVITY), 1e-9,
                "A zero base must yield zero, so the feature can be switched off");
    }

    @Test
    void committerComesFromTheCommittingSide() {
        RandomGenerator r = rng(3);
        List<PlayerGameState> offense = five("OFF", 10.0);
        List<PlayerGameState> defense = five("DEF", 10.0);
        int checked = 0;
        for (int i = 0; i < 20_000 && checked < 200; i++) {
            ReboundFoul foul = resolver.resolveReboundFoul(offense, defense, 1.0, r);
            if (foul == null) continue;
            checked++;
            String expectedTeam = foul.side() == ReboundFoul.Side.OFFENSE ? "OFF" : "DEF";
            assertEquals(expectedTeam, foul.committer().getTeamId(),
                    "The committer must be a player on the side that committed");
        }
        assertTrue(checked > 0, "Expected at least one foul to inspect");
    }

    @Test
    void reboundFoulIsDeterministicForASeed() {
        List<PlayerGameState> offense = five("OFF", 10.0);
        List<PlayerGameState> defense = five("DEF", 10.0);
        ReboundFoul first = resolver.resolveReboundFoul(offense, defense, 1.0, rng(2024));
        ReboundFoul second = resolver.resolveReboundFoul(offense, defense, 1.0, rng(2024));
        assertEquals(first == null, second == null, "Same seed must give the same hit/miss");
        if (first != null) {
            assertEquals(first.side(), second.side());
            assertEquals(first.committer().getPlayerId(), second.committer().getPlayerId());
        }
    }

    @Test
    void pickCommitterFallsBackWhenWeightsAreZero() {
        // Defensive edge: an all-zero foulProne five makes the weighted walk fall
        // through to the final return (the same edge pickStealer/pickWeighted has).
        List<PlayerGameState> zeroed = five("Z", 0.0);
        assertNotNull(resolver.pickCommitter(zeroed, rng(1)));
    }

    @Test
    void reboundFoulSideCarriesTheOutcomeStringAndPossessionFork() {
        assertEquals("REBOUNDING_FOUL_DEFENSE", ReboundFoul.Side.DEFENSE.outcome());
        assertEquals("REBOUNDING_FOUL_OFFENSE", ReboundFoul.Side.OFFENSE.outcome());
        // An offensive foul is a turnover-like loss of the ball — it ALWAYS ends
        // the possession; a defensive one may leave the offense the ball (#028 B).
        assertTrue(ReboundFoul.Side.OFFENSE.endsPossession());
        assertFalse(ReboundFoul.Side.DEFENSE.endsPossession());
    }
}
