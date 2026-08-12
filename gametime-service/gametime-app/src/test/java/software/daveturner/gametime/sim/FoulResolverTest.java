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

    // §3.12 (decisions.md #030 A1) RE-BASELINED, not deleted: these two used to
    // assert that a PERIMETER and a THREE could NEVER be fouled — the binary
    // isContactType gate. That gate is gone and the premise is now false by design
    // (a closeout on a three-point shooter is a real foul), so each is inverted to
    // the invariant that replaced it: a jump shot fouls RARELY, not never.

    @Test
    void perimeterFoulsAreUncommonButPossible() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        RandomGenerator r = rng(42);
        int fouls = 0;
        for (int i = 0; i < 10_000; i++) {
            if (resolver.isFoul(ShotType.PERIMETER, shooter, defender, r)) fouls++;
        }
        assertTrue(fouls > 0, "A mid-range jumper CAN now be fouled (§3.12)");
        assertTrue(fouls < 10_000 / 2,
                "...but a perimeter foul stays uncommon, even at this skill gap: " + fouls);
    }

    @Test
    void threeFoulsAreRareButPossible() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        RandomGenerator r = rng(42);
        int fouls = 0;
        for (int i = 0; i < 10_000; i++) {
            if (resolver.isFoul(ShotType.THREE, shooter, defender, r)) fouls++;
        }
        assertTrue(fouls > 0, "A three CAN now be fouled — and it awards 3 FTs (§3.12)");
        assertTrue(fouls < 10_000 / 4,
                "...but a fouled three is the RAREST of the four types: " + fouls);
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
    void pressureScalesJumpShotFoulsToo() {
        // §3.12 (#030 A1) RE-BASELINED: this asserted that even max pressure could
        // not foul on a three (the isContactType gate). Now it can, so the surviving
        // invariant is the one that actually matters — defensivePressure scales the
        // jump-shot foul rate the same way it scales a drive's, since the multiplier
        // is applied to the same contest rather than replacing it.
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 20.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 1.0);
        int neutral = 0;
        RandomGenerator r1 = rng(42);
        for (int i = 0; i < 10_000; i++) {
            if (resolver.isFoul(ShotType.THREE, shooter, defender, 1.0, r1)) neutral++;
        }
        int pressured = 0;
        RandomGenerator r2 = rng(42);
        for (int i = 0; i < 10_000; i++) {
            if (resolver.isFoul(ShotType.THREE, shooter, defender, 2.0, r2)) pressured++;
        }
        assertTrue(pressured > neutral,
                "Aggressive defense must concede more fouls on threes too: pressured="
                        + pressured + " neutral=" + neutral);
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

    // --- §3.11 and-1 (decisions.md #029 A1/C) -------------------------------

    /** A player whose only non-average skills are foulDrawing and foulProne. */
    private PlayerGameState contactPlayer(String id, String teamId,
                                          double foulDrawing, double foulProne) {
        return TestPlayerFactory.create(id, teamId, 10.0, 10.0, 10.0, 10.0, 10.0,
                10.0, 10.0, foulDrawing, 10.0, 10.0, 10.0, 10.0, foulProne);
    }

    /**
     * §3.12: defaults to DRIVE, whose FOUL_MULT_DRIVE = 1.0 anchor makes the rate
     * numerically identical to §3.11's — so the pre-existing and-1 assertions below
     * keep testing exactly what they tested before the multiplier was introduced.
     */
    private double andOneRate(PlayerGameState shooter, PlayerGameState defender,
                              double defensivePressure, long seed, int trials) {
        return andOneRate(ShotType.DRIVE, shooter, defender, defensivePressure, seed, trials);
    }

    private double andOneRate(ShotType shotType, PlayerGameState shooter,
                              PlayerGameState defender, double defensivePressure,
                              long seed, int trials) {
        RandomGenerator r = rng(seed);
        int hits = 0;
        for (int i = 0; i < trials; i++) {
            if (resolver.isAndOne(shotType, shooter, defender, defensivePressure, r)) hits++;
        }
        return hits / (double) trials;
    }

    @Test
    void andOneCanTriggerAtAverageSkill() {
        // The base case: an average shooter against an average defender draws
        // and-1s at roughly AND_ONE_BASE. This is the thin slice carved off the
        // top of made contact shots (#029 A1) — it must actually fire.
        double rate = andOneRate(contactPlayer("s1", "A", 10.0, 10.0),
                contactPlayer("d1", "B", 10.0, 10.0), 1.0, 42, 20_000);
        assertEquals(SimConfig.AND_ONE_BASE, rate, 0.02,
                "At avg-vs-avg the and-1 rate should sit near AND_ONE_BASE");
    }

    @Test
    void betterFoulDrawingRaisesTheAndOneRate() {
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        double weak = andOneRate(contactPlayer("s1", "A", 2.0, 10.0), defender, 1.0, 7, 20_000);
        double strong = andOneRate(contactPlayer("s2", "A", 18.0, 10.0), defender, 1.0, 7, 20_000);
        assertTrue(strong > weak,
                "A shooter who draws contact should convert more and-1s (" + strong
                        + " vs " + weak + ")");
    }

    @Test
    void anUndisciplinedDefenderConcedesMoreAndOnes() {
        // foulProne is INVERTED into discipline: high foulProne = fouls more.
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        double disciplined = andOneRate(shooter, contactPlayer("d1", "B", 10.0, 2.0),
                1.0, 11, 20_000);
        double hacker = andOneRate(shooter, contactPlayer("d2", "B", 10.0, 18.0),
                1.0, 11, 20_000);
        assertTrue(hacker > disciplined,
                "A low-discipline defender should concede more and-1s (" + hacker
                        + " vs " + disciplined + ")");
    }

    @Test
    void defensivePressureScalesTheAndOneRate() {
        // §3.4/coach.md: an aggressive scheme concedes more contact — the same
        // pressure/breakdown trade-off the other foul rolls carry.
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        double passive = andOneRate(shooter, defender, 0.8, 5, 20_000);
        double aggressive = andOneRate(shooter, defender, 1.2, 5, 20_000);
        assertTrue(aggressive > passive,
                "Higher defensivePressure must concede more and-1s (" + aggressive
                        + " vs " + passive + ")");
    }

    @Test
    void andOneBaseIsTunableDownwardAndCanBeSwitchedOff() {
        // The §3.11 twin of reboundFoulBaseIsTunableDownward — the same PROB_FLOOR
        // regression guard (#028 C impl note), asserted against the and-1's OWN
        // sensitivity. §3.11's recalibration (#029 E) may need to turn this rate
        // DOWN, which clampProbability's 0.02 floor would have silently prevented.
        double tiny = config.rareEventProbability(0.001, 10.0, 10.0,
                SimConfig.AND_ONE_SENSITIVITY);
        assertEquals(0.001, tiny, 1e-9,
                "An and-1 base below PROB_FLOOR must NOT be floored up to it");
        assertEquals(0.0, config.rareEventProbability(0.0, 10.0, 10.0,
                SimConfig.AND_ONE_SENSITIVITY), 1e-9,
                "A zero base must yield zero at an EVEN contest");
    }

    @Test
    void aZeroBaseStillLeavesASkillDrivenAndOneTail() {
        // An honest boundary on the "switched off" claim, measured rather than
        // assumed (it cost a confusing baseline run to find). rareEventProbability
        // is base + sensitivity × (driving − opposing)/10, so a ZERO base is only
        // zero at an EVEN contest: whenever the shooter's fatigue-scaled
        // foulDrawing exceeds the defender's fatigue-scaled discipline, the skill
        // term alone keeps the rate positive. Zeroing AND_ONE_BASE therefore makes
        // and-1s rare, NOT impossible — the same shape §3.10's rebound foul has.
        //
        // §3.12 (#030 A1): the caller's gate that USED to be the real off-switch
        // (isContactType in PossessionEngine, §3.11's fence around PERIMETER/THREE)
        // is DELETED — every shot type rolls now. The one true off-switch left is a
        // ZERO MULTIPLIER (FOUL_MULT_* = 0.0), which scales the whole probability
        // including this skill term, unlike the zero BASE tested here. That
        // contrast is the point of this test: see
        // aZeroMultiplierIsTheOneTrueOffSwitchForAShotType below.
        double favourable = config.rareEventProbability(0.0, 14.0, 10.0,
                SimConfig.AND_ONE_SENSITIVITY);
        assertTrue(favourable > 0.0,
                "A zero base still leaves a skill-driven tail: " + favourable);
        // ...and the tail stays small, so a zeroed base is a meaningful floor.
        assertTrue(favourable < 0.05,
                "The zero-base tail must stay thin: " + favourable);
    }

    @Test
    void andOneSensitivityStaysGentleEnoughForTheBaseToDominate() {
        // The §3.7 blocks / §3.10 rebound-foul finding, twice discovered: the
        // global SENSITIVITY (0.5) swamps a thin base, letting skill alone drive
        // the rate several-fold over target. Guard that the and-1 dial is its own
        // and stays far below it (#029 C).
        assertTrue(SimConfig.AND_ONE_SENSITIVITY < SimConfig.SENSITIVITY / 2,
                "AND_ONE_SENSITIVITY must stay well below the global SENSITIVITY");
        // The bound that matters is ABSOLUTE, not a multiple of the base: the skill
        // term is sensitivity × (Δskill)/10, so it is independent of the base, and
        // a thinner base makes the SAME small swing a larger multiple. (Asserting a
        // multiple would spuriously fail every time the base is tuned down — which
        // is exactly what §3.11's recalibration did, 0.11 → 0.055.) What must hold
        // is that even a max mismatch leaves the and-1 a RARE event.
        double extreme = config.rareEventProbability(SimConfig.AND_ONE_BASE, 20.0, 0.0,
                SimConfig.AND_ONE_SENSITIVITY);
        assertTrue(extreme < 0.30,
                "Even a max-skill mismatch must leave the and-1 rare: " + extreme);
        // And the global sensitivity would NOT: it is the trap §3.7/§3.10 hit twice.
        double withGlobal = config.rareEventProbability(SimConfig.AND_ONE_BASE, 20.0, 0.0,
                SimConfig.SENSITIVITY);
        assertTrue(withGlobal > 3 * extreme,
                "The global SENSITIVITY would swamp a thin base — hence our own dial");
    }

    @Test
    void andOneIsDeterministicForASeed() {
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        assertEquals(resolver.isAndOne(ShotType.DRIVE, shooter, defender, 1.0, rng(2024)),
                resolver.isAndOne(ShotType.DRIVE, shooter, defender, 1.0, rng(2024)),
                "Same seed must give the same and-1 result");
    }

    // --- §3.12 all-shot-type contact fouls (decisions.md #030) ---------------

    @Test
    void everyShotTypeCanNowDrawAFoul() {
        // The crux of §3.12 (#030 A1). Before this pass isFoul early-returned false
        // for PERIMETER and THREE — they could not be fouled at ANY rate. Now the
        // graduated multiplier replaces the gate, so all four must fire.
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        for (ShotType type : ShotType.values()) {
            assertTrue(foulRate(type, shooter, defender, 7, 20_000) > 0.0,
                    type + " must be able to draw a shooting foul after §3.12");
        }
    }

    @Test
    void everyShotTypeCanNowDrawAnAndOne() {
        // The same widening on the post-make roll — PossessionEngine's
        // isContactType gate is gone, so a made three can draw an and-1 too.
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        for (ShotType type : ShotType.values()) {
            assertTrue(andOneRate(type, shooter, defender, 1.0, 7, 40_000) > 0.0,
                    type + " must be able to draw an and-1 after §3.12");
        }
    }

    @Test
    void foulRateIsGraduatedDriveMostThreeLeast() {
        // The ORDERING is the model (#030 A1/A2): post/drive frequent → perimeter
        // uncommon → three rare. At EQUAL skill, so the ordering can only come from
        // the multiplier table, not from the contest.
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        double drive = foulRate(ShotType.DRIVE, shooter, defender, 11, 40_000);
        double post = foulRate(ShotType.POST, shooter, defender, 11, 40_000);
        double perimeter = foulRate(ShotType.PERIMETER, shooter, defender, 11, 40_000);
        double three = foulRate(ShotType.THREE, shooter, defender, 11, 40_000);

        assertEquals(drive, post, 0.02, "DRIVE and POST share the 1.0 anchor");
        assertTrue(perimeter < drive,
                "A perimeter jumper must foul less than a drive: " + perimeter + " vs " + drive);
        assertTrue(three < perimeter,
                "A three must foul less than a mid-range jumper: " + three + " vs " + perimeter);
        assertTrue(three > 0.0, "...but a three is RARE, not impossible");
    }

    @Test
    void drivePostFoulRatesAreUnchangedFromSection311() {
        // #030 A2's attributability guarantee, pinned. The multiplier table anchors
        // DRIVE/POST at 1.0 precisely so their behavior is numerically IDENTICAL to
        // §3.11 — which is what makes §3.12's entire harness delta attributable to
        // perimeter/three. If someone retunes FOUL_MULT_DRIVE off 1.0, this fails.
        assertEquals(1.0, SimConfig.FOUL_MULT_DRIVE, 1e-9,
                "DRIVE is the anchor — raising it reopens a §3.4-calibrated number");
        assertEquals(1.0, SimConfig.FOUL_MULT_POST, 1e-9,
                "POST passed the same gate as DRIVE in §3.11, so it stays 1.0");

        // And the realized rate matches the un-multiplied §3.11 computation.
        PlayerGameState shooter = contactPlayer("s1", "A", 12.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 13.0);
        double expected = config.clampProbability(config.contestProbability(
                SimConfig.BASE_NO_BASKET_FOUL,
                shooter.getFoulDrawing() * shooter.fatigueFactor(),
                (SimConfig.SCALE_AVG * 2 - defender.getFoulProne()) * defender.fatigueFactor()));
        assertEquals(expected, foulRate(ShotType.DRIVE, shooter, defender, 99, 40_000), 0.01,
                "The DRIVE foul rate must still be BASE_NO_BASKET_FOUL's contest, unscaled");
    }

    @Test
    void aFouledThreeIsRareButLandsNearItsIntendedRate() {
        // #030 G: FOUL_MULT_THREE encodes the STOPPED-three rate (~2% of 3PA), the
        // benchmark the user agreed. Anchor on the RATE, never the trip count —
        // this engine shoots fewer 3PA than the NBA, so the same rate yields fewer
        // trips. Asserted as a band so harness retuning within the agreed range
        // does not break the build.
        PlayerGameState shooter = contactPlayer("s1", "A", 10.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 10.0);
        double three = foulRate(ShotType.THREE, shooter, defender, 5, 60_000);
        assertEquals(SimConfig.BASE_NO_BASKET_FOUL * SimConfig.FOUL_MULT_THREE, three, 0.01,
                "A stopped three should land near BASE_NO_BASKET_FOUL × FOUL_MULT_THREE");
        assertTrue(three < 0.05,
                "A fouled three must stay rare (well under the perimeter rate): " + three);
    }

    @Test
    void aZeroMultiplierIsTheOneTrueOffSwitchForAShotType() {
        // The contrast with aZeroBaseStillLeavesASkillDrivenAndOneTail above, and
        // the reason #030 A1 could delete the caller's gate. A zero BASE leaves a
        // skill-driven tail (the skill term is additive); a zero MULTIPLIER scales
        // the WHOLE probability, skill term included, so it genuinely reaches zero
        // — even at a lopsided contest that maximally favours the shooter.
        //
        // Verified against the arithmetic rather than a config edit: the constants
        // are final, so this pins the property the multiply must have.
        PlayerGameState shooter = contactPlayer("s1", "A", 20.0, 10.0);
        PlayerGameState defender = contactPlayer("d1", "B", 10.0, 20.0);
        double contested = config.contestProbability(SimConfig.BASE_NO_BASKET_FOUL,
                shooter.getFoulDrawing() * shooter.fatigueFactor(),
                (SimConfig.SCALE_AVG * 2 - defender.getFoulProne()) * defender.fatigueFactor());
        assertTrue(contested > 0.0, "the un-multiplied contest is positive here");
        assertEquals(0.0, 0.0 * contested, 1e-12,
                "A 0.0 multiplier must zero the whole probability, skill term included");

        // ...whereas a zero BASE at the same lopsided contest does NOT reach zero.
        assertTrue(config.rareEventProbability(0.0, 14.0, 10.0,
                        SimConfig.AND_ONE_SENSITIVITY) > 0.0,
                "a zero base leaves a tail — which is why the OFF-switch is the multiplier");
    }

    @Test
    void theMultiplierIsAnchoredNotAProbability() {
        // The units hazard #030 records: these constants sit beside
        // BASE_NO_BASKET_FOUL = 0.15 where everything LOOKS like a probability.
        // FOUL_MULT_THREE = 0.133 means "13.3% of the drive rate" (⇒ ~2%), not
        // "13.3% of threes are fouled". Pin the arithmetic so the meaning is
        // executable, not just documented.
        assertEquals(0.15 * SimConfig.FOUL_MULT_THREE,
                SimConfig.BASE_NO_BASKET_FOUL * config.foulMultiplier(ShotType.THREE), 1e-9);
        assertTrue(SimConfig.BASE_NO_BASKET_FOUL * SimConfig.FOUL_MULT_THREE < 0.03,
                "The THREE multiplier must resolve to a ~2% foul rate, not a 13% one");
        for (ShotType type : ShotType.values()) {
            assertTrue(config.foulMultiplier(type) > 0.0 && config.foulMultiplier(type) <= 1.0,
                    type + " multiplier must sit in (0, 1] with DRIVE as the 1.0 anchor");
        }
    }

    /** §3.12: the realized stopped-shot foul rate for a shot type over many trials. */
    private double foulRate(ShotType shotType, PlayerGameState shooter,
                            PlayerGameState defender, long seed, int trials) {
        RandomGenerator r = rng(seed);
        int hits = 0;
        for (int i = 0; i < trials; i++) {
            if (resolver.isFoul(shotType, shooter, defender, 1.0, r)) hits++;
        }
        return hits / (double) trials;
    }

    // ---------- §3.14b: the flagrant severity roll (decisions.md #034 A/E) ----------

    /**
     * #034 A/G: the realized flagrant rate over many trials lands on the configured
     * per-foul probability — the roll is a plain rate check, with no contest.
     */
    @Test
    void theFlagrantRollFiresAtTheConfiguredPerFoulRate() {
        RandomGenerator r = rng(7);
        int trials = 200_000;
        int hits = 0;
        for (int i = 0; i < trials; i++) {
            if (resolver.isFlagrant(r)) hits++;
        }
        double observed = hits / (double) trials;
        assertEquals(config.flagrantFoulProbability(), observed, 0.001,
                "The flagrant roll must realize the configured ~0.0084 per-foul rate");
    }

    /**
     * #034 E: the severity sub-roll realizes the flat 15% conditional share — the
     * fraction of flagrants that are flagrant-2.
     */
    @Test
    void theSeverityRollFiresAtTheFlatFifteenPercentShare() {
        RandomGenerator r = rng(11);
        int trials = 100_000;
        int hits = 0;
        for (int i = 0; i < trials; i++) {
            if (resolver.isFlagrantTwo(r)) hits++;
        }
        assertEquals(SimConfig.FLAGRANT_TWO_SHARE, hits / (double) trials, 0.005,
                "~15% of flagrants are flagrant-2 (#034 E)");
    }

    /**
     * #034 A/E, the design claim stated as a test: <b>neither roll takes any skill
     * input</b>. The engine cannot distinguish excessive from ordinary contact, and
     * foulProne has already had its say in who was selected as committer — so weighting
     * the grade would apply one signal twice.
     *
     * <p>Asserted structurally (the methods take only a RandomGenerator) because the
     * symmetry with §3.14a's foulProne-weighted committer draw (#032 C) is tempting and
     * wrong: that weighted a SELECTION, this is a GRADE on a player already selected.
     */
    @Test
    void neitherFlagrantRollTakesAnySkillInput() throws Exception {
        assertArrayEquals(new Class<?>[] { RandomGenerator.class },
                FoulResolver.class.getMethod("isFlagrant", RandomGenerator.class)
                        .getParameterTypes(),
                "isFlagrant must take no PlayerGameState — causally inert by design");
        assertArrayEquals(new Class<?>[] { RandomGenerator.class },
                FoulResolver.class.getMethod("isFlagrantTwo", RandomGenerator.class)
                        .getParameterTypes(),
                "isFlagrantTwo must take no PlayerGameState either");
    }

    /**
     * #034 A: the flagrant is layered ON TOP of the existing foul rolls, so those rolls
     * must be untouched — same rate, same inputs, same single draw. This pins the
     * "no existing rate moves by construction" property that makes the pass free.
     */
    @Test
    void theExistingFoulRollsAreUnchangedByTheFlagrantLayer() {
        PlayerGameState shooter = TestPlayerFactory.create("s1", "A", 10.0);
        PlayerGameState defender = TestPlayerFactory.create("d1", "B", 10.0);
        // One draw per isFoul call: two generators at the same seed must agree
        // step-for-step, which they cannot if isFoul had started consuming a second.
        RandomGenerator a = rng(3);
        RandomGenerator b = rng(3);
        for (int i = 0; i < 500; i++) {
            assertEquals(resolver.isFoul(ShotType.DRIVE, shooter, defender, 1.0, a),
                    resolver.isFoul(ShotType.DRIVE, shooter, defender, 1.0, b),
                    "isFoul's RNG consumption must be unchanged by §3.14b");
        }
    }
}
