package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import software.daveturner.gametime.model.LineupRole;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §3.5 (decisions.md #023) + §3.13 (decisions.md #031): the rotation manager —
 * energy drain/recover, the between-possession substitution check
 * (starter-priority, rotationDepth-gated, rested-return), foul-out forced subs,
 * the never-below-5 invariant, and §3.13's soft foul-trouble sub.
 *
 * <p>§3.13 made the step RNG-consuming (one unconditional draw per call, revising
 * #023 C). The §3.5 tests below pass a roll that never fires the foul-trouble rule
 * ({@link #rng()} — always 1.0) so they still exercise fatigue/foul-out behavior in
 * isolation; the §3.13 tests drive the roll explicitly.
 */
class RotationStateTest {

    private final SimConfig config = SimConfig.baseline();

    /** A generator whose nextDouble() is always 1.0 — no foul-trouble sub ever fires. */
    private static RandomGenerator rng() {
        return fixedRng(1.0);
    }

    /**
     * A generator returning a fixed nextDouble(), so the foul-trouble sub's
     * probability gate is exercised deterministically: 0.0 fires whenever the
     * computed probability is positive, 1.0 never fires.
     */
    private static RandomGenerator fixedRng(double value) {
        return new RandomGenerator() {
            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public long nextLong() {
                return 0L;
            }
        };
    }

    private PlayerGameState player(String id, LineupRole role, Integer rotationOrder,
                                   int endurance, int energy) {
        return TestPlayerFactory.createRotationPlayer(id, "T", 10.0, role, rotationOrder,
                endurance, energy);
    }

    /** A squad of 5 starters + n bench players (rotationOrder 1..n), all avg. */
    private List<PlayerGameState> squad(int benchCount) {
        List<PlayerGameState> squad = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            squad.add(player("S" + i, LineupRole.STARTER, null, 10, 10));
        }
        for (int i = 1; i <= benchCount; i++) {
            squad.add(player("B" + i, LineupRole.ROTATION, i, 10, 10));
        }
        return squad;
    }

    private RotationState rotation(List<PlayerGameState> squad, CoachModifiers mods) {
        return new RotationState(squad, mods, config);
    }

    // --- Construction / invariant ---

    @Test
    void onFloorIsTheFiveStartersAtTipoff() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        assertEquals(5, r.onFloor().size());
        assertTrue(r.onFloor().containsAll(squad.subList(0, 5)),
                "The five starters start on the floor");
    }

    @Test
    void requiresAtLeastFivePlayers() {
        List<PlayerGameState> tooFew = squad(0).subList(0, 4);
        assertThrows(IllegalArgumentException.class,
                () -> rotation(new ArrayList<>(tooFew), CoachModifiers.neutral()));
    }

    @Test
    void onFloorStaysExactlyFiveAcrossManyPossessions() {
        RotationState r = rotation(squad(6), CoachModifiers.neutral());
        for (int i = 0; i < 500; i++) {
            r.advancePossession(rng());
            assertEquals(5, r.onFloor().size(), "on-floor must always be exactly 5");
        }
    }

    // --- §3.20: tookFloor vs onFloorPossessions ---

    /**
     * §3.20: <b>a substituted-in player is marked as having TAKEN THE FLOOR
     * immediately, before any possession is drained against him.</b>
     *
     * <p>⚠ <b>This is the regression guard for a shipped bug.</b> {@code
     * GameSimulator} wrote a box-score row only for players with {@code
     * onFloorPossessions > 0}, treating that as "checked in". But {@link
     * RotationState#advancePossession} drains for the five on the floor at the TOP of
     * the call and substitutes AFTERWARDS, so a player subbed in on possession N has
     * nothing drained against him yet — and if the game ended, or he was subbed back
     * out, his counter stayed 0. <b>If he had SCORED in that window his row was never
     * written and his points vanished from the box score</b> (measured: box 237
     * against an event log and final score that both read 240).
     *
     * <p>⚠ <b>The two facts are deliberately kept SEPARATE.</b> {@code
     * onFloorPossessions} is the minutes DENOMINATOR and must stay a pure possession
     * count — inflating it to answer "did he play?" would fix the box score and
     * corrupt minutes instead. Hence {@link PlayerGameState#tookFloor()}.
     */
    @Test
    void aSubstitutedInPlayerHasTakenTheFloorBeforeAnyPossessionIsDrained() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());

        // The five starters have taken the floor at tipoff, before any drain.
        for (PlayerGameState p : r.onFloor()) {
            assertTrue(p.tookFloor(), "a starter has taken the floor at tipoff");
            assertEquals(0, p.getOnFloorPossessions(),
                    "…and has had NO possession drained against him yet — which is "
                            + "exactly why the two facts cannot be the same field");
        }

        // ⚠ CHECK AFTER EVERY SINGLE POSSESSION, NOT AT THE END. The bug's window is
        // exactly ONE possession wide: a player subbed in at the end of possession N
        // is marked by his own drain on possession N+1, so a check at the end of a
        // long loop sees him already correct and proves NOTHING. The box score is
        // written when the GAME ENDS, which can be immediately after that swap.
        boolean sawFreshSub = false;
        for (int i = 0; i < 60; i++) {
            r.advancePossession(rng());
            for (PlayerGameState p : r.onFloor()) {
                if (p.getOnFloorPossessions() == 0) {
                    sawFreshSub = true;   // on the floor, nothing drained yet
                }
                assertTrue(p.tookFloor(),
                        "a player ON THE FLOOR must be marked as having taken it "
                                + "THE MOMENT he is substituted in — if the game ended "
                                + "here his box-score row would be dropped along with "
                                + "any points he just scored. Player " + p.getPlayerId()
                                + " (onFloorPossessions=" + p.getOnFloorPossessions()
                                + ", possession " + i + ")");
            }
        }

        // The test is only meaningful if a substitution actually happened and left
        // someone on the floor with a zero count — the exact bug condition.
        assertTrue(sawFreshSub,
                "no just-substituted player was ever observed on the floor with a "
                        + "zero possession count — this test did not exercise the bug");

        // And the invariant that protects the box score: nobody can have drained a
        // possession without being marked.
        for (PlayerGameState p : squad) {
            if (p.getOnFloorPossessions() > 0) {
                assertTrue(p.tookFloor(),
                        "drained a possession but not marked: " + p.getPlayerId());
            }
        }
    }

    /**
     * §3.20: a player who never leaves the bench is NOT marked — the flag must not
     * simply be true for everyone, or the box score gains phantom rows.
     */
    @Test
    void aPlayerWhoNeverChecksInHasNotTakenTheFloor() {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState deepBench = squad.get(squad.size() - 1);

        r.advancePossession(rng());

        assertFalse(deepBench.tookFloor(),
                "a deep-bench player who never checked in has not taken the floor");
        assertEquals(0, deepBench.getOnFloorPossessions());
    }

    // --- Energy drain / recovery ---

    @Test
    void onFloorPlayersDrainAndBenchRecovers() {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter = squad.get(0);
        PlayerGameState benchDeep = squad.get(squad.size() - 1); // never subs in early

        double startEnergy = starter.getCurrentEnergy();
        r.advancePossession(rng());
        assertTrue(starter.getCurrentEnergy() < startEnergy, "on-floor starter drains");
        // A deep-bench player is at or above full (recovery caps at MAX_ENERGY).
        assertEquals(SimConfig.MAX_ENERGY, benchDeep.getCurrentEnergy(), 0.001);
    }

    @Test
    void higherEnduranceDrainsSlower() {
        PlayerGameState iron = player("iron", LineupRole.STARTER, null, 20, 10);
        PlayerGameState frail = player("frail", LineupRole.STARTER, null, 3, 10);
        List<PlayerGameState> squad = new ArrayList<>(List.of(
                iron, frail,
                player("S3", LineupRole.STARTER, null, 10, 10),
                player("S4", LineupRole.STARTER, null, 10, 10),
                player("S5", LineupRole.STARTER, null, 10, 10)));
        RotationState r = rotation(squad, CoachModifiers.neutral()); // no bench → no subs
        for (int i = 0; i < 10; i++) {
            r.advancePossession(rng());
        }
        assertTrue(iron.getCurrentEnergy() > frail.getCurrentEnergy(),
                "high-endurance player retains more energy: iron=" + iron.getCurrentEnergy()
                        + " frail=" + frail.getCurrentEnergy());
    }

    @Test
    void fatigueFactorFallsWithEnergyButStaysModest() {
        PlayerGameState p = player("p", LineupRole.STARTER, null, 1, 1);
        List<PlayerGameState> squad = new ArrayList<>(List.of(p,
                player("S2", LineupRole.STARTER, null, 10, 10),
                player("S3", LineupRole.STARTER, null, 10, 10),
                player("S4", LineupRole.STARTER, null, 10, 10),
                player("S5", LineupRole.STARTER, null, 10, 10)));
        RotationState r = rotation(squad, CoachModifiers.neutral());
        for (int i = 0; i < 100; i++) {
            r.advancePossession(rng());
        }
        double f = p.fatigueFactor();
        assertTrue(f < 1.0, "a drained player's fatigue factor is below 1.0");
        assertTrue(f >= 1.0 - config.fatigueMaxPenalty() - 1e-9,
                "fatigue is a modest thumb, never below 1 − FATIGUE_MAX_PENALTY: " + f);
    }

    // --- Fatigue substitution ---

    @Test
    void tiredStarterIsEventuallySubbedForFreshBench() {
        List<PlayerGameState> squad = squad(4);
        // A very aggressive coach pulls tired players earlier.
        CoachModifiers aggressive = coachMods(10, 20);
        RotationState r = rotation(squad, aggressive);
        PlayerGameState starter1 = squad.get(0);

        boolean starterWasBenched = false;
        for (int i = 0; i < 200 && !starterWasBenched; i++) {
            r.advancePossession(rng());
            starterWasBenched = !r.onFloor().contains(starter1);
        }
        assertTrue(starterWasBenched, "a tired starter should eventually be subbed out");
    }

    @Test
    void restedPlayerBecomesEligibleToReturn() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState starter1 = squad.get(0);

        // Run long enough that starter1 gets pulled, rests, and returns.
        boolean pulled = false;
        boolean returned = false;
        for (int i = 0; i < 2000; i++) {
            r.advancePossession(rng());
            if (!r.onFloor().contains(starter1)) {
                pulled = true;
            } else if (pulled) {
                returned = true;
                break;
            }
        }
        assertTrue(pulled, "starter should be pulled");
        assertTrue(returned, "a rested starter should return to the floor");
    }

    @Test
    void deeperRotationUsesMoreBenchPlayers() {
        // Tight rotation (low rotationDepth) vs deep rotation (high) over the same
        // run: the deep coach should give minutes to more distinct players.
        int tightPlayers = distinctPlayersUsed(coachMods(1, 20));
        int deepPlayers = distinctPlayersUsed(coachMods(20, 20));
        assertTrue(deepPlayers >= tightPlayers,
                "a deeper rotation uses at least as many players: deep=" + deepPlayers
                        + " tight=" + tightPlayers);
        assertTrue(deepPlayers > 5, "a deep rotation should use bench players");
    }

    private int distinctPlayersUsed(CoachModifiers mods) {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, mods);
        java.util.Set<PlayerGameState> used = new java.util.HashSet<>(r.onFloor());
        for (int i = 0; i < 400; i++) {
            r.advancePossession(rng());
            used.addAll(r.onFloor());
        }
        return used.size();
    }

    // --- Foul-outs (Decision F) ---

    @Test
    void fouledOutPlayerIsForcedOffTheFloor() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter1 = squad.get(0);
        for (int f = 0; f < config.foulOutLimit(); f++) {
            starter1.recordFoul();
        }
        assertTrue(starter1.isFouledOut());

        r.advancePossession(rng());
        assertFalse(r.onFloor().contains(starter1),
                "a fouled-out player must be forced off the floor");
        assertEquals(5, r.onFloor().size());
    }

    @Test
    void fouledOutPlayerNeverReturns() {
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter1 = squad.get(0);
        for (int f = 0; f < config.foulOutLimit(); f++) {
            starter1.recordFoul();
        }
        for (int i = 0; i < 500; i++) {
            r.advancePossession(rng());
            assertFalse(r.onFloor().contains(starter1),
                    "a fouled-out player is permanently ineligible");
        }
    }

    @Test
    void floorNeverDropsBelowFiveWhenWholeRosterFoulsOut() {
        // Degenerate path: a minimal 5-man squad where everyone fouls out. The
        // least-fouled available player must stay on so the floor holds at 5.
        List<PlayerGameState> squad = squad(0); // exactly 5, no bench
        RotationState r = rotation(squad, CoachModifiers.neutral());
        for (PlayerGameState p : squad) {
            for (int f = 0; f < config.foulOutLimit(); f++) {
                p.recordFoul();
            }
        }
        r.advancePossession(rng());
        assertEquals(5, r.onFloor().size(),
                "floor never drops below 5 even when everyone has fouled out");
    }

    @Test
    void forcedSubDrawsFromFullRosterBeyondRotationDepth() {
        // A tight rotation (depth ~1) but many foul-outs must still reach deep bench
        // players — forced subs use the full roster, not the rotationDepth window.
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, coachMods(1, 10)); // tight depth
        // Foul out all five starters.
        for (int i = 0; i < 5; i++) {
            for (int f = 0; f < config.foulOutLimit(); f++) {
                squad.get(i).recordFoul();
            }
        }
        r.advancePossession(rng());
        assertEquals(5, r.onFloor().size());
        for (PlayerGameState p : r.onFloor()) {
            assertFalse(p.isFouledOut(), "forced subs must replace fouled-out starters");
        }
    }

    // --- §3.13 foul trouble (decisions.md #031 B/D/F) ---

    /** A squad whose starters/bench carry an explicit value composite. */
    private List<PlayerGameState> valuedSquad(int benchCount, double starterValue,
                                              double benchValue) {
        List<PlayerGameState> squad = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            squad.add(TestPlayerFactory.createValuedRotationPlayer(
                    "S" + i, "T", 10.0, starterValue, LineupRole.STARTER, null, 10, 10));
        }
        for (int i = 1; i <= benchCount; i++) {
            squad.add(TestPlayerFactory.createValuedRotationPlayer(
                    "B" + i, "T", 10.0, benchValue, LineupRole.ROTATION, i, 10, 10));
        }
        return squad;
    }

    private static void foul(PlayerGameState p, int times) {
        for (int i = 0; i < times; i++) {
            p.recordFoul();
        }
    }

    /**
     * Drain the on-floor five (and rest the bench) so a bench player clears the
     * anti-oscillation freshness margin — the ordinary mid-game state. At tip-off
     * everyone is at MAX_ENERGY, so no replacement is meaningfully fresher and the
     * foul-trouble rule correctly declines to fire.
     */
    private void drainTheFloor(RotationState r, int possessions) {
        for (int i = 0; i < possessions; i++) {
            r.advancePossession(rng());
        }
    }

    @Test
    void foulTroubledPlayerIsBenchedWhenTheRollFires() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState troubled = squad.get(0);
        drainTheFloor(r, 6);
        foul(troubled, 5);

        r.advancePossession(fixedRng(0.0)); // a roll of 0 always fires
        assertFalse(r.onFloor().contains(troubled),
                "a 5-foul player should be benched when the sit roll fires");
        assertEquals(5, r.onFloor().size());
    }

    @Test
    void foulTroubleSubRequiresAFresherReplacement() {
        // The anti-oscillation guard, asserted as an INVARIANT rather than against a
        // particular FOUL_TROUBLE_FRESHNESS_MARGIN value (which is a tuning constant):
        // whenever the rule sits a player, the man replacing him is strictly fresher.
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, coachMods(10, 10));
        PlayerGameState troubled = squad.get(0);
        foul(troubled, 5);

        for (int i = 0; i < 200; i++) {
            List<PlayerGameState> before = new ArrayList<>(r.onFloor());
            boolean wasOn = before.contains(troubled);
            r.advancePossession(fixedRng(0.0));
            if (wasOn && !r.onFloor().contains(troubled)) {
                // He was just swapped out. Compare energies as the rule saw them —
                // i.e. AFTER this possession's drain/recovery, which is the state the
                // guard reads. The man who came in must be strictly fresher.
                for (PlayerGameState p : r.onFloor()) {
                    if (!before.contains(p)) {
                        assertTrue(p.getCurrentEnergy() > troubled.getCurrentEnergy(),
                                "the replacement must be fresher than the player he"
                                        + " replaces: replacement=" + p.getCurrentEnergy()
                                        + " benched=" + troubled.getCurrentEnergy());
                    }
                }
            }
        }
    }

    @Test
    void aPlayerBelowTheFoulTroubleCurveIsNeverBenchedForFouls() {
        // Counts 0-2 are zero probability: no coach benches for 2 fouls, even at the
        // most extreme roll and the most aggressive coach.
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState p = squad.get(0);
        foul(p, 2);

        r.advancePossession(fixedRng(0.0));
        assertTrue(r.onFloor().contains(p),
                "2 fouls must never trigger the foul-trouble rule");
    }

    @Test
    void higherFoulCountsAreBenchedMoreReadily() {
        // The curve must be monotonically increasing over the counts it covers.
        double prev = -1.0;
        for (int f = 3; f < config.foulOutLimit(); f++) {
            double p = config.foulTroubleSitProbability(f, 1.0, SimConfig.SCALE_AVG, false, 5);
            assertTrue(p > prev, "sit probability must rise with foul count at f=" + f);
            prev = p;
        }
    }

    @Test
    void betterPlayersAreProtectedMoreAtTheSameFoulCount() {
        // #031 B: the value factor protects the BEST players MORE — a star at 4 fouls
        // is likelier to sit than a low-value player at 4 fouls.
        double star = config.foulTroubleSitProbability(4, 1.0, 16.0, true, null);
        double scrub = config.foulTroubleSitProbability(4, 1.0, 6.0, true, null);
        assertTrue(star > scrub,
                "a high-value player must be benched more readily: star=" + star
                        + " scrub=" + scrub);
    }

    @Test
    void starProtectionInvertsTheFatigueRuleAndThatIsDeliberate() {
        // The fatigue rule lets STARTERS tolerate MORE fatigue (they are pulled
        // later); the foul-trouble rule pulls the better/starting player SOONER.
        // Pinning the inversion so a later reader does not "fix" it into agreement.
        double starterThreshold = config.subEnergyThreshold(1.0, true);
        double benchThreshold = config.subEnergyThreshold(1.0, false);
        assertTrue(starterThreshold < benchThreshold,
                "fatigue: starters tolerate MORE fatigue (lower threshold)");

        double highValueStarter = config.foulTroubleSitProbability(4, 1.0, 15.0, true, null);
        double lowValueDeepBench = config.foulTroubleSitProbability(4, 1.0, 7.0, false, 4);
        assertTrue(highValueStarter > lowValueDeepBench,
                "foul trouble: the better/starting player is pulled SOONER — the"
                        + " deliberate inverse of the fatigue rule (#031 B). starter="
                        + highValueStarter + " deepBench=" + lowValueDeepBench);
    }

    @Test
    void moreAggressiveCoachesBenchForFoulTroubleEarlier() {
        // Compared at the probability itself, so the coach's OTHER behavior (its
        // fatigue-sub threshold, which also scales with substitutionAggressiveness)
        // cannot confound the measurement by churning the floor.
        double aggressive = config.foulTroubleSitProbability(
                4, coachMods(10, 20).subAggressivenessFactor(), SimConfig.SCALE_AVG, true, null);
        double passive = config.foulTroubleSitProbability(
                4, coachMods(10, 1).subAggressivenessFactor(), SimConfig.SCALE_AVG, true, null);
        assertTrue(aggressive > passive,
                "a high-substitutionAggressiveness coach sits foul-troubled players"
                        + " more readily: aggressive=" + aggressive + " passive=" + passive);
    }

    @Test
    void aggressiveCoachesBenchAtALowerFoulCountThanPassiveOnes() {
        // #031 B's stated intent, expressed as ONE curve scaled by the coach factor:
        // an aggressive coach at 3 fouls is already likelier to sit a player than a
        // passive coach is at the same count, and the aggressive coach's 4-foul
        // probability exceeds the passive coach's.
        double aggressiveFactor = coachMods(10, 20).subAggressivenessFactor();
        double passiveFactor = coachMods(10, 1).subAggressivenessFactor();
        for (int f = 3; f < config.foulOutLimit(); f++) {
            double aggressive = config.foulTroubleSitProbability(
                    f, aggressiveFactor, SimConfig.SCALE_AVG, true, null);
            double passive = config.foulTroubleSitProbability(
                    f, passiveFactor, SimConfig.SCALE_AVG, true, null);
            assertTrue(aggressive > passive,
                    "aggressive coach benches more readily at f=" + f);
        }
    }

    @Test
    void aFoulTroubledPlayerIsBenchedMoreOftenThanACleanOneOverARun() {
        // The behavioral end-to-end check the probability comparisons above cannot
        // make: over a long run at a fixed roll, a 4-foul starter spends more time
        // off the floor than an identical team-mate with no fouls.
        List<PlayerGameState> squad = valuedSquad(6, 15.0, 8.0);
        RotationState r = rotation(squad, coachMods(10, 10));
        PlayerGameState troubled = squad.get(0);
        PlayerGameState clean = squad.get(1);
        foul(troubled, 4);

        int troubledSits = 0;
        int cleanSits = 0;
        for (int i = 0; i < 400; i++) {
            r.advancePossession(fixedRng(0.02));
            if (!r.onFloor().contains(troubled)) {
                troubledSits++;
            }
            if (!r.onFloor().contains(clean)) {
                cleanSits++;
            }
        }
        assertTrue(troubledSits > cleanSits,
                "the foul-troubled player sits more than his clean team-mate:"
                        + " troubled=" + troubledSits + " clean=" + cleanSits);
    }

    @Test
    void foulTroubleSubYieldsWhenTheBenchCannotCoverIt() {
        // #031 F: a soft preference never weakens a hard invariant. With no eligible
        // bench at all, the foul-troubled player keeps playing and the floor holds at 5.
        List<PlayerGameState> squad = squad(0); // exactly 5, no bench
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState troubled = squad.get(0);
        foul(troubled, 5);

        r.advancePossession(fixedRng(0.0));
        assertEquals(5, r.onFloor().size(), "floor stays at exactly 5");
        assertTrue(r.onFloor().contains(troubled),
                "with no eligible replacement the rule yields and he keeps playing");
    }

    @Test
    void foulTroubleSubYieldsWhenTheWholeBenchHasFouledOut() {
        List<PlayerGameState> squad = squad(3);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState troubled = squad.get(0);
        foul(troubled, 5);
        for (int i = 5; i < squad.size(); i++) {
            foul(squad.get(i), config.foulOutLimit());
        }

        r.advancePossession(fixedRng(0.0));
        assertEquals(5, r.onFloor().size());
        assertTrue(r.onFloor().contains(troubled),
                "a fouled-out bench is not an eligible replacement — the rule yields");
    }

    @Test
    void foulTroubleSubNeverBringsInAFouledOutPlayer() {
        // The invariant, asserted directly at the point the soft rule would most like
        // to break it: the only bench body available has fouled out.
        List<PlayerGameState> squad = squad(1);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState troubled = squad.get(0);
        PlayerGameState fouledOutBench = squad.get(5);
        foul(troubled, 5);
        foul(fouledOutBench, config.foulOutLimit());

        for (int i = 0; i < 50; i++) {
            r.advancePossession(fixedRng(0.0));
            assertFalse(r.onFloor().contains(fouledOutBench),
                    "a fouled-out player is never swapped in, ever");
            assertEquals(5, r.onFloor().size());
        }
    }

    @Test
    void hardFoulOutBeatsTheSoftFoulTroubleRule() {
        // Precedence: replaceFouledOut() runs first and is unconditional, so a
        // fouled-out player is forced off even in the same check where the soft rule
        // has something to say about another player.
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState fouledOut = squad.get(0);
        PlayerGameState troubled = squad.get(1);
        foul(fouledOut, config.foulOutLimit());
        foul(troubled, 5);

        r.advancePossession(fixedRng(0.0));
        assertFalse(r.onFloor().contains(fouledOut),
                "hard beats soft: the fouled-out player is forced off regardless");
        assertEquals(5, r.onFloor().size());
    }

    @Test
    void aFoulTroubleBenchedPlayerDoesNotReturnOnTheNextCheckWhileTired() {
        // STICKY SIT (#031 D): once benched the player is an ordinary bench player.
        // There is no competing foul-trouble roll to bring him back, and the freshness
        // path cannot return him until he has actually recovered energy.
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, coachMods(10, 20));
        PlayerGameState troubled = squad.get(0);

        // Drain the floor first so the returning player must genuinely out-rest a
        // bench body rather than walking straight back on.
        for (int i = 0; i < 20; i++) {
            r.advancePossession(rng());
        }
        foul(troubled, 5);
        r.advancePossession(fixedRng(0.0));
        assertFalse(r.onFloor().contains(troubled), "he should be benched for foul trouble");

        r.advancePossession(fixedRng(0.0));
        assertFalse(r.onFloor().contains(troubled),
                "he must not re-enter on the very next check — the return is earned"
                        + " through the freshness path, not re-rolled");
    }

    @Test
    void aFoulTroubledPlayerDoesNotOscillateOnAndOffTheFloor() {
        // #031 D/E: substitution is re-decided ~100 times per team per game. A rule
        // that re-rolled in both directions would flicker. Assert a realistic minimum
        // gap between being benched and returning, over a long run.
        // What "no oscillation" actually means here, measured against a CONTROL (an
        // identical team-mate with no fouls, same run): a foul-troubled player's
        // bench stints are EXPECTED to be shorter than a purely-exhausted player's —
        // he is benched more often, and a foul-trouble sit begins while he is still
        // relatively fresh, so he earns his way back sooner. That is the design, not
        // flicker. The thing #031 D forbids is a stint of one or two possessions
        // (~30-60 game-seconds), so the assertion is an absolute floor on the average
        // sit, with the control reported for context.
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, coachMods(10, 10));
        PlayerGameState troubled = squad.get(0);
        PlayerGameState clean = squad.get(1);
        foul(troubled, 4);

        SitStats troubledStats = new SitStats();
        SitStats cleanStats = new SitStats();
        for (int i = 0; i < 400; i++) {
            r.advancePossession(fixedRng(0.02));
            troubledStats.observe(r.onFloor().contains(troubled));
            cleanStats.observe(r.onFloor().contains(clean));
        }

        assertTrue(troubledStats.cycles > 0,
                "the foul-troubled player should be benched and return at least once");
        assertTrue(troubledStats.averageSit() >= 3.0,
                "a benched foul-troubled player must sit for several possessions on"
                        + " average before returning — a 1-2 possession stint is the"
                        + " flicker #031 D forbids. troubled avg sit="
                        + troubledStats.averageSit() + " (clean control avg sit="
                        + cleanStats.averageSit() + ")");
        assertTrue(troubledStats.cycles > cleanStats.cycles,
                "the foul-troubled player is benched MORE often than his clean"
                        + " team-mate — the rule is doing its job: troubled cycles="
                        + troubledStats.cycles + " clean cycles=" + cleanStats.cycles);
    }

    /** Tracks how long a player's completed bench stints last across a run. */
    private static final class SitStats {
        private boolean wasOn = true;
        private int sitLength;
        private int totalSat;
        private int cycles;

        void observe(boolean isOn) {
            if (!isOn) {
                sitLength++;
            } else if (!wasOn) {
                totalSat += sitLength;
                cycles++;
                sitLength = 0;
            }
            wasOn = isOn;
        }

        double averageSit() {
            return cycles == 0 ? Double.MAX_VALUE : (double) totalSat / cycles;
        }
    }

    @Test
    void foulTroubleSubDrawsOnlyFromTheRotationDepthWindow() {
        // #031 F: a foul-trouble sub is a rotation decision, not an emergency — it
        // draws from benchWithinDepth(), unlike the full-bench forced foul-out sub.
        List<PlayerGameState> squad = squad(6);
        RotationState r = rotation(squad, coachMods(1, 20)); // depth ~1
        PlayerGameState troubled = squad.get(0);
        PlayerGameState deepBench = squad.get(squad.size() - 1);
        drainTheFloor(r, 6);
        foul(troubled, 5);

        r.advancePossession(fixedRng(0.0));
        assertFalse(r.onFloor().contains(deepBench),
                "the deepest bench player is outside the rotationDepth window and must"
                        + " not be reached for a foul-trouble sub");
    }

    @Test
    void foulTroubleSubTakesExactlyOneUnconditionalDrawPerCall() {
        // Step 4 / #031: the draw must be taken at a fixed point regardless of whether
        // any candidate exists, or the seed stream forks on rotation state.
        //
        // §3.14a (#032 B) RE-BASELINED THE COUNT from one draw to THREE — the
        // foul-trouble roll, the technical roll, and the technical committer draw.
        // The count is what moved; the INVARIANT this test exists for did not, and
        // it is the second assertion: the number of draws must not depend on rotation
        // state. The committer draw is taken unconditionally (even though it is used
        // on ~0.35% of calls) precisely so that stays true — a conditional draw would
        // fork the stream on the roll's own outcome.
        CountingRng noCandidates = new CountingRng();
        RotationState quiet = rotation(squad(4), CoachModifiers.neutral());
        quiet.advancePossession(noCandidates);

        List<PlayerGameState> squad = squad(4);
        RotationState busy = rotation(squad, CoachModifiers.neutral());
        foul(squad.get(0), 5);
        CountingRng withCandidate = new CountingRng();
        busy.advancePossession(withCandidate);

        assertEquals(3, noCandidates.draws,
                "three unconditional draws even with nobody in foul trouble "
                        + "(foul-trouble roll + technical roll + committer draw)");
        assertEquals(withCandidate.draws, noCandidates.draws,
                "the draw count must not depend on rotation state");
    }

    private static final class CountingRng implements RandomGenerator {
        private int draws;

        @Override
        public double nextDouble() {
            draws++;
            return 1.0;
        }

        @Override
        public long nextLong() {
            return 0L;
        }
    }

    private CoachModifiers coachMods(int rotationDepth, int subAggressiveness) {
        software.daveturner.gametime.model.Coach c = new software.daveturner.gametime.model.Coach();
        c.setPace(10);
        c.setOffensiveScheme(10);
        c.setDefensiveScheme(10);
        c.setRotationDepth(rotationDepth);
        c.setSubstitutionAggressiveness(subAggressiveness);
        return CoachModifiers.from(c, config);
    }

    // ---------- §3.14a: technical fouls + ejections (decisions.md #032 B/C/F) ----------

    /**
     * A generator that replays a fixed script of nextDouble() values, so the three
     * unconditional draws {@code advancePossession} takes can be driven
     * independently: [0] foul-trouble roll, [1] technical roll, [2] committer draw.
     * Values past the end repeat the last entry.
     */
    private static RandomGenerator scriptedRng(double... values) {
        return new RandomGenerator() {
            private int i;

            @Override
            public double nextDouble() {
                double v = values[Math.min(i, values.length - 1)];
                i++;
                return v;
            }

            @Override
            public long nextLong() {
                return 0L;
            }
        };
    }

    /** No foul-trouble sub, technical roll FIRES, committer draw at {@code pick}. */
    private static RandomGenerator technicalFires(double pick) {
        return scriptedRng(1.0, 0.0, pick);
    }

    @Test
    void aTechnicalIsNotRolledWhenTheRollMissesAndTheReturnIsNull() {
        RotationState r = rotation(squad(4), CoachModifiers.neutral());
        // roll 1.0 is far above the ~0.0035 per-check probability.
        assertNull(r.advancePossession(rng()),
                "No technical on a missed roll — the overwhelmingly common case");
    }

    @Test
    void aTechnicalOnAHitReturnsAnOnFloorCommitterAndChargesTheSeparateCounter() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());

        PlayerGameState committer = r.advancePossession(technicalFires(0.0));

        assertNotNull(committer, "A fired roll must yield a committer");
        assertTrue(squad.subList(0, 5).contains(committer),
                "The committer is drawn from the ON-FLOOR five, never the bench (#032 C)");
        assertEquals(1, committer.getTechnicalFouls());
    }

    /**
     * #032 E — THE counter-split test, and the one the inverted stop condition is
     * watching. A technical must not leak into anything the six-foul machinery reads.
     */
    @Test
    void aTechnicalDoesNotTouchPersonalFoulsFoulOutOrFoulTrouble() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());

        PlayerGameState committer = r.advancePossession(technicalFires(0.0));

        assertEquals(0, committer.getFouls(),
                "getFouls() must keep meaning PERSONAL fouls only (#032 E)");
        assertEquals(0, committer.foulTroubleLevel(),
                "A technical must not raise the §3.13 foul-trouble level");
        assertFalse(committer.isFouledOut(),
                "A technical must not push a player toward the 6-foul limit");
    }

    /**
     * #032 C: the bench is excluded from the committer pool — a measurement-bias
     * call, not a realism one. Swept across the whole committer-draw range so the
     * assertion covers every branch of the weighted selection, not one lucky roll.
     */
    @Test
    void theTechnicalCommitterIsNeverABenchPlayer() {
        for (int i = 0; i <= 20; i++) {
            List<PlayerGameState> squad = squad(6);
            RotationState r = rotation(squad, CoachModifiers.neutral());
            PlayerGameState committer = r.advancePossession(technicalFires(i / 20.0));
            assertNotNull(committer);
            assertTrue(r.onFloor().contains(committer),
                    "Every technical lands on a player who is actually playing");
        }
    }

    /**
     * #032 C: the draw is {@code foulProne}-weighted. With one player at a far higher
     * foulProne than the rest, the top of the cumulative range must reach him — this
     * asserts the weighting is wired up at all, NOT that it is strong (it is
     * deliberately near-uniform, and that is accepted).
     */
    @Test
    void theTechnicalCommitterDrawIsFoulProneWeighted() {
        List<PlayerGameState> squad = new ArrayList<>();
        // Four low-foulProne starters and one very high one.
        for (int i = 1; i <= 4; i++) {
            squad.add(TestPlayerFactory.create("S" + i, "T", 10.0, 10.0, 10.0, 10.0,
                    10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, /*foulProne*/ 1.0));
        }
        squad.add(TestPlayerFactory.create("HOT", "T", 10.0, 10.0, 10.0, 10.0, 10.0,
                10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, /*foulProne*/ 96.0));
        for (int i = 1; i <= 3; i++) {
            squad.add(TestPlayerFactory.create("B" + i, "T", 10.0));
        }
        RotationState r = rotation(squad, CoachModifiers.neutral());

        // The four low players hold 4/100 of the weight, so anything above 0.04
        // must select HOT. Sample just above that boundary.
        PlayerGameState committer = r.advancePossession(technicalFires(0.5));
        assertEquals("HOT", committer.getPlayerId(),
                "A far higher foulProne must dominate the weighted draw");

        // ...and the low-weight head of the range still reaches a low player, so no
        // one is exempt by construction (#032 H).
        RotationState r2 = rotation(squad(4), CoachModifiers.neutral());
        assertNotNull(r2.advancePossession(technicalFires(0.0)));
    }

    // ---------- §3.14a: the ejection extends eligible(...) (#032 F, #031 H) ----------

    /**
     * #032 F: the ejection is DERIVED from the counter — no stored flag. Forced
     * directly, because the event essentially never occurs naturally.
     */
    @Test
    void twoTechnicalsEjectAPlayerAndTheEjectionIsDerived() {
        PlayerGameState p = player("S1", LineupRole.STARTER, null, 10, 10);
        assertFalse(p.isEjected());
        p.recordTechnicalFoul();
        assertFalse(p.isEjected(), "One technical is not an ejection");
        p.recordTechnicalFoul();
        assertTrue(p.isEjected(), "Two technicals is an automatic ejection");
        assertEquals(0, p.getFouls(), "...and still no personal fouls");
    }

    /**
     * #031 H / #032 F: an ejected player is forced off by the HARD tier — the same
     * path a foul-out uses — not by a fourth removal path.
     */
    @Test
    void anEjectedOnFloorPlayerIsForcedOffAndReplacedFromTheBench() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter = squad.get(0);
        eject(starter);

        r.advancePossession(rng());

        assertFalse(r.onFloor().contains(starter), "An ejected player must leave the floor");
        assertEquals(5, r.onFloor().size(), "The floor stays at exactly five");
    }

    /** #032 F: an ejected BENCH player is never selected as a replacement. */
    @Test
    void anEjectedBenchPlayerIsNotEligibleToComeIn() {
        List<PlayerGameState> squad = squad(2);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState bench1 = squad.get(5);
        PlayerGameState bench2 = squad.get(6);
        eject(bench1);
        // Force a hard sub by fouling out a starter.
        foul(squad.get(0), config.foulOutLimit());

        r.advancePossession(rng());

        assertFalse(r.onFloor().contains(bench1),
                "An ejected bench player must not be picked as a replacement");
        assertTrue(r.onFloor().contains(bench2),
                "...the next eligible bench player comes in instead");
    }

    /**
     * #023 F / #032 F: the never-below-5 last resort still holds when ejections and
     * foul-outs together exhaust the bench — the degenerate case. Nobody eligible
     * anywhere means a disqualified player stays on rather than the floor dropping.
     */
    @Test
    void onFloorStaysAtFiveWhenEjectionsAndFoulOutsExhaustTheBench() {
        List<PlayerGameState> squad = squad(2);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        // Every bench player unavailable: one ejected, one fouled out.
        eject(squad.get(5));
        foul(squad.get(6), config.foulOutLimit());
        // And two of the on-floor five disqualified, one by each cause.
        eject(squad.get(0));
        foul(squad.get(1), config.foulOutLimit());

        r.advancePossession(rng());

        assertEquals(5, r.onFloor().size(),
                "The never-below-5 invariant survives ejections (#023 F)");
    }

    /**
     * The degenerate guard in the committer draw: if every on-floor player has
     * non-positive {@code foulProne} there is no weight to draw against, so the
     * technical is dropped rather than pinned on an arbitrary player. Unreachable
     * with real skills (which are ≥ 1) — asserted so the guard is not silently
     * wrong if a future fixture or calculator ever produces a zero.
     */
    @Test
    void aTechnicalIsDroppedWhenNoOnFloorPlayerHasAnyFoulProneWeight() {
        List<PlayerGameState> squad = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            squad.add(TestPlayerFactory.create("P" + i, "T", 10.0, 10.0, 10.0, 10.0,
                    10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, /*foulProne*/ 0.0));
        }
        RotationState r = rotation(squad, CoachModifiers.neutral());

        assertNull(r.advancePossession(technicalFires(0.5)),
                "With zero total weight the technical is dropped, not misattributed");
        for (PlayerGameState p : squad) {
            assertEquals(0, p.getTechnicalFouls(), "…and nobody is charged");
        }
    }

    private static void eject(PlayerGameState p) {
        for (int i = 0; i < SimConfig.TECHNICAL_EJECTION_LIMIT; i++) {
            p.recordTechnicalFoul();
        }
    }

    // ---------- §3.14b: the flagrant-2 ejection, the THIRD cause (#034 F, #031 H) ----------

    private static void ejectForFlagrant(PlayerGameState p) {
        for (int i = 0; i < SimConfig.FLAGRANT_EJECTION_LIMIT; i++) {
            p.recordFlagrantTwo();
        }
    }

    /**
     * #034 F: the flagrant-2 ejection is DERIVED from a monotonic counter — no stored
     * flag — exactly like the foul-out and the two-technical ejection. Forced directly
     * (#032 F's discipline) rather than waiting for the event, which fires ~0.024 times
     * per team-game.
     *
     * <p>This is the assertion behind the finding that reverses #031 H / #032 F: the
     * threshold being 1 rather than 6 or 2 changes nothing about the shape.
     */
    @Test
    void oneFlagrantTwoEjectsAPlayerAndTheEjectionIsDerived() {
        PlayerGameState p = player("S1", LineupRole.STARTER, null, 10, 10);
        assertFalse(p.isEjectedForFlagrant());
        p.recordFlagrantTwo();
        assertTrue(p.isEjectedForFlagrant(),
                "ONE flagrant-2 ejects immediately — there is no accumulation threshold "
                        + "to remember, which is why no stored flag is needed (#034 F)");
        assertEquals(0, p.getFouls(),
                "…and recordFlagrantTwo() alone charges no personal foul — the call sites "
                        + "do that separately, so the two counters never double-count (#034 I)");
        assertFalse(p.isEjected(),
                "…and it is NOT the technical ejection: reusing technicalFouls would "
                        + "corrupt a surfaced box-score stat (#033)");
    }

    /**
     * #031 H / #034 F: a flagrant-2 ejection is forced off by the SAME hard tier a
     * foul-out and a technical ejection use — the third cause behind one
     * {@code isDisqualified(...)}, not a fourth removal path.
     */
    @Test
    void aFlagrantEjectedOnFloorPlayerIsForcedOffAndReplacedFromTheBench() {
        List<PlayerGameState> squad = squad(4);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState starter = squad.get(0);
        ejectForFlagrant(starter);

        r.advancePossession(rng());

        assertFalse(r.onFloor().contains(starter),
                "A flagrant-2 ejection must leave the floor, via the same hard tier");
        assertEquals(5, r.onFloor().size(), "The floor stays at exactly five");
    }

    /** #034 F: a flagrant-ejected BENCH player is never selected as a replacement. */
    @Test
    void aFlagrantEjectedBenchPlayerIsNotEligibleToComeIn() {
        List<PlayerGameState> squad = squad(2);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState bench1 = squad.get(5);
        PlayerGameState bench2 = squad.get(6);
        ejectForFlagrant(bench1);
        foul(squad.get(0), config.foulOutLimit()); // force a hard sub

        r.advancePossession(rng());

        assertFalse(r.onFloor().contains(bench1),
                "eligible(...) must exclude the flagrant-ejected player too");
        assertTrue(r.onFloor().contains(bench2),
                "…the next eligible bench player comes in instead");
    }

    /**
     * #023 F / #034 F: the never-below-5 last resort holds with ALL THREE
     * disqualification causes in play at once — the degenerate case, now three-sided.
     */
    @Test
    void onFloorStaysAtFiveWhenAllThreeDisqualificationCausesExhaustTheBench() {
        List<PlayerGameState> squad = squad(2);
        RotationState r = rotation(squad, CoachModifiers.neutral());
        // Both bench players unavailable, by two different causes.
        ejectForFlagrant(squad.get(5));
        foul(squad.get(6), config.foulOutLimit());
        // And three of the on-floor five disqualified, one by EACH cause.
        ejectForFlagrant(squad.get(0));
        eject(squad.get(1));
        foul(squad.get(2), config.foulOutLimit());

        r.advancePossession(rng());

        assertEquals(5, r.onFloor().size(),
                "The never-below-5 invariant survives all three causes (#023 F)");
    }

    /**
     * #034 F: a flagrant-ejected player is not a SOFT foul-trouble sub candidate either
     * — the third cause reaches {@code mostFoulTroubledCandidate()} through the same
     * shared predicate, so the two tiers cannot drift apart on what "disqualified" means.
     */
    @Test
    void aFlagrantEjectedPlayerIsNotASoftFoulTroubleSubCandidate() {
        List<PlayerGameState> squad = squad(0); // no bench: the hard tier cannot replace
        RotationState r = rotation(squad, CoachModifiers.neutral());
        PlayerGameState p = squad.get(0);
        foul(p, config.foulOutLimit() - 1); // deep in foul trouble
        ejectForFlagrant(p);

        r.advancePossession(rng());

        assertEquals(5, r.onFloor().size(),
                "With no bench he stays on (never-below-5), and the soft rule must not "
                        + "try to sit him either — both tiers ask isDisqualified(...)");
    }
}
