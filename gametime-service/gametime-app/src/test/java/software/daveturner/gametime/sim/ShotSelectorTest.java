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

    /**
     * §3.17 (decisions.md #040 C) re-baselined this. It used to assert an elite-
     * {@code longRange} / weak-everything-else player shot >60% threes, which held
     * while the weights WERE the raw skills — a 20-vs-1 skill ratio was a 20-vs-1
     * weight ratio. It is no longer: the weights are the league share table bent by
     * {@code SHOT_MIX_SENSITIVITY}, which is a bounded multiplier, so a skill extreme
     * can no longer produce a weight extreme. <b>That bound is the point of the pass,
     * not a loss of fidelity</b> — the league mix stopped being at the mercy of the
     * player population (#040 A/C). The assertion that survives is the directional one:
     * this player must still clearly favour the three.
     */
    @Test
    void pickShotTypeFavorsStrongestSkill() {
        // Player with elite longRange, weak everything else
        PlayerGameState sniper = TestPlayerFactory.create("s1", "A",
                1, 1, 1, 1, 20.0, 10, 10, 10, 10, 10, 10, 10, 10);
        PlayerGameState average = TestPlayerFactory.create("avg", "A", 10.0);
        int threes = 0;
        int averageThrees = 0;
        RandomGenerator r = rng(42);
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < 5_000; i++) {
            if (selector.pickShotType(sniper, r) == ShotType.THREE) threes++;
            if (selector.pickShotType(average, r2) == ShotType.THREE) averageThrees++;
        }
        assertTrue(threes > averageThrees,
                "Sniper should shoot more 3s than an average player, got " + threes
                        + " vs " + averageThrees);
        assertTrue(threes > 5_000 * 0.45,
                "the three must still be this player's dominant shot, got " + threes);
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

    // --- §3.4 / §3.17 shot-mix lean (offensiveScheme) ---

    /**
     * §3.17 (decisions.md #040 D). ⚠ <b>THIS TEST'S OLD PREMISE IS NOW FALSE BY
     * DESIGN.</b> It used to assert that a high lean produced more <i>jumpers</i>
     * (PERIMETER <b>or</b> THREE together), which was exactly the conflation #036 D
     * named as the blocker: the lean scaled both, so a jump-shooting coach raised
     * mid-range and threes in lockstep — the one shape the real game forbids, since the
     * modern game trades the mid-range jumper <i>for</i> the three.
     *
     * <p>The lean is now two opposed leans, so the assertion splits in two: a high lean
     * raises THREE <b>and lowers PERIMETER</b>.
     */
    @Test
    void aHighLeanRaisesThreesAndLowersMidRange() {
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        int neutralThrees = 0;
        int leanedThrees = 0;
        int neutralPerimeter = 0;
        int leanedPerimeter = 0;
        RandomGenerator r1 = rng(42);
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < 5_000; i++) {
            ShotType neutral = selector.pickShotType(balanced, 1.0, r1);
            ShotType leaned = selector.pickShotType(balanced, 1.6, r2);
            if (neutral == ShotType.THREE) neutralThrees++;
            if (leaned == ShotType.THREE) leanedThrees++;
            if (neutral == ShotType.PERIMETER) neutralPerimeter++;
            if (leaned == ShotType.PERIMETER) leanedPerimeter++;
        }
        assertTrue(leanedThrees > neutralThrees,
                "A >1 lean must produce MORE threes: leaned=" + leanedThrees
                        + " neutral=" + neutralThrees);
        assertTrue(leanedPerimeter < neutralPerimeter,
                "A >1 lean must produce FEWER mid-range jumpers (#040 D — the "
                        + "reciprocal): leaned=" + leanedPerimeter
                        + " neutral=" + neutralPerimeter);
    }

    /** §3.17 (#040 D): a LOW lean is the mirror — fewer threes, more mid-range. */
    @Test
    void aLowLeanLowersThreesAndRaisesMidRange() {
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        int neutralThrees = 0;
        int leanedThrees = 0;
        int neutralPerimeter = 0;
        int leanedPerimeter = 0;
        RandomGenerator r1 = rng(11);
        RandomGenerator r2 = rng(11);
        for (int i = 0; i < 5_000; i++) {
            ShotType neutral = selector.pickShotType(balanced, 1.0, r1);
            ShotType leaned = selector.pickShotType(balanced, 0.625, r2);
            if (neutral == ShotType.THREE) neutralThrees++;
            if (leaned == ShotType.THREE) leanedThrees++;
            if (neutral == ShotType.PERIMETER) neutralPerimeter++;
            if (leaned == ShotType.PERIMETER) leanedPerimeter++;
        }
        assertTrue(leanedThrees < neutralThrees,
                "A <1 lean must produce FEWER threes: leaned=" + leanedThrees
                        + " neutral=" + neutralThrees);
        assertTrue(leanedPerimeter > neutralPerimeter,
                "A <1 lean must produce MORE mid-range jumpers: leaned=" + leanedPerimeter
                        + " neutral=" + neutralPerimeter);
    }

    /**
     * §3.17 (#040 D): DRIVE and POST are unscaled by the lean — the axis is
     * mid-range-vs-three, NOT jumper-vs-interior. Pinning this is what stops a later
     * change quietly re-widening the lean back to the pre-§3.17 shape.
     */
    @Test
    void theLeanLeavesDriveAndPostUntouched() {
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        int neutralInterior = 0;
        int leanedInterior = 0;
        RandomGenerator r1 = rng(99);
        RandomGenerator r2 = rng(99);
        for (int i = 0; i < 20_000; i++) {
            if (isInterior(selector.pickShotType(balanced, 1.0, r1))) neutralInterior++;
            if (isInterior(selector.pickShotType(balanced, 1.6, r2))) leanedInterior++;
        }
        // Not identical — the lean changes the NORMALIZER, so the interior share moves
        // a little even though neither interior weight is scaled. What must hold is
        // that the interior does not move the way a jumper-vs-interior lean would: the
        // two jumper types trade with EACH OTHER, so the interior stays close.
        double drift = Math.abs(leanedInterior - neutralInterior) / (double) neutralInterior;
        assertTrue(drift < 0.15,
                "DRIVE+POST are unscaled by the lean, so their combined share must stay "
                        + "close: neutral=" + neutralInterior + " leaned=" + leanedInterior
                        + " drift=" + drift);
    }

    @Test
    void shotMixLeanOfOneMatchesUnmodifiedDraw() {
        PlayerGameState balanced = TestPlayerFactory.create("p1", "A", 10.0);
        assertEquals(
                selector.pickShotType(balanced, rng(7)),
                selector.pickShotType(balanced, 1.0, rng(7)),
                "Lean 1.0 must match the unmodified draw for the same seed");
    }

    // --- §3.17 the share table + the skill modifier (#040 C/J) ---

    /**
     * §3.17 (#040 C/J): <b>the per-player check the aggregates physically cannot
     * make.</b> {@code SHOT_MIX_SENSITIVITY} is an unsourced feel constant whose error
     * is invisible in the league aggregates <i>by construction</i> — the shares are
     * normalized, so bending who takes which shot does not move the league mix at all.
     * The only honest check is this one: a {@code longRange} specialist must visibly
     * out-shoot a {@code longRange}-poor big, and <b>neither may reach a degenerate
     * share</b>.
     */
    @Test
    void skillBendsTheShareTableWithoutEitherPlayerGoingDegenerate() {
        // longRange 19 against longRange 6, everything else average.
        PlayerGameState sniper = TestPlayerFactory.create("sniper", "A",
                10, 10, 10, 10, 19.0, 10, 10, 10, 10, 10, 10, 10, 10);
        PlayerGameState big = TestPlayerFactory.create("big", "A",
                10, 10, 10, 10, 6.0, 10, 10, 10, 10, 10, 10, 10, 10);

        int sniperThrees = 0;
        int bigThrees = 0;
        RandomGenerator r1 = rng(2024);
        RandomGenerator r2 = rng(2024);
        int trials = 20_000;
        for (int i = 0; i < trials; i++) {
            if (selector.pickShotType(sniper, r1) == ShotType.THREE) sniperThrees++;
            if (selector.pickShotType(big, r2) == ShotType.THREE) bigThrees++;
        }
        double sniperShare = sniperThrees / (double) trials;
        double bigShare = bigThrees / (double) trials;

        assertTrue(sniperShare > bigShare + 0.10,
                "A longRange-19 specialist must shoot VISIBLY more threes than a "
                        + "longRange-6 big: " + sniperShare + " vs " + bigShare);
        // Neither degenerate: the specialist is not a three-only player and the big
        // has not stopped shooting them. This is the bound the aggregates cannot see.
        assertTrue(sniperShare < 0.75,
                "the specialist must not become a three-only player: " + sniperShare);
        assertTrue(bigShare > 0.05,
                "the big must not stop shooting threes entirely: " + bigShare);
    }

    /**
     * §3.17 (#040 C): an average player draws the LEAGUE share table, because every
     * skill modifier is 1.0 at skill 10 ({@code SkillCalculator}s all centre there).
     * This is what makes {@code sim.shot-share-*} a readable calibration surface rather
     * than a number whose meaning depends on the population.
     */
    @Test
    void anAveragePlayerDrawsTheLeagueShareTable() {
        SimConfig config = SimConfig.baseline();
        PlayerGameState average = TestPlayerFactory.create("avg", "A", 10.0);
        double total = 0;
        for (ShotType t : ShotType.values()) {
            total += config.shotShare(t);
        }
        for (ShotType t : ShotType.values()) {
            assertEquals(config.shotShare(t) / total,
                    average.shotTypeWeight(t) / totalWeight(average), 1e-9,
                    t + "'s weight at an average player must be its raw share, normalized");
        }
    }

    private double totalWeight(PlayerGameState p) {
        double total = 0;
        for (ShotType t : ShotType.values()) {
            total += p.shotTypeWeight(t);
        }
        return total;
    }

    private boolean isInterior(ShotType t) {
        return t == ShotType.DRIVE || t == ShotType.POST;
    }
}
