package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class FoulResolver {

    private final SimConfig config;

    public FoulResolver(SimConfig config) {
        this.config = config;
    }

    public boolean isFoul(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, RandomGenerator rng) {
        return isFoul(shotType, shooter, defender, 1.0, rng);
    }

    /**
     * Roll a foul that <b>stopped the shot</b> — the possession-ending "foul, no
     * basket" branch, on {@link SimConfig#BASE_NO_BASKET_FOUL}. Its counterpart is
     * {@link #isAndOne}, the foul the shot survived.
     *
     * <p>§3.4: {@code defensivePressure} (the defending coach's defensiveScheme
     * modifier) scales the foul rate — an aggressive, gambling defense both forces
     * turnovers and concedes more fouls (the pressure/breakdown trade-off in
     * coach.md). 1.0 = neutral.
     *
     * <p>§3.12 (decisions.md #030 A1/A2): <b>every</b> shot type can draw a foul
     * here. This used to early-return {@code false} for anything but a DRIVE/POST
     * (the deleted {@code ShotType.isContactType()} gate); it now multiplies by
     * {@link SimConfig#foulMultiplier} instead, so the rate — not a gate — carries
     * how contact-prone the shot type is. Because the table anchors DRIVE/POST at
     * 1.0, their rates are numerically unchanged from §3.11.
     */
    public boolean isFoul(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, double defensivePressure,
                          RandomGenerator rng) {
        // Both foulDrawing and foulProne increase foul probability.
        // foulProne is inverted: a high value means the defender fouls more (low discipline).
        // §3.5: fatigue scales each contestant's skill — a tired defender's
        // discipline (effectiveDefense) drops, so they foul more; a tired shooter
        // draws fouls slightly less.
        double effectiveDefense = (SimConfig.SCALE_AVG * 2 - defender.getFoulProne())
                * defender.fatigueFactor();
        double foulDrawing = shooter.getFoulDrawing() * shooter.fatigueFactor();
        // §3.12: the per-shot-type multiplier scales the WHOLE probability, skill
        // term included — which is what makes a 0.0 multiplier a true off-switch,
        // unlike a zero base (#029's finding: the skill term alone keeps a zero-base
        // rate positive).
        //
        // ⚠ The multiplied result is clamped WITHOUT PROB_FLOOR (0.02), the #028
        // trap in its §3.12 form. FOUL_MULT_THREE targets exactly 2% — sitting ON
        // the floor — so clampProbability here would (a) silently ignore any
        // downward tuning of the THREE multiplier and (b) floor a 0.0 multiplier up
        // to 2%, destroying the off-switch #030 A1 relies on. The floor exists so a
        // skill mismatch cannot make a NORMAL outcome impossible; a deliberately
        // rare per-type carve is the case it was never meant for. The unmultiplied
        // DRIVE/POST path is unaffected — at mult 1.0 the value is far above the
        // floor, so drive/post stay bit-identical to §3.11 (A2).
        //
        // §3.14a (#032 H): the hand-rolled clamp here now runs through the shared
        // SimConfig.clampRareProbability — the fourth site consolidated onto one
        // owner. Behavior-neutral: the added max(0.0, ...) can never bind, since
        // both factors above are non-negative.
        double contested = defensivePressure * config.contestProbability(
                SimConfig.BASE_NO_BASKET_FOUL, foulDrawing, effectiveDefense);
        double prob = config.clampRareProbability(
                config.foulMultiplier(shotType) * contested);
        return rng.nextDouble() < prob;
    }

    /**
     * §3.11 (decisions.md #029 A1/C): roll an <b>and-1</b> — a defensive foul on a
     * shot that still went in. This is a SECOND, post-make roll, entirely separate
     * from {@link #isFoul}: the pre-shot foul branch keeps meaning "the contact
     * stopped the shot" and {@link SimConfig#BASE_NO_BASKET_FOUL} keeps its §3.4
     * calibration (#029 A1). The caller rolls this only on a MADE shot, so this
     * method does not re-check the make.
     *
     * <p>§3.12 (decisions.md #030 A1/B): rolled on <b>every</b> made shot, not just
     * a made DRIVE/POST — the caller's {@code isContactType()} gate is gone. The
     * graduation now rides on {@code shotType}, which scales this roll by the
     * <b>same</b> {@link SimConfig#foulMultiplier} table {@link #isFoul} uses: a
     * shot type's propensity to draw contact is a property of the shot, not of
     * which roll is asking.
     *
     * <p><b>An and-1 on a three is rarer than a foul on a three for free</b> — the
     * two rolls are independent, so the shot must ALSO go in, and a three both
     * makes less often and fouls less often. That compounding is why there is no
     * second, steeper and-1 table (#030 B): adding one would double-count it. Note
     * the multiplier consequently lands twice in the and-1 path (here, and on the
     * stopped-shot roll this shot had to survive), making the knob non-linear on
     * and-1 rates.
     *
     * <p><b>This roll and {@link #isFoul} are mutually exclusive</b> — not by any
     * check here, but by control flow: the caller's stopped-shot branch returns, so
     * a shot that drew a foul there never reaches the make/miss roll and never
     * reaches this one. One shot emits at most one of {@code SHOOTING_FOUL} /
     * {@code AND_ONE}, never both, and sharing one multiplier table does not apply
     * it twice to a single shot.
     *
     * <p><b>Carved off the top</b>, the §3.7 block / §3.10 rebound-foul shape a
     * third time: an independent roll layered on an existing outcome, never
     * entangled with the outcome it rides, which is what keeps its rate tunable on
     * its own.
     *
     * <p>Probability reuses {@link #isFoul}'s exact avg-10 inputs (#021 C / #022) —
     * the shooter's {@code foulDrawing} against the defender's effective discipline
     * ({@code foulProne} inverted), both fatigue-scaled, scaled by {@code
     * defensivePressure} (an aggressive scheme concedes more contact, the coach.md
     * pressure/breakdown trade-off) — but on {@link SimConfig#AND_ONE_BASE} through
     * {@link SimConfig#rareEventProbability}, NOT {@code contestProbability}: the
     * global {@code PROB_FLOOR} (0.02) would act as a floor on a thin base and make
     * this knob tunable only upward (the #028 trap), and the global {@code
     * SENSITIVITY} (0.5) would swamp it — hence its own {@link
     * SimConfig#AND_ONE_SENSITIVITY} (#029 C).
     */
    public boolean isAndOne(ShotType shotType, PlayerGameState shooter,
                            PlayerGameState defender, double defensivePressure,
                            RandomGenerator rng) {
        double effectiveDefense = (SimConfig.SCALE_AVG * 2 - defender.getFoulProne())
                * defender.fatigueFactor();
        double foulDrawing = shooter.getFoulDrawing() * shooter.fatigueFactor();
        double prob = Math.min(SimConfig.PROB_CEILING,
                config.foulMultiplier(shotType) * defensivePressure
                        * config.rareEventProbability(
                                SimConfig.AND_ONE_BASE, foulDrawing, effectiveDefense,
                                SimConfig.AND_ONE_SENSITIVITY));
        return rng.nextDouble() < prob;
    }

    /**
     * §3.14b (decisions.md #034 A/E/G): was a foul that <b>already happened</b> a
     * FLAGRANT? <b>One definition, three callers</b> — the stopped-shot site, the and-1
     * site and the rebounding-foul site all ask this same question, and it must never
     * become three copies.
     *
     * <p><b>This is LAYERED ON TOP of an outcome that is already fully resolved, not
     * carved OUT of one</b>, and that inversion is what makes §3.14b free on every
     * existing rate. §3.7's block, §3.10's rebounding foul and §3.11's and-1 each take
     * a slice out of an outcome, re-partitioning it. This one is asked only after
     * {@link #isFoul}, {@link #isAndOne} or {@link #resolveReboundFoul} has already
     * returned a foul, so <b>nothing is re-partitioned and no existing rate moves by
     * construction</b> (#034 A). {@code isFoul} / {@code isAndOne} /
     * {@code resolveReboundFoul} keep their rates, their inputs and their RNG draws
     * exactly as shipped.
     *
     * <p><b>⚠ NO SKILL INPUTS AT ALL — and unlike every other roll in this class, that
     * is a positive design claim rather than a simplification (#034 E).</b> This takes
     * no {@link PlayerGameState} because the engine cannot distinguish excessive
     * contact from ordinary contact, so weighting it by {@code foulProne} would
     * manufacture a signal the model does not have. More precisely: <b>{@code
     * foulProne} has ALREADY had its say</b> — the committer was chosen before this
     * roll fires ({@code pickDefender} at the shooting sites, {@code pickCommitter} at
     * the rebounding site, both {@code foulProne}-weighted), so weighting the grade
     * too would apply one signal twice. The symmetry with §3.14a's {@code foulProne}
     * committer draw (#032 C) is tempting and <b>wrong</b>: that weighted a
     * <i>selection</i>, this is a <i>grade</i> on a player already selected.
     *
     * <p><b>Determinism:</b> this adds one {@code nextDouble()} <b>per FOUL</b>, not
     * per possession. A deliberate exception to §3.14a's unconditional-draw discipline,
     * permissible because the draw is nested <i>inside</i> an already-conditional
     * branch (the foul), so it cannot fork the stream on rotation state the way #031's
     * would have — it forks only on the foul's own outcome, which the stream has
     * already forked on.
     */
    public boolean isFlagrant(RandomGenerator rng) {
        return rng.nextDouble() < config.flagrantFoulProbability();
    }

    /**
     * §3.14b (decisions.md #034 E): given a flagrant, was it a <b>FLAGRANT-2</b> — the
     * grade that ejects the committer immediately? A flat {@link
     * SimConfig#FLAGRANT_TWO_SHARE} conditional sub-roll, taken <b>only on a hit</b>
     * from {@link #isFlagrant}.
     *
     * <p><b>One mechanic with a severity sub-roll, not two independently-rated
     * mechanics.</b> A separately-tuned flagrant-2 <i>rate</i> was rejected on
     * measurability: at ~33 flagrants per 102-game harness run a flagrant-2 line is ~5
     * events, unresolvable at any seed count, so the second constant would be a knob
     * nobody could ever read. A conditional share says the thing actually known —
     * <i>what fraction of flagrants are severe</i> — and inherits the parent rate's
     * resolvability.
     *
     * <p><b>The grade changes NOTHING but the ejection</b> (#034 C/E): two free throws
     * either way, the same possession fork, the same personal foul. Causally inert for
     * the same reason {@link #isFlagrant} is — see its javadoc.
     */
    public boolean isFlagrantTwo(RandomGenerator rng) {
        return rng.nextDouble() < SimConfig.FLAGRANT_TWO_SHARE;
    }

    public boolean isFreeThrowMade(PlayerGameState shooter, RandomGenerator rng) {
        double prob = config.freeThrowProbability(shooter.getFreeThrows());
        return rng.nextDouble() < prob;
    }

    /**
     * §3.10 (decisions.md #028 A2/C): roll a loose-ball / rebounding foul on a
     * missed shot. Returns {@code null} when no foul was committed (the common
     * case) — the caller then runs the normal four-way board draw.
     *
     * <p><b>Carved off the top</b> (Decision C, the §3.7 {@code P(BLOCK)} shape):
     * this is rolled BEFORE the board contest and short-circuits it on a hit (the
     * whistle stopped play, so nobody rebounds). Keeping it a separate roll rather
     * than a fifth outcome inside the four-way draw is what keeps the foul rate
     * independently tunable — it never entangles with the rebound weights.
     *
     * <p><b>Two-sided</b> (Decision A2): on a foul, a second, defense-LEANING draw
     * picks the committing side — a defensive box-out push (dominant) or an
     * offensive over-the-back (the minority) — and the committer is then picked
     * from that side by a {@code foulProne}-weighted draw, so the undisciplined
     * players foul most. The side determines who is fouled and how the possession
     * forks (Decision B), which the caller owns.
     *
     * <p>Probability reuses the shooting foul's inputs in the avg-10 form (#021 C
     * / #022): the aggregate discipline of the rebounding side against the
     * aggregate foul-drawing of the other, scaled by {@code defensivePressure} (an
     * aggressive scheme concedes more contact — the coach.md pressure/breakdown
     * trade-off), all on a small {@link SimConfig#REBOUND_FOUL_BASE}. Fatigue
     * scales each side's skills exactly as {@link #isFoul} does.
     *
     * <p><b>RNG order is fixed</b> (#028, determinism): the foul roll, then (only
     * on a hit) the side draw, then the committer draw — all before the board
     * draw. Seed-pinned rebound assertions re-baseline once against this shift.
     */
    public ReboundFoul resolveReboundFoul(List<PlayerGameState> offense,
                                          List<PlayerGameState> defense,
                                          double defensivePressure,
                                          RandomGenerator rng) {
        double defenseDiscipline = averageEffectiveDiscipline(defense);
        double offenseDrawing = averageFoulDrawing(offense);
        // rareEventProbability, NOT contestProbability: the global PROB_FLOOR (0.02)
        // would act as a floor on a ~0.03 base, making this knob tunable only upward
        // (#028 C — it must stay independently tunable in BOTH directions). Its own
        // gentle sensitivity keeps the base dominant, as §3.7 does for blocks.
        double prob = Math.min(SimConfig.PROB_CEILING,
                defensivePressure * config.rareEventProbability(
                        SimConfig.REBOUND_FOUL_BASE, offenseDrawing, defenseDiscipline,
                        SimConfig.REBOUND_FOUL_SENSITIVITY));
        if (rng.nextDouble() >= prob) {
            return null;
        }

        // Side draw — defense-leaning (raw weights, normalized by their sum).
        double sideTotal = SimConfig.REBOUND_FOUL_DEFENSE_WEIGHT
                + SimConfig.REBOUND_FOUL_OFFENSE_WEIGHT;
        boolean offenseCommits =
                rng.nextDouble() * sideTotal >= SimConfig.REBOUND_FOUL_DEFENSE_WEIGHT;
        ReboundFoul.Side side = offenseCommits
                ? ReboundFoul.Side.OFFENSE
                : ReboundFoul.Side.DEFENSE;

        List<PlayerGameState> committingSide = offenseCommits ? offense : defense;
        return new ReboundFoul(side, pickCommitter(committingSide, rng));
    }

    /**
     * §3.10: pick who committed the rebounding foul from the committing five,
     * weighted by {@code foulProne} — the undisciplined bang bodies commit most of
     * them. Mirrors {@link ReboundResolver}'s skill-weighted rebounder draw.
     */
    PlayerGameState pickCommitter(List<PlayerGameState> players, RandomGenerator rng) {
        double totalWeight = 0;
        for (PlayerGameState p : players) {
            totalWeight += p.getFoulProne();
        }
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : players) {
            cumulative += p.getFoulProne();
            if (roll < cumulative) return p;
        }
        return players.get(players.size() - 1);
    }

    /**
     * §3.10: the five's average effective discipline — {@code foulProne} inverted
     * (high foulProne = low discipline = fouls more) and fatigue-scaled, exactly
     * the per-defender form {@link #isFoul} uses.
     */
    private double averageEffectiveDiscipline(List<PlayerGameState> players) {
        double sum = 0;
        for (PlayerGameState p : players) {
            sum += (SimConfig.SCALE_AVG * 2 - p.getFoulProne()) * p.fatigueFactor();
        }
        return players.isEmpty() ? SimConfig.SCALE_AVG : sum / players.size();
    }

    /** §3.10: the five's average fatigue-scaled {@code foulDrawing}. */
    private double averageFoulDrawing(List<PlayerGameState> players) {
        double sum = 0;
        for (PlayerGameState p : players) {
            sum += p.getFoulDrawing() * p.fatigueFactor();
        }
        return players.isEmpty() ? SimConfig.SCALE_AVG : sum / players.size();
    }
}
