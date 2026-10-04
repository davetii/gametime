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
     * basket" branch, on {@link SimConfig#baseNoBasketFoul()}. Its counterpart is
     * {@link #isAndOne}, the foul the shot survived.
     *
     * <p>§3.4: {@code defensivePressure} (the defending coach's defensiveScheme
     * modifier) scales the foul rate — an aggressive, gambling defense both forces
     * turnovers and concedes more fouls (the pressure/breakdown trade-off in
     * coach.md). 1.0 = neutral.
     *
     * <p>§3.12 (decisions.md #030 A1/A2): <b>every</b> shot type can draw a foul here,
     * graduated by {@link SimConfig#foulMultiplier} — the rate, not a gate, carries how
     * contact-prone a shot type is. The table anchors DRIVE/POST at 1.0, so their rates
     * are unchanged from §3.11.
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
        // §3.12: the multiplier scales the WHOLE probability, skill term included —
        // which is what makes a 0.0 multiplier a true off-switch, unlike a zero base
        // (#029: the skill term alone keeps a zero-base rate positive).
        //
        // ⚠ Clamped WITHOUT PROB_FLOOR (0.02) — the #028 trap in its §3.12 form.
        // FOUL_MULT_THREE targets exactly 2%, ON the floor, so clampProbability here
        // would silently ignore downward tuning of the THREE multiplier AND floor a 0.0
        // multiplier back up, destroying the off-switch #030 A1 relies on. The floor is
        // there so a skill mismatch cannot make a NORMAL outcome impossible; a
        // deliberately rare per-type carve is the case it was never meant for. DRIVE/POST
        // are unaffected — at mult 1.0 the value sits far above the floor (#030 A2).
        double contested = defensivePressure * config.contestProbability(
                config.baseNoBasketFoul(), foulDrawing, effectiveDefense);
        double prob = config.clampRareProbability(
                config.foulMultiplier(shotType) * contested);
        return rng.nextDouble() < prob;
    }

    /**
     * §3.11 (decisions.md #029 A1/C): roll an <b>and-1</b> — a defensive foul on a
     * shot that still went in. This is a SECOND, post-make roll, entirely separate
     * from {@link #isFoul}: the pre-shot foul branch keeps meaning "the contact
     * stopped the shot" and {@link SimConfig#baseNoBasketFoul()} keeps its §3.4
     * calibration (#029 A1). The caller rolls this only on a MADE shot, so this
     * method does not re-check the make.
     *
     * <p>§3.12 (decisions.md #030 A1/B): rolled on <b>every</b> made shot, scaled by the
     * <b>same</b> {@link SimConfig#foulMultiplier} table {@link #isFoul} uses — a shot
     * type's propensity to draw contact is a property of the shot, not of which roll is
     * asking. ⚠ <b>Do NOT add a second, steeper and-1 table</b> (#030 B): an and-1 on a
     * three is already rarer than a foul on a three, because the two rolls are
     * independent and the shot must ALSO go in. A second table would double-count that.
     * The multiplier consequently lands twice in this path (here, and on the stopped-shot
     * roll the shot had to survive), so the knob is non-linear on and-1 rates.
     *
     * <p><b>This roll and {@link #isFoul} are mutually exclusive by CONTROL FLOW</b>, not
     * by any check here: the caller's stopped-shot branch returns, so a shot that drew a
     * foul there never reaches the make/miss roll. One shot emits at most one of {@code
     * SHOOTING_FOUL} / {@code AND_ONE}.
     *
     * <p>Inputs are {@link #isFoul}'s exact avg-10 form (#021 C / #022), but on {@link
     * SimConfig#andOneBase()} through {@link SimConfig#rareEventProbability}, NOT {@code
     * contestProbability}: {@code PROB_FLOOR} (0.02) would floor a thin base and make the
     * knob tunable only upward (the #028 trap), and the global {@code SENSITIVITY} (0.5)
     * would swamp it — hence {@link SimConfig#AND_ONE_SENSITIVITY} (#029 C).
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
                                config.andOneBase(), foulDrawing, effectiveDefense,
                                SimConfig.AND_ONE_SENSITIVITY));
        return rng.nextDouble() < prob;
    }

    /**
     * §3.14b (decisions.md #034 A/E/G): was a foul that <b>already happened</b> a
     * FLAGRANT? <b>One definition, three callers</b> — the stopped-shot site, the and-1
     * site and the rebounding-foul site all ask this same question, and it must never
     * become three copies.
     *
     * <p><b>LAYERED ON TOP of an already-resolved outcome, not carved OUT of one</b> —
     * the inversion that makes §3.14b free on every existing rate (#034 A). §3.7's block,
     * §3.10's rebounding foul and §3.11's and-1 each re-partition an outcome; this is
     * asked only after a foul has already been returned, so nothing is re-partitioned and
     * no existing rate moves by construction.
     *
     * <p><b>⚠ NO SKILL INPUTS AT ALL — a positive design claim, not a simplification
     * (#034 E). This is the argument {@link #isNonShootingFoul} refers back to.</b> The
     * engine cannot distinguish excessive contact from ordinary contact, so weighting by
     * {@code foulProne} would manufacture a signal the model does not have. And {@code
     * foulProne} has <b>already had its say</b>: at the shooting sites the defender's
     * {@code foulProne} is already in the foul <i>rate</i> (the defender is drawn by
     * {@code individualDefense}, not {@code foulProne}), and at the rebounding site {@code
     * pickCommitter} is {@code foulProne}-weighted, so weighting the grade too would
     * apply one signal twice. ⚠ The symmetry with §3.14a's committer draw (#032 C) is
     * tempting and <b>wrong</b> — that weighted a <i>selection</i>, this is a
     * <i>grade</i> on a player already selected.
     *
     * <p><b>Determinism:</b> one {@code nextDouble()} <b>per FOUL</b>, not per
     * possession. A deliberate exception to §3.14a's unconditional-draw discipline,
     * permissible because the draw is nested inside an already-conditional branch, so it
     * forks the stream only on the foul's own outcome — never on rotation state, as
     * #031's would have.
     */
    public boolean isFlagrant(RandomGenerator rng) {
        return rng.nextDouble() < config.flagrantFoulProbability();
    }

    /**
     * §3.14b (decisions.md #034 E): given a flagrant, was it a <b>FLAGRANT-2</b> — the
     * grade that ejects the committer immediately? A flat {@link
     * SimConfig#flagrantTwoShare()} conditional sub-roll, taken <b>only on a hit</b>
     * from {@link #isFlagrant}.
     *
     * <p><b>⚠ One mechanic with a severity sub-roll, not two independently-rated
     * mechanics</b> — a separately-tuned flagrant-2 <i>rate</i> was rejected as
     * unmeasurable; the sizing is on {@code flagrantTwoShare}'s field.
     *
     * <p><b>The grade changes NOTHING but the ejection</b> (#034 C/E): two free throws
     * either way, the same possession fork, the same personal foul. Causally inert for
     * the same reason {@link #isFlagrant} is.
     */
    public boolean isFlagrantTwo(RandomGenerator rng) {
        return rng.nextDouble() < config.flagrantTwoShare();
    }

    /**
     * §3.16 (decisions.md #039 A/B/C/E): was a foul that <b>already happened</b> a
     * <b>non-shooting</b> foul rather than a {@code SHOOTING_FOUL}? A flat {@link
     * SimConfig#nonShootingFoulShare()} roll, {@link #isFlagrant}'s sibling and
     * deliberately its twin in shape.
     *
     * <p><b>LAYERED ON TOP of a foul already rolled and charged</b> — {@link
     * #isFlagrant}'s inversion applied a second time. {@code defender.recordFoul()} has
     * already run before this is asked, so <b>the foul TOTAL holds by construction</b>
     * (#039 A): foul-outs, {@code foulTroubleLevel()} and the bonus tally keep working on
     * either branch with no tuning. What moves is the <i>outcome</i>, and with it the
     * free throws.
     *
     * <p><b>⚠ The converted foul awards NO free throws outside the bonus, and ENDS the
     * possession — the ball does NOT come back</b> (#039 C). <b>This is wrong as
     * basketball and it is deliberate</b>, so do not "fix" it as a bug: a real common
     * foul is a side inbound and the offense keeps the ball, but every returning variant
     * re-enters the loop at {@code ShotSelector} for a live attempt worth ~0.76 FGA where
     * the stopped shot charged none — against under one attempt of FGA headroom (88.4
     * vs 89.1 real when this was designed; see calibration.md for the current reading). That caps a retaining variant near a ~6% share, moving FTA by less than one
     * attempt. FGA wins because it is sourced and already correct. The caller owns the
     * fork — see {@code PossessionEngine}'s foul block.
     *
     * <p><b>⚠ NO SKILL INPUTS — a positive design claim, on {@link #isFlagrant}'s
     * argument (#039 E).</b> Additionally specific to this roll: the engine has no
     * representation of <i>where on the floor</i> the contact happened, which is the
     * thing that actually decides shooting vs. non-shooting.
     *
     * <p><b>⚠ Rolled only AFTER {@link #isFlagrant} misses</b> (#039 F). A flagrant
     * common foul is simply a flagrant — it awards its flat 2 FTs and returns the ball
     * — so the two never compose and there is no "flagrant that awards nothing" case.
     * The ordering is load-bearing, not incidental.
     *
     * <p><b>Determinism:</b> one {@code nextDouble()} <b>per non-flagrant foul</b>,
     * nested inside the conditional foul branch exactly as {@link #isFlagrant} is.
     */
    public boolean isNonShootingFoul(RandomGenerator rng) {
        return rng.nextDouble() < config.nonShootingFoulShare();
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
     * <p><b>Carved off the top</b> (Decision C, the §3.7 {@code P(BLOCK)} shape): rolled
     * BEFORE the board contest and short-circuits it on a hit (the whistle stopped play,
     * so nobody rebounds). ⚠ Keeping it a separate roll rather than a fifth outcome in
     * the four-way draw is what keeps the foul rate independently tunable — it never
     * entangles with the rebound weights.
     *
     * <p><b>Two-sided</b> (Decision A2): a defense-LEANING draw picks the committing side
     * (box-out push, dominant; over-the-back, the minority), then a {@code
     * foulProne}-weighted draw picks the committer from that side. The side determines
     * who is fouled and how the possession forks (Decision B), which the caller owns.
     *
     * <p>Probability reuses the shooting foul's avg-10 inputs (#021 C / #022) in
     * aggregate per side, fatigue-scaled as {@link #isFoul} does, on a small {@link
     * SimConfig#reboundFoulBase()}.
     *
     * <p><b>⚠ RNG order is fixed</b> (#028): the foul roll, then (only on a hit) the side
     * draw, then the committer draw — all before the board draw.
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
                        config.reboundFoulBase(), offenseDrawing, defenseDiscipline,
                        SimConfig.REBOUND_FOUL_SENSITIVITY));
        if (rng.nextDouble() >= prob) {
            return null;
        }

        // Side draw — defense-leaning (raw weights, normalized by their sum).
        double sideTotal = config.reboundFoulDefenseWeight()
                + config.reboundFoulOffenseWeight();
        boolean offenseCommits =
                rng.nextDouble() * sideTotal >= config.reboundFoulDefenseWeight();
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
