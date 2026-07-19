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

    // --- §3.4 chemistry make-multiplier (Decision C) ---

    @Test
    void chemistryMultiplierAboveOneRaisesMakeRate() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        int trials = 10_000;
        int neutralMakes = 0;
        int boostedMakes = 0;
        RandomGenerator r1 = rng(42);
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < trials; i++) {
            if (resolver.isMade(ShotType.PERIMETER, shooter, defender, 1.0, r1)) neutralMakes++;
            if (resolver.isMade(ShotType.PERIMETER, shooter, defender, 1.15, r2)) boostedMakes++;
        }
        assertTrue(boostedMakes > neutralMakes,
                "A >1 chemistry multiplier should raise the make rate: boosted=" + boostedMakes
                        + " neutral=" + neutralMakes);
    }

    @Test
    void chemistryMultiplierOfOneMatchesUnmodified() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 13.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 9.0);
        assertEquals(
                resolver.isMade(ShotType.POST, shooter, defender, rng(55)),
                resolver.isMade(ShotType.POST, shooter, defender, 1.0, rng(55)),
                "Multiplier 1.0 must equal the unmodified overload for the same seed");
    }

    // --- §3.7 block contest (decisions.md #025 A1, B2, C) ---

    // A shooter with an explicit finishing skill (all else league-average). finishing
    // is the 2nd skill in the 13-skill overload.
    private PlayerGameState shooterWithFinishing(String id, double finishing) {
        return TestPlayerFactory.create(id, "A",
                10, finishing, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
    }

    // A defender with explicit rimProtection (10th) + shotContest (11th) skills.
    private PlayerGameState defenderWithBlockSkills(String id, double rimProtection,
                                                    double shotContest) {
        return TestPlayerFactory.create(id, "B",
                10, 10, 10, 10, 10, 10, 10, 10, 10, rimProtection, shotContest, 10, 10);
    }

    private double blockRate(ShotType type, PlayerGameState shooter,
                             PlayerGameState defender, long seed, int trials) {
        int blocks = 0;
        RandomGenerator r = rng(seed);
        for (int i = 0; i < trials; i++) {
            if (resolver.isBlocked(type, shooter, defender, r)) blocks++;
        }
        return (double) blocks / trials;
    }

    @Test
    void averageVsAverageBlockRateNearBase() {
        PlayerGameState shooter = shooterWithFinishing("s1", 10);
        PlayerGameState defender = defenderWithBlockSkills("d1", 10, 10);
        double rate = blockRate(ShotType.DRIVE, shooter, defender, 42, 20_000);
        assertEquals(SimConfig.BASE_BLOCK_DRIVE, rate, 0.02,
                "average-vs-average block rate should land near the base rate");
    }

    @Test
    void eliteRimProtectorVsWeakFinisherBlocksMore() {
        PlayerGameState weakFinisher = shooterWithFinishing("s1", 3);
        PlayerGameState scrubFinisher = shooterWithFinishing("s2", 10);
        PlayerGameState eliteProtector = defenderWithBlockSkills("d1", 20, 10);
        PlayerGameState avgProtector = defenderWithBlockSkills("d2", 10, 10);

        double eliteVsWeak = blockRate(ShotType.DRIVE, weakFinisher, eliteProtector, 7, 20_000);
        double avgVsAvg = blockRate(ShotType.DRIVE, scrubFinisher, avgProtector, 7, 20_000);
        assertTrue(eliteVsWeak > avgVsAvg,
                "elite rim protector vs weak finisher should block more: elite=" + eliteVsWeak
                        + " avg=" + avgVsAvg);
    }

    @Test
    void eliteFinisherGetsBlockedLessThanScrub() {
        PlayerGameState eliteFinisher = shooterWithFinishing("s1", 20);
        PlayerGameState scrubFinisher = shooterWithFinishing("s2", 3);
        PlayerGameState protector = defenderWithBlockSkills("d1", 16, 10);

        double eliteRate = blockRate(ShotType.DRIVE, eliteFinisher, protector, 11, 20_000);
        double scrubRate = blockRate(ShotType.DRIVE, scrubFinisher, protector, 11, 20_000);
        assertTrue(eliteRate < scrubRate,
                "a great finisher gets blocked less than a scrub vs the same protector: elite="
                        + eliteRate + " scrub=" + scrubRate);
    }

    @Test
    void threesAreBlockedFarLessThanRimAttempts() {
        PlayerGameState shooter = shooterWithFinishing("s1", 10);
        PlayerGameState defender = defenderWithBlockSkills("d1", 10, 10);
        double driveRate = blockRate(ShotType.DRIVE, shooter, defender, 5, 20_000);
        double threeRate = blockRate(ShotType.THREE, shooter, defender, 5, 20_000);
        assertTrue(threeRate < driveRate,
                "threes should be blocked far less than drives: three=" + threeRate
                        + " drive=" + driveRate);
        assertTrue(threeRate > 0.0,
                "threes are very-low but not unblockable (Decision C): " + threeRate);
    }

    @Test
    void rimProtectionDrivesRimBlocksShotContestDrivesJumperBlocks() {
        // A rim-only specialist (high rimProtection, avg shotContest) blocks DRIVE/POST
        // harder; a contest-only specialist (avg rimProtection, high shotContest) blocks
        // PERIMETER/THREE harder — confirming the per-shot-type defender skill mapping.
        PlayerGameState shooter = shooterWithFinishing("s1", 10);
        PlayerGameState rimSpecialist = defenderWithBlockSkills("dR", 20, 10);
        PlayerGameState contestSpecialist = defenderWithBlockSkills("dC", 10, 20);

        double rimGuyOnDrive = blockRate(ShotType.DRIVE, shooter, rimSpecialist, 3, 20_000);
        double contestGuyOnDrive = blockRate(ShotType.DRIVE, shooter, contestSpecialist, 3, 20_000);
        assertTrue(rimGuyOnDrive > contestGuyOnDrive,
                "rimProtection should drive DRIVE blocks: rim=" + rimGuyOnDrive
                        + " contest=" + contestGuyOnDrive);

        double contestGuyOnPerimeter = blockRate(ShotType.PERIMETER, shooter, contestSpecialist, 3, 20_000);
        double rimGuyOnPerimeter = blockRate(ShotType.PERIMETER, shooter, rimSpecialist, 3, 20_000);
        assertTrue(contestGuyOnPerimeter > rimGuyOnPerimeter,
                "shotContest should drive PERIMETER blocks: contest=" + contestGuyOnPerimeter
                        + " rim=" + rimGuyOnPerimeter);
    }

    @Test
    void tiredDefenderBlocksLess() {
        // Drain the defender's energy far down; a fatigued defender contests worse, so
        // its effective block skill (and block rate) drops vs a fresh copy.
        PlayerGameState shooter = shooterWithFinishing("s1", 10);
        PlayerGameState freshDefender = defenderWithBlockSkills("dFresh", 18, 18);
        PlayerGameState tiredDefender = defenderWithBlockSkills("dTired", 18, 18);
        for (int i = 0; i < 200; i++) {
            tiredDefender.drainForPossession();
        }
        assertTrue(tiredDefender.fatigueFactor() < 1.0, "defender should be fatigued");

        double freshRate = blockRate(ShotType.DRIVE, shooter, freshDefender, 21, 20_000);
        double tiredRate = blockRate(ShotType.DRIVE, shooter, tiredDefender, 21, 20_000);
        assertTrue(tiredRate < freshRate,
                "a tired defender should block less: tired=" + tiredRate + " fresh=" + freshRate);
    }

    @Test
    void blockDeterministicWithSameSeed() {
        PlayerGameState shooter = shooterWithFinishing("s1", 9);
        PlayerGameState defender = defenderWithBlockSkills("d1", 14, 12);
        assertEquals(
                resolver.isBlocked(ShotType.POST, shooter, defender, rng(321)),
                resolver.isBlocked(ShotType.POST, shooter, defender, rng(321)),
                "same seed ⇒ same block result");
    }

    @ParameterizedTest
    @EnumSource(ShotType.class)
    void baseBlockProbabilityPositiveForAllTypes(ShotType type) {
        double base = resolver.baseBlockProbability(type);
        assertTrue(base > 0 && base < 1, "block base rate out of range for " + type + ": " + base);
    }

    @Test
    void baseBlockRateOrderingHoldsAcrossShotTypes() {
        // Decision C: DRIVE ≥ POST > PERIMETER ≫ THREE.
        double drive = resolver.baseBlockProbability(ShotType.DRIVE);
        double post = resolver.baseBlockProbability(ShotType.POST);
        double perimeter = resolver.baseBlockProbability(ShotType.PERIMETER);
        double three = resolver.baseBlockProbability(ShotType.THREE);
        assertTrue(drive >= post, "DRIVE ≥ POST");
        assertTrue(post > perimeter, "POST > PERIMETER");
        assertTrue(perimeter > three, "PERIMETER ≫ THREE");
    }
}
