package software.daveturner.gametime.sim;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * All simulation constants in one place (decisions.md #021) so they can be tuned
 * without touching the resolvers.
 *
 * <p><b>Where the numbers and their history live.</b> This header used to carry a
 * per-phase calibration changelog (§3.4 → §3.11, each with its landing box). It was
 * removed as stale narrative: those boxes reported a ~112-point target and landings
 * of 111.7 → 115.9, all superseded. The current record is elsewhere and is kept
 * current:
 * <ul>
 *   <li><b>docs/calibration.md</b> — the targets, and the SOURCE OF TRUTH for them.
 *       Read it before changing any constant here or calling a landing "on target".</li>
 *   <li><b>docs/engine-traps.md</b> — the traps, wrong-way levers, saturated knobs and
 *       measured elasticities. The facts no source file holds.</li>
 *   <li><b>docs/decisions.md #NNN</b> — the archive each constant's note cites for the
 *       argument behind its value.</li>
 * </ul>
 * Per-constant notes below carry what is operative for that field; they are the
 * warnings that must stay next to the thing they protect.
 */
@ConfigurationProperties(prefix = "sim")
@Validated
public class SimConfig {

    /*
    Two kinds of constant live here, and the declaration form tells them apart:

      public static final       a RULE of basketball or the SHAPE of the model.
                                Not a knob. 29 of them.
      private final + accessor  a tunable knob, bound by Spring from
                                application-baseline.properties. 63 of them.

    The instance fields take NO INITIALIZERS: the values exist only in the
    properties file, and a missing key fails the context at startup. Adding a
    default back here would create a second source of truth (javac rejects it
    outright, since the fields are final and constructor-assigned).

    Never add a public static final alias for a tunable constant: javac inlines
    constant variables into each caller at that caller's compile time, so an
    alias becomes a stale second value with no link to the real one.

    SCALE_AVG and MAX_ENERGY are scale DEFINITIONS, not knobs - each is the
    denominator that gives its family meaning.
    */

    // --- Possession count (Decision B) ---
    private final int defaultPossessionsPerPeriod;
    public static final int PERIODS = 4;
    private final int otPossessionsPerPeriod;

    // --- Probability sensitivity (Decision C) ---
    public static final double SENSITIVITY = 0.5;
    public static final double PROB_FLOOR = 0.02;
    public static final double PROB_CEILING = 0.97;
    public static final double SCALE_AVG = 10.0;

    // --- Shot base rates (calibrated §3.4 against ~47% FG / ~36% 3P) ---
    private final double baseDrive;
    private final double basePerimeter;
    private final double baseThree;
    private final double basePost;

    // --- Turnover base rate (per possession; calibrated §3.4 toward ~14 TO/team) ---
    private final double baseTurnover;

    /**
     * Foul base rate: P(a foul STOPPED the shot) on a DRIVE (§3.12 anchor).
     * This is the possession-ENDING branch (foul, no basket, go to the line), not
     * "the foul rate". Its pair is AND_ONE_BASE — no basket vs. basket-plus-one.
     *
     * <p>⚠ NOT A POINTS LEVER — measured 2026-09, seed 1000, this value the only change:
     * <pre>
     *   0.140 -> points 114.7 | FGA 91.0 | FTA 20.9 | fouls 17.94
     *   0.178 -> points 114.8 | FGA 89.3 | FTA 23.8 | fouls 19.33   (baseline)
     *   0.210 -> points 115.6 | FGA 87.6 | FTA 26.7 | fouls 20.74
     * </pre>
     *
     * <p>Points move +0.9 across a 50% swing while FTA moves +5.8 and FGA -3.4. TUNE IT
     * AGAINST FGA, NEVER AGAINST POINTS; FGA and fouls are over-determined through it
     * (~1.4 FGA per foul). ⚠ Ignore any older "trimming moves points the WRONG way"
     * warning — that was #028 E, measured pre-§3.12 and disproved by #036 C.
     *
     * <p>Since §3.12 this is the DRIVE rate and the anchor for the FOUL_MULT_* table
     * below (#030 A2): every shot type's stopped-shot probability is
     * BASE_NO_BASKET_FOUL × FOUL_MULT_<type>. It is deliberately NOT re-derived as
     * a league-wide average — keeping it the drive rate is what makes drive/post
     * behavior bit-identical to §3.11 and §3.12's whole delta attributable to
     * perimeter/three.
     */
    private final double baseNoBasketFoul;

    /**
     * Per-shot-type foul multipliers (§3.12, decisions.md #030 A1/A2/B).
     *
     * <p>⚠ THESE ARE MULTIPLIERS, NOT PROBABILITIES. They sit beside
     * BASE_NO_BASKET_FOUL = 0.15, where every value looks like a probability, so
     * read them carefully: FOUL_MULT_THREE = 0.133 does NOT mean "13.3% of threes
     * are fouled" — it means "a three draws contact at 13.3% of the rate a drive
     * does", i.e. 0.15 × 0.133 = 0.02 = 2%. (This misreading happened once during
     * the design pass; hence the shouting.)
     *
     * <p>⚠ DRIVE = 1.0 IS THE ANCHOR, and raising it is not the way to make drives foul
     * more — that is a BASE_NO_BASKET_FOUL conversation, and it reopens a
     * §3.4-calibrated number (#030 A2).
     *
     * <p>⚠ Do NOT re-introduce a boolean contact-type gate: every shot type draws contact
     * at a graduated rate, and the rate carries what the gate used to (#030 A1, the
     * #013/#015 single-source rule). A multiplier of exactly 0.0 is the one true
     * off-switch — it scales the whole probability, skill term included, where a zero
     * BASE does not (#029: the skill term alone keeps a zero-base rate positive).
     *
     * <p>⚠ ONE SHARED TABLE drives BOTH foul rolls (#030 B) — FoulResolver.isFoul and
     * .isAndOne. It therefore lands TWICE in the and-1 path, making these knobs
     * NON-LINEAR on and-1s: halving FOUL_MULT_THREE more than halves the three's and-1
     * rate. Do NOT add a second and-1 table; see FoulResolver.isAndOne.
     */
    private final double foulMultDrive;
    private final double foulMultPost;
    private final double foulMultPerimeter;
    private final double foulMultThree;

    /**
     * §3.12 (#030 A1/A2/B): the per-shot-type foul multiplier — how often this shot
     * type draws contact <b>relative to a drive</b>, which is the 1.0 anchor.
     *
     * <p>Read by both foul rolls ({@link FoulResolver#isFoul} and {@link
     * FoulResolver#isAndOne}), so a type's contact propensity has one owner. The
     * arithmetic at the stopped-shot roll is {@code BASE_NO_BASKET_FOUL ×
     * foulMultiplier(type)}, then the usual skill/fatigue/{@code defensivePressure}
     * terms.
     *
     * <p><b>This returns a multiplier, not a probability</b> — see the constants'
     * note above.
     */
    public double foulMultiplier(ShotType shotType) {
        return switch (shotType) {
            case DRIVE -> foulMultDrive;
            case POST -> foulMultPost;
            case PERIMETER -> foulMultPerimeter;
            case THREE -> foulMultThree;
        };
    }

    // --- Free throw ---
    private final double ftBase;
    public static final double FT_SENSITIVITY = 0.20;
    public static final int FREE_THROWS_PER_FOUL = 2;

    /**
     * Rebounding (§3.3): the rate at an average-vs-average contest (NBA ~25–28%).
     * Rebound contests reuse the global {@link #SENSITIVITY}.
     */
    private final double baseOffensiveRebound;
    /**
     * The ONLY bound on PossessionEngine.resolvePossession's second-chance
     * `while (true)` loop: how many times the offense may keep the ball and run the
     * flow again within one possession. Once reached, the retaining path is refused
     * and the possession ends (a missed shot is forced to a defensive rebound; a
     * §3.14b flagrant still awards its free throws, then ends it).
     *
     * <p>⚠ RETENTIONS, NOT REBOUNDS: FIVE paths count against this cap and FOUR of them
     * are not rebounds — §3.7 block recovery, §3.8 OOB-offense, §3.10's rebounding
     * foul and §3.14b's flagrant retention, alongside the original §3.3 offensive
     * rebound. Every one is the offense keeping the ball, the sense
     * BlockRecovery/MissedShotOutcome/ReboundFoulResult all expose as offenseRetains().
     * ⚠ Do NOT confuse it with PlayerGameState's `offensiveRebounds`, which is the
     * BOX-SCORE STAT and counts something different.
     *
     * <p>Raising it 3 → 5 is a SEPARATE, parked tuning idea (ideas.md): it fires on the
     * common path, so it would add offensive rebounds, shot attempts and points across
     * every game and needs its own recalibration pass. Do not change the value here.
     */
    private final int maxOffensiveRetentionsPerPossession;

    /**
     * §3.21 (decisions.md #043 D): the defense's inside position on a free throw,
     * expressed as a multiplier on {@link #baseOffensiveRebound()} for the free-throw
     * board ONLY. A missed last free throw is live and someone rebounds it, but the
     * defense lines up inside on both blocks — the realized offensive share is ~0.19
     * against the ordinary board's 0.262.
     *
     * <p><b>A RULE, NOT A TUNABLE</b> — hence {@code public static final}: lane
     * positions on a free throw are set by the rulebook, not by an era or a coach.
     * ⚠ It therefore gets NO properties line and NO {@code calibration.md} row — a value
     * in Java <i>and</i> a properties line is the double-value trap this class's header
     * names, and a calibration row would be an unsourced target.
     *
     * <p>⚠ <b>The contest is logistic, so this is NOT the realized share</b> — set once
     * from the real ~0.19 figure, with the realized share MEASURED, never back-solved
     * (#043 D). The whole plausible spread (0.14 → 0.262) is 0.32 rebounds either way,
     * smaller than the residual it would chase: <b>do not iterate on it.</b>
     */
    public static final double FREE_THROW_REBOUND_LEAN = 0.68;

    /**
     * §3.22 (decisions.md #044 A/B): the offensive rebounder's pull on the NEXT shot —
     * a multiplier on his {@code offensiveWeight()} in {@link
     * ShotSelector#pickShooter(java.util.List, PlayerGameState, java.util.random.RandomGenerator)},
     * for that ONE draw only.
     *
     * <p><b>A WEIGHT, NOT A BRANCH, and the shot type is NOT forced.</b> {@link
     * ShotSelector#pickShotType} already bends by the shooter's own skills (#040 C) and
     * the rebounder — usually a big — leans interior on his own. A weight reproduces the
     * whole second-chance distribution (the ball is sometimes kicked out for three); a
     * branch hard-codes one leg of it. <b>Multiplicative, not additive</b> (#044 A), so
     * the rebounder keeps his <i>relative</i> standing — a weak-scoring big doubled is
     * still below a star guard. {@code 1.0} means "off".
     *
     * <p>⚠ <b>THE CONSTANT IS THE MULTIPLIER; THE SHARE IS WHAT IT REALIZES.</b> {@code
     * 2.0} yields 33.3% at five equal weights but realizes <b>35.4%</b> on the seeded
     * league. <b>Do not back-solve this from a target share</b> (#043 D's discipline).
     * It sits below real basketball's ~45–55%: a deliberate, conservative first step.
     *
     * <p>A TUNABLE, not a static: the go-back-up was a bigger share of the 1990s game
     * than the modern one, and a tendency is an era knob (#044 B).
     */
    private final double offensiveRebounderShotWeight;

    /**
     * §3.22 (decisions.md #044 E): a putback's assist chance, as a multiplier on
     * {@link #assistProbability} applied at the ONE assist site when the shooter IS the
     * player who took the offensive board. Nobody passed the rebounder his own board,
     * so crediting a teammate — measured at 66.7% of his makes — is wrong.
     *
     * <p><b>⚠ HALVED, NOT ZEROED, for a measured reason.</b> Zeroing removes every
     * rebounder-make assist (≈ −1.1/team-game) and lands a <i>real</i> miss the other
     * way, trading an unmeasurable overshoot for a measurable undershoot (#043 B). At
     * 0.5 the removal measures −0.52 and assists land 27.00.
     *
     * <p><b>A RULE, NOT A TUNABLE</b> — hence {@code public static final}, the {@link
     * #FREE_THROW_REBOUND_LEAN} shape (#043 D): nobody assists a tip-in in any era, so
     * this is model shape while the era lever is {@link
     * #offensiveRebounderShotWeight()}. ⚠ It therefore gets NO properties line and NO
     * {@code calibration.md} row — a value in Java <i>and</i> a properties line is the
     * double-value trap this class's header names.
     *
     * <p>⚠ Keys off {@code shooter == rebounder}, <b>NOT</b> "any second-chance shot" —
     * a kick-out three off an offensive rebound is an ordinary assisted basket, and
     * taxing it is the blunt rule #044 E rejected. Applied AFTER {@link
     * #clampProbability}, so the floor is not in play (0.62 × 0.5 = 0.31).
     */
    public static final double OFFENSIVE_REBOUNDER_ASSIST_LEAN = 0.5;

    /**
     * Missed-shot out of bounds (§3.8, decisions.md #026).
     * A missed shot resolves to one of FOUR outcomes in a single draw (Decision A):
     * offensive rebound / defensive rebound / OOB-offense / OOB-defense. The OOB
     * share is carved off FIRST by these flat, skill-INDEPENDENT weights (a fixed
     * defensive lean, NOT a second skilled contest — Decision A); only the clean-
     * rebound remainder runs ReboundResolver's skill contest. oobTotalWeight is
     * the fraction of missed shots that leave the court (sail-out untouched or
     * tipped out in a scramble); the two slices below split THAT share, leaning
     * defensive (a loose ball in a scrum favors the defense). MissedShotResolver
     * normalizes the two slices by their sum, so they need not add to anything in
     * particular — only their ratio and oobTotalWeight matter. Placeholders,
     * settled by the CalibrationHarness OOB line (Decision D): OOB removes some
     * second-chance possessions, so its rate must be visible and the §3.4/§3.5
     * aggregates re-confirmed. Kept smaller than the two rebound outcomes.
     */
    private final double oobTotalWeight;
    private final double oobDefenseWeight;
    private final double oobOffenseWeight;

    /**
     * Blocked shots (§3.7, decisions.md #025).
     * Per-shot-type block base rate at an average-vs-average contest (defender
     * block skill vs. shooter finishing, both 10). A block is carved off the top
     * of the shot outcome (Decision A1) before the make/miss contest runs on the
     * remainder. Ordering DRIVE ≥ POST > PERIMETER ≫ THREE (Decision C): rim
     * attempts are far more blockable than threes; THREE is very-low (a rare
     * closeout swat) but not flat-zero (which would make threes unblockable, an
     * artifact for no benefit). Placeholders — tuned empirically by the
     * CalibrationHarness toward ~5 blocks/team/game (Decision C), same as the
     * §3.4 BASE_* shot rates. Blocks reuse the global SENSITIVITY.
     */
    private final double baseBlockDrive;
    private final double baseBlockPost;
    private final double baseBlockPerimeter;
    private final double baseBlockThree;
    /**
     * Block-specific contest sensitivity (Decision B: "same logistic shape"). The
     * global SENSITIVITY (0.5) is far too steep for blocks — a real event so rare
     * that a good rim protector blocks only ~5–6% of opponent attempts, so a ±0.5
     * swing per 10 skill points swamps the thin base and drives blocks 2–3× over
     * target. Blocks therefore use their own, much gentler sensitivity: the defender
     * still matters (elite rim protectors block more, elite finishers get blocked
     * less — Decision B2) but the base rate stays the dominant term, keeping blocks
     * a thin slice off the top (Decision A1). Tuned with BASE_BLOCK_* toward ~5/team.
     */
    public static final double BLOCK_SENSITIVITY = 0.12;

    /**
     * Flat four-way loose-ball recovery weights (Decision D). After a block, a
     * single roll picks where the swatted ball goes. This is FLAT — fixed weights
     * identical for every block, no skill input — because a blocked ball is a
     * chaotic loose ball dominated by physics/chance, not the offenseRebound-vs-
     * defenseRebound box-out contest ReboundResolver models. The split is
     * defense-leaning (RECOVERED_DEFENSE > RECOVERED_OFFENSE > the two OOB slices).
     * Weights are raw (BlockResolver normalizes by their sum) and are unsourced
     * placeholders — no citable NBA block-recovery distribution exists — settled
     * by the harness against the ~5/team target. Defense keeps the ball on
     * RECOVERED_DEFENSE + OOB_DEFENSE (OOB off the shooter/offense).
     */
    private final double blockRecoveredDefense;
    private final double blockRecoveredOffense;
    private final double blockOobDefense;
    private final double blockOobOffense;

    /**
     * Turnover sub-cause weights (§3.9, decisions.md #027).
     * Once the (unchanged) turnover gate fires, TurnoverResolver.pickCause runs a
     * single weighted categorical draw over the nine TurnoverCause values — a pure
     * RE-PARTITION of a turnover that already occurred, so it can shift the mix but
     * NEVER the count (Decision A: the count is fixed by the untouched gate). These
     * are RAW base weights (pickCause normalizes them to sum to 1.0 on EACH declared
     * turnover), tiered by relative magnitude (Decision B): STOLEN dominant, then
     * high / mid / low / super-low. STOLEN is kept ~56% of the mix so BoxScore.steals
     * (the one calibrated number a turnover taxonomy could disturb) does not drift —
     * its share is the tier weight here; WHO steals is still shaped by pickStealer's
     * stealing-weighted draw. The other eight causes carve out of the old ~40%
     * LOST_BALL bucket, which is RETIRED as the catch-all. Placeholders — settled by
     * the CalibrationHarness per-cause line (Decision E) so the mix looks plausible;
     * there is NO hard per-cause target and these do NOT touch the aggregates (the
     * turnover count is free by construction). Same static-base-plus-modest-lean
     * pattern as the block-recovery / OOB weights above.
     */
    private final double toWeightStolen;
    private final double toWeightShotClock;
    private final double toWeightOffensiveFoul;
    private final double toWeightBadPass;
    private final double toWeightTravelling;
    private final double toWeightLostBallOob;
    private final double toWeightThreeSeconds;
    private final double toWeightEightSecondsBackcourt;
    private final double toWeightOverAndBack;

    /**
     * Modest avg-10 deviation sensitivity for the four LEANED causes (Decision C).
     * Only four causes scale (the rest are flat tier weights): SHOT_CLOCK_VIOLATION
     * (ball-handler acumen ↓ + defending coach defensiveScheme/defensivePressure ↑),
     * OFFENSIVE_FOUL and BAD_PASS (offense teamOffense ↓ — a poorly-coordinated
     * offense charges/throws it away more). The lean multiplies that cause's base
     * weight by (1 + TO_CAUSE_SENSITIVITY × deviation/10) before the per-turnover
     * normalization, so the leans shift the RELATIVE shares only — no lean can change
     * the turnover count (Decision C). Kept modest and single-form (the #022 shape).
     * Placeholder, settled by the harness line alongside the weights above.
     */
    public static final double TO_CAUSE_SENSITIVITY = 0.20;

    /**
     * Rebounding fouls + team-foul / bonus substrate (§3.10, decisions.md #028).
     * A non-shooting foul during the rebound phase — a defensive box-out push or an
     * offensive over-the-back. Carved off the TOP of the miss flow (Decision C, the
     * §3.7 block-carve shape): rolled BEFORE the four-way board draw and short-
     * circuiting it on a hit, so the rebound-foul rate stays independently tunable
     * and never entangles with the rebound weights.
     *
     * <p>reboundFoulBase is the per-missed-shot probability at an average-vs-average
     * contest, scaled by the same discipline/pressure inputs the shooting foul uses
     * (foulProne / defensivePressure) in the avg-10 form (#021 C / #022). It must
     * stay SMALL: every hit either hands the offense a second chance or (in the
     * bonus) two free throws, so this is the knob that drives §3.10's scoring lift.
     */
    private final double reboundFoulBase;
    /**
     * Two-sided split (Decision A2), DEFENSE-LEANING: box-out contact dominates,
     * over-the-back is the genuine minority. Raw weights — the resolver normalizes
     * by their sum, so only the ratio matters.
     */
    private final double reboundFoulDefenseWeight;
    private final double reboundFoulOffenseWeight;
    /**
     * Rebound-foul contest sensitivity — its OWN, far below the global SENSITIVITY
     * (0.5), for the same reason BLOCK_SENSITIVITY is (§3.7): at a ~0.03 base, a
     * ±0.5 swing per 10 skill points swamps the base entirely and lets skill alone
     * drive the rate several-fold over target. The skills still matter (an
     * undisciplined five fouls more on the glass) but the base stays dominant, so
     * this remains a thin, independently-tunable slice off the top (#028 C).
     */
    public static final double REBOUND_FOUL_SENSITIVITY = 0.10;

    /**
     * Team fouls per period after which the OTHER team is in the bonus (penalty) —
     * the modern-NBA 5th team foul. The predicate is DERIVED from the FOUL event log
     * (GameData.isInBonus), never stored (#028 A1); this is the only constant it
     * needs. EMIT-THEN-COUNT: the Nth foul is emitted first, so it awards the bonus
     * itself.
     */
    private final int bonusFoulsPerPeriod;

    /**
     * And-1 / shooting foul on a made basket (§3.11, decisions.md #029).
     * An and-1 is a SECOND, post-make foul roll (#029 A1) carved beside the assist:
     * the pre-shot foul branch and baseNoBasketFoul are untouched, so "P(a foul
     * stops the shot)" keeps its §3.4 meaning and this rate stays independently
     * tunable — the §3.7 block / §3.10 rebound-foul carve, a third time.
     *
     * <p>andOneBase is P(the make also drew a foul) at an average-vs-average contest,
     * rolled ONLY on a made DRIVE/POST (#029 A2 — widening to all shot types is
     * §3.12). It must stay THIN: every hit is a pure-additive point (a made FG plus
     * one FT with no offsetting removal), so this is the knob that drives §3.11's
     * scoring lift. Placeholder, settled by the CalibrationHarness and-1 line (E).
     */
    private final double andOneBase;
    /**
     * And-1 contest sensitivity — its OWN, far below the global SENSITIVITY (0.5),
     * for the same reason BLOCK_SENSITIVITY (§3.7) and REBOUND_FOUL_SENSITIVITY
     * (§3.10) are: at a thin base, a ±0.5 swing per 10 skill points swamps the base
     * and lets skill alone drive the rate several-fold over target. The skills still
     * matter (a strong foul-drawer converts more contact) but the base stays
     * dominant (#029 C). Note this rate rides rareEventProbability, NOT
     * contestProbability — the PROB_FLOOR (0.02) would make a thin base tunable
     * only UPWARD (the #028 trap), and §3.11's recalibration needs it to go down.
     */
    public static final double AND_ONE_SENSITIVITY = 0.10;
    /**
     * An and-1 is ALWAYS exactly one free throw, by rule — independent of the bonus
     * (#029 B). Threaded through awardFreeThrows as the per-situation count, the
     * same seam §3.12 will reuse to pass 3 for a fouled three.
     */
    public static final int AND_ONE_FREE_THROWS = 1;

    /**
     * Coach / chemistry modifiers (§3.4, decisions.md #022).
     * Single avg-10 deviation sensitivity shared by all coach effects
     * (pace / offensiveScheme / defensiveScheme, plus the §3.5 rotationDepth /
     * substitutionAggressiveness). attr 10 ⇒ ×1.0. Split per-effect only if
     * calibration shows one knob can't fit all effects.
     */
    public static final double COACH_SENSITIVITY = 0.20;

    /**
     * Minutes / fatigue / substitution (§3.5, decisions.md #023).
     * Foul-out disqualification limit (Decision F). A player with >= this many
     * fouls is forced off the floor and ineligible to return (a derived predicate
     * over the existing PlayerGameState.fouls counter — no stored flag).
     */
    private final int foulOutLimit;

    /**
     * Wall-clock minutes a full regulation game represents (PERIODS × MINUTES_PER
     * = 48) and per-OT (Decision A). Minutes are a possession-share projection:
     * team on-floor possessions map to (regulation + OT) minutes by ratio.
     */
    public static final int MINUTES_PER_PERIOD = 12;
    public static final int OT_MINUTES = 5;

    /**
     * Energy (Decision B): a single per-player currentEnergy, full at tip-off,
     * draining per on-floor possession and recovering while benched. Effect is one
     * fatigue multiplier over the player's skills at contest time. All within-game.
     */
    public static final double MAX_ENERGY = 100.0;
    /** At endurance 10 this is the raw drain; higher endurance drains less. */
    private final double energyDrainPerPossession;
    /**
     * Scales the drain by {@code 1 − ENDURANCE_DRAIN_SENSITIVITY × (endurance − 10)/10},
     * clamped ≥ {@link #minDrainScale} so an elite-endurance player still tires.
     */
    public static final double ENDURANCE_DRAIN_SENSITIVITY = 0.6;
    private final double minDrainScale;
    private final double energyRecoveryPerPossession;
    /**
     * At full energy the multiplier is ×1.0; at zero it falls to
     * {@code (1 − fatigueMaxPenalty)}. Tuned in §3.5 so tired players degrade visibly
     * (late-period FG% sags a touch) while staying a thumb on the scale, not a cliff.
     */
    private final double fatigueMaxPenalty;

    /** Scaled per-coach by {@code substitutionAggressiveness} — higher pulls earlier. */
    private final double baseSubEnergyThreshold;
    /**
     * Starters tolerate more fatigue: their effective threshold is LOWERED by this many
     * energy points, so a starter is pulled later than a bench player at equal energy
     * (Decision C star retention). Tuned in §3.5 to land the top starter near ~36 min.
     */
    private final double starterSubThresholdBonus;
    /**
     * Bench depth at an average (10) {@code rotationDepth} coach, scaled by
     * {@code rotationDepthFactor}. ⚠ The full squad is always available for forced
     * (foul-out) subs regardless of this.
     */
    private final int baseRotationDepth;

    /**
     * Foul trouble (§3.13, decisions.md #031 B).
     * The SOFT benching rule: a player carrying fouls is a bench CANDIDATE, and
     * whether he actually sits is a per-possession probability, not a threshold.
     *
     * <p>UNITS — read this before touching the numbers below (the #030 G lesson):
     *   foulTroubleSitProbabilities()[f] is a PROBABILITY (per rotation check) that
     *   an average-value player under an average (10) substitutionAggressiveness
     *   coach is benched at foul count f. It is NOT a multiplier.
     *   FOUL_TROUBLE_VALUE_SENSITIVITY is a MULTIPLIER sensitivity (avg-10
     *   deviation form) — it scales the probability above, it is not one.
     *
     * <p>Shape (#031 B, the user's stated intent and the acceptance criterion): one
     * curve scaled by the coach factor, NOT three thresholds — a high-aggressiveness
     * coach starts thinking about sitting at 3, a medium one at 4, a low one at 5.
     * So index 3 is deliberately small (only an aggressive coach's multiplier lifts
     * it to something that fires often) and index 5 is high (nearly everyone sits).
     * Counts 0–2 are ZERO: no coach benches a player for 2 fouls. Index 6 is
     * foulOutLimit — a foul-out is the HARD rule's business, not this one.
     *
     * <p>These are per-CHECK probabilities, re-decided ~100 times per team per game, so
     * even a small value fires reliably; the curve decides HOW EARLY, not whether.
     *
     * <p>⚠ THE CURVE IS SATURATED — do not reach for it to move foul-outs further. §3.13
     * measured it: {0.25, 0.75, 0.95} gives 0.407 and {0.40, 0.90, 0.98} gives 0.382 —
     * ~60% more substitutions for nothing. The binding constraint is that #031 D sends
     * the player BACK into the same over-dispersed defender draw that gave him the
     * fouls. Below ~0.38 needs a different lever — see #031's implementation note.
     */
    private final double[] foulTroubleSitProbabilities;

    /**
     * How strongly the player's VALUE composite (PlayerGameState.valueComposite())
     * bends the sit probability, in the avg-10 deviation form the rest of this class
     * uses: factor = 1 + FOUL_TROUBLE_VALUE_SENSITIVITY × (value − 10)/10.
     *
     * <p>DIRECTION IS DELIBERATE AND INVERTS THE FATIGUE RULE (#031 B): a POSITIVE
     * sensitivity means a BETTER player is MORE likely to be sat at the same foul
     * count. You ride your star when he's tired (starterSubThresholdBonus lets
     * starters tolerate more fatigue); you PROTECT him when he's in foul trouble.
     * This looks like an inconsistency and is not — do not "fix" it.
     */
    public static final double FOUL_TROUBLE_VALUE_SENSITIVITY = 0.55;

    /**
     * The extra protection the roster signal adds on top of the value composite
     * (#031 B: the two signals are COMBINED, not substituted). A starter is a player
     * the coach has already committed to, so he is managed a little more tightly
     * still; bench players get a mild discount that deepens down the rotationOrder
     * queue, capped so a deep reserve is never fully exempt.
     */
    private final double foulTroubleStarterBonus;
    private final double foulTroubleBenchDiscountPerSlot;
    private final double foulTroubleMinRosterFactor;

    /**
     * How much fresher (energy points) a bench player must be before the SOFT
     * foul-trouble rule will sit an on-floor player for him. This is the
     * anti-oscillation guard, and it is what makes #031 D's sticky sit actually
     * stick in BOTH directions without a stored flag or a countdown.
     *
     * <p>Why it is needed: a player benched for foul trouble rests to full, returns via the
     * ordinary freshness path, and is briefly barely less fresh than the bench. With NO
     * margin the rule re-fires at once and he flickers on and off (measured in §3.13).
     *
     * <p>⚠ NOT "as large as possible" — a LARGER margin makes foul-outs WORSE by blocking
     * legitimate sits. Swept: 12 → 0.53 (vetoing ~69% of fired rolls), 8 → 0.46,
     * 3 → 0.45, 1 → 0.38, but 0 → 0.40 (a bare `>` swaps for a replacement fresher by a
     * rounding error). 1.0 is the floor of the useful band.
     *
     * <p>⚠ It sits BELOW one possession's drain (2.6), so it does not by itself hold a
     * returning player on the floor — the anti-oscillation guarantee comes from this
     * margin PLUS a returning player entering at a full tank, and is pinned by
     * RotationStateTest's average-sit-length assertion, not by this constant's size.
     *
     * <p>A margin, in energy points — NOT a probability and NOT a multiplier.
     */
    private final double foulTroubleFreshnessMargin;

    /**
     * Technical fouls (§3.14a, decisions.md #032 B2/E/F).
     *
     * <p>⚠ THIS IS A PER-TEAM-PER-GAME RATE, NOT A PER-CHECK PROBABILITY. It sits in a
     * file where nearly every double is a probability, so read it as what it is: the
     * number of technical fouls one team is expected to commit in one game. The
     * per-check probability the engine actually rolls is ~0.00175 and is DERIVED, by
     * technicalFoulProbability() below (which also explains why that is half the
     * ~0.0035 #032 B2 estimated).
     *
     * <p>Stored this way deliberately (#032 B2): 0.00175 has no intuitive meaning, cannot
     * be sanity-checked by eye, is not comparable to anything in calibration.md, and
     * would silently drift if defaultPossessionsPerPeriod or PERIODS ever moved.
     * 0.35 is the number a tuner reasons about.
     *
     * <p>Set from the user's real-world figure of 0.6–0.8 technicals per GAME
     * league-wide, i.e. ~0.3–0.4 per TEAM per game. UNSOURCED, like every row in
     * calibration.md — §3.16's job (1) owns verifying it.
     *
     * <p>A ballpark, NOT a target (#032 J): nothing in the engine is tuned toward it —
     * the constant is set from the real-world figure directly, so the harness line is
     * a correctness check that the roll fires at the rate configured, not a
     * calibration objective. Judge it at 5 SEEDS ONLY (11.8% relative sd at 102
     * games; 5.3% at 5 seeds).
     */
    private final double technicalFoulsPerTeamGame;

    /**
     * Two technicals in one game is an automatic ejection (#032 F). A monotonic
     * counter exactly like foulOutLimit, which is why PlayerGameState.isEjected()
     * is a DERIVED predicate with no stored flag — #023 F's discipline applies
     * unchanged. (The stored-state exception belongs to §3.14b's flagrant-2, which
     * is a severity grade with no counter behind it.)
     */
    public static final int TECHNICAL_EJECTION_LIMIT = 2;

    /**
     * A technical is exactly ONE free throw (FREE_THROWS_PER_FOUL is 2,
     * AND_ONE_FREE_THROWS is 1). Named separately rather than sharing AND_ONE's
     * constant: the two are 1 for unrelated reasons, and §3.12 already showed what
     * happens when one count is assumed to follow another (#030 C).
     */
    public static final int TECHNICAL_FREE_THROWS = 1;

    /**
     * Flagrant fouls (§3.14b, decisions.md #034 A/E/G).
     *
     * <p>⚠ THIS IS A PER-TEAM-PER-GAME RATE, NOT A PER-FOUL PROBABILITY — the same
     * shape as technicalFoulsPerTeamGame above, and read the same way: the
     * number of flagrant fouls one team is expected to commit in one game. The
     * per-foul probability the engine actually rolls is ~0.0084 and is DERIVED, by
     * flagrantFoulProbability() below.
     *
     * <p>Stored this way deliberately (#032 B2's argument, #034 G): 0.0084 has no
     * intuitive meaning, cannot be sanity-checked by eye, and is not comparable to
     * anything in calibration.md. 0.16 is the number a tuner reasons about.
     *
     * <p>Back-solved from the real-world figure of ~0.25-0.40 flagrants per GAME
     * league-wide, i.e. ~0.13-0.20 per TEAM per game. UNSOURCED, like every row in
     * calibration.md — §3.16's job (1) owns verifying it.
     *
     * <p>A ballpark, NOT a target (#034 H): nothing in the engine is tuned toward it —
     * the constant is set from the real-world figure directly, so the harness line is
     * a correctness check that the roll fires at the rate configured, not a
     * calibration objective. JUDGE IT AT 5 SEEDS ONLY — at 102 games this is ~33
     * events (relative sd 17.4%) and at 5 seeds ~166 (7.8%), the COARSEST row in
     * calibration.md. A single-seed reading is useless.
     */
    private final double flagrantFoulsPerTeamGame;

    /**
     * What fraction of flagrants are FLAGRANT-2 — the grade that ejects immediately
     * (#034 E). A flat CONDITIONAL SHARE, rolled only once a flagrant has already
     * happened, with no causal input: the engine cannot distinguish excessive from
     * ordinary contact, and foulProne has already had its say in who was selected as
     * the committer.
     *
     * <p>⚠ NOT a probability in the clamped sense — it is a share of an already-rare
     * parent event, so it needs NO clamp (neither clampProbability nor
     * clampRareProbability). It inherits the parent rate's resolvability rather than
     * having its own: a separately-tuned flagrant-2 RATE was rejected because ~5
     * events per 102-game run cannot be resolved at any seed count (#034 E).
     */
    private final double flagrantTwoShare;

    /**
     * Shooting-foul composition (§3.16, decisions.md #039 A/B/D/E).
     *
     * <p>What fraction of the fouls FoulResolver.isFoul has ALREADY rolled and charged
     * are NON-SHOOTING fouls rather than SHOOTING_FOUL. A SECOND roll layered
     * on the foul, the isFlagrant shape three fields up: isFoul keeps its rate, skills
     * and RNG draw, recordFoul() has already run, so THE FOUL TOTAL HOLDS BY
     * CONSTRUCTION and this knob re-partitions the outcome only (#039 A).
     *
     * <p>⚠ Key and event outcome agree — both NON_SHOOTING_FOUL since §3.17 (#040 M).
     * COMMON_FOUL survives nowhere in engine logic, but it IS still in game_event.outcome
     * for pre-§3.17 games; see PossessionEngine.NON_SHOOTING_FOUL_OUTCOME for that
     * no-migration cutover.
     *
     * <p>⚠ PRICED BY THE PENALTY RATE, not the foul rate alone (§3.16's execution finding):
     * inside the bonus a non-shooting foul still awards 2 FTs, so what a conversion
     * removes depends on how often teams are in the penalty. Any pass moving the foul
     * rate must RE-CHECK FTA rather than assume this value still lands it.
     *
     * <p>⚠ BACK-SOLVED, NOT SOURCED (#039 D) — the real NBA share needs play-by-play
     * derivation; this is the value that lands a SOURCED FTA (~23.5), a weaker claim.
     * ⚠ TUNE AGAINST THE MEASURED FTA LINE, never against points: the landing is the
     * target, not the constant (#039 D predicted 0.43 and it came out 0.50).
     *
     * <p>NO skill input by design (#039 E) — see FoulResolver.isNonShootingFoul.
     */
    private final double nonShootingFoulShare;

    /**
     * §3.17 (decisions.md #040 C/J/K): THE SHOT-MIX SHARE TABLE — the league's base
     * distribution over the four ShotTypes, as RAW WEIGHTS normalized at the call site
     * (the to-weight-* / block-* / oob-* convention: only RATIOS matter, so a profile
     * author never has to make them sum to 1).
     *
     * <p>⚠ THE MIX IS NO LONGER EMERGENT, AND THAT IS A REAL TRADE (#040 C). The league
     * imposes the base mix and players bend it via the skill modifier; a future
     * player-generation change moves a PLAYER's share within the mix but no longer moves
     * the LEAGUE's. Accepted because an emergent property nobody can tune is not a
     * feature when it is emergently wrong. See PlayerGameState.shotTypeWeight for the
     * formula this replaced and why it was wrong.
     *
     * <p>⚠ THESE ARE SHARES OF *DRAWS*, NOT OF CHARGED ATTEMPTS (#040 E). A stopped shot
     * charges NO FGA, and foul-mult-three (0.133) is 7.5x below DRIVE's 1.0, so a three
     * is stopped far less often and converts to a charged attempt at a higher rate. The
     * three DRAW share must therefore sit BELOW the target share of ATTEMPTS: do not set
     * these to the target percentages and expect them back out. TUNE AGAINST THE
     * MEASURED 3PA LINE AT 5 SEEDS — the landing is the target, not the constant.
     */
    private final double shotShareDrive;
    private final double shotSharePerimeter;
    private final double shotSharePost;
    private final double shotShareThree;

    /**
     * §3.17 (decisions.md #040 C/J): how hard a shooter's own skill bends the league
     * share table — the avg-10 deviation multiplier the whole engine already uses,
     * {@code 1 + SHOT_MIX_SENSITIVITY * (skill - 10) / 10}.
     *
     * <p><b>Model machinery, NOT a tunable</b> — the class of {@link #BLOCK_SENSITIVITY}
     * and {@link #AND_ONE_SENSITIVITY}, so it stays {@code public static final} and out
     * of the properties file (CLAUDE.md's declaration-form convention, #040 J).
     *
     * <p>Its job is that shot selection keeps reading the player model: without it every
     * player on the floor shoots the identical mix, a {@code longRange}-19 sniper and a
     * {@code longRange}-6 centre alike. <b>A shooter's skills still decide who shoots
     * what; they no longer decide what the LEAGUE shoots</b> (#040 C).
     *
     * <p>⚠ <b>UNSOURCED FEEL CONSTANT whose error is INVISIBLE IN THE AGGREGATES BY
     * CONSTRUCTION</b> (#040 C trade-off): the shares are normalized, so raising or
     * lowering this moves who takes which shot without moving the league mix at all.
     * The only honest check is per-player, not aggregate — verify a high-{@code
     * longRange} specialist visibly out-shoots a low-{@code longRange} big without
     * either reaching a degenerate share.
     */
    public static final double SHOT_MIX_SENSITIVITY = 0.5;

    /**
     * ONE flagrant-2 is an automatic ejection (#034 E/F). Named rather than inlined as
     * `>= 1` so the third disqualification threshold reads identically to the other two
     * (foulOutLimit = 6, TECHNICAL_EJECTION_LIMIT = 2) — the shape is the point:
     * PlayerGameState.isEjectedForFlagrant() is a DERIVED predicate over a monotonic
     * counter, and a limit of 1 changes nothing about that. It is what makes the
     * stored-state exception predicted by #031 H / #032 F unnecessary a second time.
     */
    public static final int FLAGRANT_EJECTION_LIMIT = 1;

    /**
     * A flagrant is exactly TWO free throws, at every site and for both grades (#034
     * C/E). Named separately rather than sharing FREE_THROWS_PER_FOUL (also 2): the
     * two are 2 for unrelated reasons, and §3.12 already showed what happens when one
     * count is assumed to follow another (#030 C) — the same argument that gave
     * TECHNICAL_FREE_THROWS its own name.
     *
     * <p>⚠ THIS REPLACES THE UNDERLYING FOUL'S AWARD, IT DOES NOT ADD TO IT. A flagrant
     * stopped THREE is 2 FTs (not 3, not 5); a flagrant and-1 is 2 (not 1 + 2). See
     * PossessionEngine.awardFlagrant.
     */
    public static final int FLAGRANT_FREE_THROWS = 2;

    /**
     * The divisor for flagrantFoulProbability() — personal fouls per team per game.
     *
     * <p>⚠ A NAMED CONSTANT, NOT A MAGIC NUMBER, because it is an ASSUMPTION ABOUT THE
     * ENGINE'S CURRENT BEHAVIOR rather than a rule: it must be greppable when a pass
     * invalidates it. Measured, not configured — the harness's `Fouls / team / game`
     * MINUS the technicals on that line (it tallies ALL FOUL events), i.e. the
     * personal-foul rate alone.
     *
     * <p>⚠ NOT A TUNABLE, AND NOT ALLOWED TO DRIFT EITHER (#034 G). THE RULE: any pass
     * that moves the foul rate must RE-MEASURE this deliberately and record the result,
     * even if the answer is "unchanged". It has fired for four consecutive phases and
     * moved on three of them (19.0 -> 20.15 -> 17.82 -> 18.52 -> 19.08; §3.22 measured
     * 19.015 and left it as noise). engine-traps.md carries the chain and its causes.
     *
     * <p>⚠ IT MOVES INDIRECTLY, WHICH IS WHY THE RULE EXISTS. §3.17 touched no foul
     * constant at all and still moved it -11.5%, by shifting draws from DRIVE/POST
     * (foul-mult 1.0) to THREE (0.133). Anything touching base-no-basket-foul or the
     * shot mix moves it. A re-partition like NON_SHOOTING_FOUL does NOT — that re-labels
     * an already-charged foul (#039 A).
     *
     * <p>⚠ A STALE DIVISOR FAILS SILENTLY (#032 B2) — nothing asserts it. The tell is in
     * the harness's FLAGRANTS row before you look at anything else: at a stale 20.15
     * against a real 17.82 it read 0.119 vs a ~0.16 ballpark, exactly the 74% ratio.
     * Measure as: all FOUL events MINUS technicals (the harness line tallies both;
     * flagrants REPLACE a foul event and are already inside it).
     */
    public static final double PERSONAL_FOULS_PER_TEAM_GAME = 19.08;

    /**
     * At an average passing supporting cast — the other 4 offensive players ≈ 10, NOT
     * the shooter. Tuned in §3.4 toward ~26 assists/team/game.
     */
    private final double baseAssist;
    public static final double ASSIST_SENSITIVITY = 0.30;

    /**
     * acumen → a small shot-make-probability bonus (better shot selection ⇒
     * higher-quality looks). Modest thumb on the scale, not a shot-type reweight.
     */
    public static final double ACUMEN_SENSITIVITY = 0.05;
    /**
     * teamOffense/teamDefense → a single possession-level efficiency multiplier on
     * the shot make rate (offense lifts, defense suppresses). Modest.
     */
    public static final double TEAM_EFFICIENCY_SENSITIVITY = 0.08;

    /**
     * The avg-10 deviation multiplier shared by all coach effects (Decision A):
     * {@code 1 + COACH_SENSITIVITY × (attr − 10) / 10}. A null attribute (coach
     * not hydrated) is treated as league-average ⇒ ×1.0.
     */
    public double coachModifier(Integer attr) {
        double a = (attr == null) ? SCALE_AVG : attr.doubleValue();
        return 1.0 + COACH_SENSITIVITY * (a - SCALE_AVG) / SCALE_AVG;
    }

    /**
     * Probability that a made field goal is assisted, given the average passing
     * of the supporting cast (the four non-shooter offensive players). Average
     * passing (10) yields {@code sim.base-assist}; better-passing casts assist more.
     */
    public double assistProbability(double supportingCastPassing) {
        double p = baseAssist
                + ASSIST_SENSITIVITY * (supportingCastPassing - SCALE_AVG) / SCALE_AVG;
        return clampProbability(p);
    }

    /**
     * Combined chemistry multiplier on a shot's make probability (Decision C):
     * the shooter's {@code acumen} (shot-quality nudge) and the team-efficiency
     * differential ({@code teamOffense} − opponent {@code teamDefense}), each via
     * the avg-10 deviation form. Average inputs ⇒ ×1.0.
     */
    public double chemistryMakeMultiplier(double acumen,
                                          double teamOffense, double oppTeamDefense) {
        double acumenFactor = 1.0 + ACUMEN_SENSITIVITY * (acumen - SCALE_AVG) / SCALE_AVG;
        double teamFactor = 1.0
                + TEAM_EFFICIENCY_SENSITIVITY * (teamOffense - oppTeamDefense) / SCALE_AVG;
        return acumenFactor * teamFactor;
    }

    /**
     * §3.5 (Decision B): the energy a player loses for one on-floor possession,
     * scaled by endurance — a high-endurance player drains slower. The scale is
     * the avg-10 deviation form, floored at {@link #minDrainScale()} so even an
     * elite-endurance player still tires (just far more slowly).
     */
    public double energyDrain(double endurance) {
        double scale = 1.0 - ENDURANCE_DRAIN_SENSITIVITY * (endurance - SCALE_AVG) / SCALE_AVG;
        scale = Math.max(minDrainScale, scale);
        return energyDrainPerPossession * scale;
    }

    /**
     * §3.5 (Decision B): the fatigue multiplier over a player's skills at contest
     * time. Full energy ⇒ ×1.0; as energy falls to 0 the multiplier falls linearly
     * to {@code 1 − fatigueMaxPenalty}. A modest thumb on the scale, not a cliff.
     */
    public double fatigueFactor(double energy) {
        double frac = Math.max(0.0, Math.min(1.0, energy / MAX_ENERGY));
        return 1.0 - fatigueMaxPenalty * (1.0 - frac);
    }

    /**
     * §3.5 (Decision D): the avg-10 deviation multiplier for a rotation coach
     * attribute (rotationDepth / substitutionAggressiveness), reusing the single
     * {@link #COACH_SENSITIVITY}. attr 10 (or null coach) ⇒ ×1.0. Used by
     * {@link CoachModifiers} to precompute the two rotation factors.
     */
    public double rotationModifier(Integer attr) {
        return coachModifier(attr);
    }

    /**
     * §3.5 (Decision D): how many bench players (drawn down the {@code
     * rotationOrder} queue) this coach uses for fatigue subs — {@link
     * #BASE_ROTATION_DEPTH} scaled by the coach's rotationDepth factor, at least 1.
     * Forced (foul-out) subs may still reach the full roster regardless of this.
     */
    public int rotationDepth(double rotationDepthFactor) {
        int depth = (int) Math.round(baseRotationDepth * rotationDepthFactor);
        return Math.max(1, depth);
    }

    /**
     * §3.5 (Decisions C/D): the energy threshold below which an on-floor player is
     * a fatigue-sub candidate. The base is scaled up by the coach's
     * substitutionAggressiveness factor (a more aggressive coach pulls earlier ⇒
     * higher threshold); starters get a lower effective threshold (tolerate more
     * fatigue — Decision C star retention).
     */
    public double subEnergyThreshold(double subAggressivenessFactor, boolean starter) {
        double threshold = baseSubEnergyThreshold * subAggressivenessFactor;
        if (starter) {
            threshold -= starterSubThresholdBonus;
        }
        return threshold;
    }

    /**
     * §3.13 (decisions.md #031 B): the probability — <b>per rotation check</b> —
     * that a coach benches this player for foul trouble. Returns a PROBABILITY in
     * [0, 1], not a multiplier.
     *
     * <p>{@code base(foulCount) × coachFactor × valueFactor × rosterFactor}:
     * <ul>
     *   <li><b>base</b> — {@link #foulTroubleSitProbabilities()}, zero below 3 fouls
     *       and at the foul-out limit (6 is the hard rule's business, not this one).</li>
     *   <li><b>coach</b> — {@code CoachModifiers.subAggressivenessFactor()}, so one
     *       curve expresses "aggressive coaches think about it at 3, average at 4,
     *       passive at 5" without three thresholds.</li>
     *   <li><b>value</b> — the avg-10 deviation over the player's
     *       {@code valueComposite()}. <b>Positive sensitivity: better players are
     *       benched MORE readily</b>, the deliberate inverse of the fatigue rule's
     *       starter tolerance (#031 B).</li>
     *   <li><b>roster</b> — the {@code lineupRole}/{@code rotationOrder} signal,
     *       <b>combined with</b> the composite rather than replacing it (#031 B).</li>
     * </ul>
     *
     * <p>Deliberately NOT run through {@link #clampProbability} — its {@link
     * #PROB_FLOOR} would give a 0-foul player a 2% chance of being benched every
     * check (~100 per game), which is the opposite of the intent. It uses {@link
     * #clampRareProbability} instead; §3.14a (#032 H) consolidated this, the third
     * floor-free site, with the other three, closing #030's clamp-helper follow-up.
     */
    public double foulTroubleSitProbability(int foulCount, double subAggressivenessFactor,
                                            double valueComposite, boolean starter,
                                            Integer rotationOrder) {
        if (foulCount < 0 || foulCount >= foulTroubleSitProbabilities.length) {
            return 0.0;
        }
        double base = foulTroubleSitProbabilities[foulCount];
        if (base <= 0.0) {
            return 0.0;
        }
        double valueFactor = 1.0
                + FOUL_TROUBLE_VALUE_SENSITIVITY * (valueComposite - SCALE_AVG) / SCALE_AVG;
        double p = base * subAggressivenessFactor * Math.max(0.0, valueFactor)
                * rosterProtectionFactor(starter, rotationOrder);
        return clampRareProbability(p);
    }

    /**
     * §3.13 (decisions.md #031 B): the {@code lineupRole}/{@code rotationOrder} half
     * of the foul-trouble value signal. A starter — a player the coach has already
     * committed to — is managed slightly more tightly; a bench player is discounted
     * progressively down the rotationOrder queue, floored at {@link
     * #FOUL_TROUBLE_MIN_ROSTER_FACTOR} so a deep reserve is protected less, never
     * exempt. Combined with (not substituted for) the skill composite.
     */
    public double rosterProtectionFactor(boolean starter, Integer rotationOrder) {
        if (starter) {
            return 1.0 + foulTroubleStarterBonus;
        }
        int slot = (rotationOrder == null) ? 1 : Math.max(1, rotationOrder);
        double factor = 1.0 - foulTroubleBenchDiscountPerSlot * slot;
        return Math.max(foulTroubleMinRosterFactor, factor);
    }

    /**
     * §3.9 (decisions.md #027 C): the modest avg-10 lean multiplier on a leaned
     * turnover cause's base weight. {@code deviation} is the amount the driving
     * attribute sits BELOW average in the weaker-forces-more direction (e.g.
     * {@code 10 − acumen} or {@code 10 − teamOffense}), so a below-average input
     * yields a factor &gt; 1.0 (that cause gets a larger share) and an above-average
     * input yields &lt; 1.0. Floored at a small positive value so a lean can shrink
     * but never zero-out (or negate) a cause's weight — normalization then divides
     * by the per-turnover total. This shifts the RELATIVE mix only; it cannot change
     * the turnover count (that is fixed by the untouched gate, Decision A).
     */
    public double turnoverCauseLean(double deviation) {
        double factor = 1.0 + TO_CAUSE_SENSITIVITY * deviation / SCALE_AVG;
        return Math.max(0.05, factor);
    }

    public double clampProbability(double p) {
        return Math.max(PROB_FLOOR, Math.min(PROB_CEILING, p));
    }

    /**
     * §3.14a (decisions.md #032 H): clamp a probability to [0, {@link
     * #PROB_CEILING}] — the <b>floor-free</b> sibling of {@link #clampProbability}, and
     * the single owner of an expression previously hand-rolled at four sites.
     *
     * <p><b>⚠ Why {@link #PROB_FLOOR} must not apply to a rare event.</b> The global
     * floor (0.02) exists so a skill mismatch cannot make a <i>normal</i> outcome
     * impossible. On a deliberately-rare carve it does the opposite: it becomes a floor
     * the base cannot go below, so the constant is tunable only UPWARD and a "turn it
     * down" recalibration silently does nothing. The margin is not subtle — the
     * per-check technical rate is ~0.00175, so the floor is >10× the rate itself. It
     * also destroys the off-switch a 0.0 multiplier gives {@link FoulResolver#isFoul}
     * (#030 A1).
     *
     * <p>It is fine — intended, even — that a floor-free rate can leave some players
     * effectively never committing the event. No floor should manufacture a minimum.
     */
    public double clampRareProbability(double p) {
        return Math.max(0.0, Math.min(PROB_CEILING, p));
    }

    /**
     * §3.14a (decisions.md #032 B/B2): the probability that <b>this team commits a
     * technical foul on this rotation check</b> — {@link
     * #TECHNICAL_FOULS_PER_TEAM_GAME} divided down by the nominal number of checks in
     * a game. Expect <b>~0.00175</b>.
     *
     * <p><b>⚠ THE ×2 IS LOAD-BEARING</b>, and corrects #032 B2's "~0.0035": {@link
     * PossessionEngine#simulate} advances BOTH teams' rotations on EVERY possession, so
     * a team gets ~200 checks per game, not the ~100 the design assumed. A divisor of
     * 100 would double technicals to ~0.7 per team per game.
     *
     * <p><b>No contest — a positive design claim, not a simplification (#032 B).</b>
     * Every other foul hangs off a contest; a technical has none to hang off. No skills,
     * no coach factor, no game situation: a blowout and a rivalry produce technicals at
     * the same rate. {@code foulProne} weights only WHICH of the five wears it (#032 C).
     *
     * <p><b>⚠ The divisor is NOMINAL, so a harness landing a few percent off the
     * constant is NOT a bug and NOT drift</b> — real possession counts are pace-scaled
     * and overtime adds more, so a fast game draws slightly more technicals. Do not
     * back-solve the constant against that gap.
     *
     * <p>Clamped through {@link #clampRareProbability}: {@link #PROB_FLOOR} (0.02) is
     * more than ten times this rate and would make the constant tunable only upward
     * (#032 H).
     */
    public double technicalFoulProbability() {
        int nominalChecks = defaultPossessionsPerPeriod * PERIODS * 2;
        return clampRareProbability(technicalFoulsPerTeamGame / nominalChecks);
    }

    /**
     * §3.14b (decisions.md #034 A/G): the probability that <b>a foul that has already
     * happened was a FLAGRANT</b> — {@link #flagrantFoulsPerTeamGame()} divided down
     * by the personal-foul rate. Expect <b>~0.0084</b> (0.16 / 19.0).
     *
     * <p><b>A severity roll layered ON TOP of an existing foul, not a foul rate</b> —
     * asked only after a foul has already been returned, so no existing rate moves by
     * construction (#034 A). The roll fires per foul, which is why the divisor is the
     * FOUL count: any other would break the moment the foul rate moved.
     *
     * <p><b>⚠ THE DIVISOR IS EMERGENT, SO THIS IS NOT A STANDALONE DIAL (#034 G).</b>
     * Unlike {@link #technicalFoulProbability}'s nominal, config-derived divisor, {@link
     * #PERSONAL_FOULS_PER_TEAM_GAME} is MEASURED — so any pass that moves the foul rate
     * moves the flagrant rate too, without touching {@link #FLAGRANT_FOULS_PER_TEAM_GAME}.
     * Directionally correct (more fouls, more chances for one to be excessive), but the
     * coupling has to be stated rather than discovered.
     *
     * <p>Clamped through {@link #clampRareProbability} — flooring would inflate flagrants
     * and make the constant tunable only upward (the #028 trap). ⚠ The margin here is
     * thin: {@link #PROB_FLOOR} (0.02) is only ~2.4× this rate, against >10× for
     * technicals, so a future rate increase could reach where this choice stops being
     * obviously right.
     */
    public double flagrantFoulProbability() {
        return clampRareProbability(
                flagrantFoulsPerTeamGame / PERSONAL_FOULS_PER_TEAM_GAME);
    }

    /**
     * §3.10 (decisions.md #028 C): the probability of a deliberately-RARE carved-off
     * event, contested in the usual avg-10 form but clamped WITHOUT the {@link
     * #PROB_FLOOR} — see {@link #clampRareProbability} for why the floor is wrong
     * here. {@link #reboundFoulBase()} sits at ~0.03, close enough to 0.02 for the floor
     * to bite. Same class of problem {@link #BLOCK_SENSITIVITY} solved for §3.7 — a
     * global constant tuned for common events being wrong for a rare one.
     */
    public double rareEventProbability(double base, double drivingSkill,
                                       double opposingSkill, double sensitivity) {
        double p = base + sensitivity * (drivingSkill - opposingSkill) / SCALE_AVG;
        return clampRareProbability(p);
    }

    public double contestProbability(double base, double offenseSkill, double defenseSkill) {
        double p = base + SENSITIVITY * (offenseSkill - defenseSkill) / SCALE_AVG;
        return clampProbability(p);
    }

    /**
     * §3.7 (decisions.md #025 B2): the probability a shot is blocked — the same
     * avg-10 logistic contest as {@link #contestProbability}, but with the
     * DEFENDER as the driving side: {@code base + SENSITIVITY × (defenderBlockSkill
     * − shooterFinishing)/10}. A great finisher gets blocked less than a scrub
     * against the same rim protector. {@code base} is the per-shot-type
     * {@code BASE_BLOCK_*}; a great rim protector vs. an average finisher lands
     * above base, an average-vs-average contest lands exactly at base.
     */
    public double blockProbability(double base, double defenderBlockSkill,
                                   double shooterFinishing) {
        double p = base + BLOCK_SENSITIVITY * (defenderBlockSkill - shooterFinishing) / SCALE_AVG;
        return clampProbability(p);
    }

    public double freeThrowProbability(double freeThrowSkill) {
        double p = ftBase + (freeThrowSkill - SCALE_AVG) / SCALE_AVG * FT_SENSITIVITY;
        return clampProbability(p);
    }

    /**
     * Constructor binding is what lets every field be final with no initializer.
     * Accessor names map to keys directly: baseThree() -> sim.base-three.
     */

    public SimConfig(
            @Positive int defaultPossessionsPerPeriod,
            @Positive int otPossessionsPerPeriod,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseDrive,
            @DecimalMin("0.0") @DecimalMax("1.0") double basePerimeter,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseThree,
            @DecimalMin("0.0") @DecimalMax("1.0") double basePost,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseTurnover,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseNoBasketFoul,
            @DecimalMin("0.0") double foulMultDrive,
            @DecimalMin("0.0") double foulMultPost,
            @DecimalMin("0.0") double foulMultPerimeter,
            @DecimalMin("0.0") double foulMultThree,
            @DecimalMin("0.0") @DecimalMax("1.0") double ftBase,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseOffensiveRebound,
            @DecimalMin("0.0") double offensiveRebounderShotWeight,
            @Positive int maxOffensiveRetentionsPerPossession,
            @DecimalMin("0.0") @DecimalMax("1.0") double oobTotalWeight,
            @DecimalMin("0.0") double oobDefenseWeight,
            @DecimalMin("0.0") double oobOffenseWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseBlockDrive,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseBlockPost,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseBlockPerimeter,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseBlockThree,
            @DecimalMin("0.0") double blockRecoveredDefense,
            @DecimalMin("0.0") double blockRecoveredOffense,
            @DecimalMin("0.0") double blockOobDefense,
            @DecimalMin("0.0") double blockOobOffense,
            @DecimalMin("0.0") double toWeightStolen,
            @DecimalMin("0.0") double toWeightShotClock,
            @DecimalMin("0.0") double toWeightOffensiveFoul,
            @DecimalMin("0.0") double toWeightBadPass,
            @DecimalMin("0.0") double toWeightTravelling,
            @DecimalMin("0.0") double toWeightLostBallOob,
            @DecimalMin("0.0") double toWeightThreeSeconds,
            @DecimalMin("0.0") double toWeightEightSecondsBackcourt,
            @DecimalMin("0.0") double toWeightOverAndBack,
            @DecimalMin("0.0") @DecimalMax("1.0") double reboundFoulBase,
            @DecimalMin("0.0") double reboundFoulDefenseWeight,
            @DecimalMin("0.0") double reboundFoulOffenseWeight,
            @Positive int bonusFoulsPerPeriod,
            @DecimalMin("0.0") @DecimalMax("1.0") double andOneBase,
            @Positive int foulOutLimit,
            @DecimalMin("0.0") double energyDrainPerPossession,
            @DecimalMin("0.0") @DecimalMax("1.0") double minDrainScale,
            @DecimalMin("0.0") double energyRecoveryPerPossession,
            @DecimalMin("0.0") @DecimalMax("1.0") double fatigueMaxPenalty,
            @DecimalMin("0.0") double baseSubEnergyThreshold,
            @DecimalMin("0.0") double starterSubThresholdBonus,
            @Positive int baseRotationDepth,
            @NotNull @Size(min = 7, max = 7, message = "sim.foul-trouble-sit-probabilities must have exactly 7 entries (foul counts 0-6)") double[] foulTroubleSitProbabilities,
            @DecimalMin("0.0") double foulTroubleStarterBonus,
            @DecimalMin("0.0") double foulTroubleBenchDiscountPerSlot,
            @DecimalMin("0.0") double foulTroubleMinRosterFactor,
            @DecimalMin("0.0") double foulTroubleFreshnessMargin,
            @DecimalMin("0.0") double technicalFoulsPerTeamGame,
            @DecimalMin("0.0") double flagrantFoulsPerTeamGame,
            @DecimalMin("0.0") @DecimalMax("1.0") double flagrantTwoShare,
            @DecimalMin("0.0") @DecimalMax("1.0") double nonShootingFoulShare,
            @DecimalMin("0.0") double shotShareDrive,
            @DecimalMin("0.0") double shotSharePerimeter,
            @DecimalMin("0.0") double shotSharePost,
            @DecimalMin("0.0") double shotShareThree,
            @DecimalMin("0.0") @DecimalMax("1.0") double baseAssist) {
        this.defaultPossessionsPerPeriod = defaultPossessionsPerPeriod;
        this.otPossessionsPerPeriod = otPossessionsPerPeriod;
        this.baseDrive = baseDrive;
        this.basePerimeter = basePerimeter;
        this.baseThree = baseThree;
        this.basePost = basePost;
        this.baseTurnover = baseTurnover;
        this.baseNoBasketFoul = baseNoBasketFoul;
        this.foulMultDrive = foulMultDrive;
        this.foulMultPost = foulMultPost;
        this.foulMultPerimeter = foulMultPerimeter;
        this.foulMultThree = foulMultThree;
        this.ftBase = ftBase;
        this.baseOffensiveRebound = baseOffensiveRebound;
        this.offensiveRebounderShotWeight = offensiveRebounderShotWeight;
        this.maxOffensiveRetentionsPerPossession = maxOffensiveRetentionsPerPossession;
        this.oobTotalWeight = oobTotalWeight;
        this.oobDefenseWeight = oobDefenseWeight;
        this.oobOffenseWeight = oobOffenseWeight;
        this.baseBlockDrive = baseBlockDrive;
        this.baseBlockPost = baseBlockPost;
        this.baseBlockPerimeter = baseBlockPerimeter;
        this.baseBlockThree = baseBlockThree;
        this.blockRecoveredDefense = blockRecoveredDefense;
        this.blockRecoveredOffense = blockRecoveredOffense;
        this.blockOobDefense = blockOobDefense;
        this.blockOobOffense = blockOobOffense;
        this.toWeightStolen = toWeightStolen;
        this.toWeightShotClock = toWeightShotClock;
        this.toWeightOffensiveFoul = toWeightOffensiveFoul;
        this.toWeightBadPass = toWeightBadPass;
        this.toWeightTravelling = toWeightTravelling;
        this.toWeightLostBallOob = toWeightLostBallOob;
        this.toWeightThreeSeconds = toWeightThreeSeconds;
        this.toWeightEightSecondsBackcourt = toWeightEightSecondsBackcourt;
        this.toWeightOverAndBack = toWeightOverAndBack;
        this.reboundFoulBase = reboundFoulBase;
        this.reboundFoulDefenseWeight = reboundFoulDefenseWeight;
        this.reboundFoulOffenseWeight = reboundFoulOffenseWeight;
        this.bonusFoulsPerPeriod = bonusFoulsPerPeriod;
        this.andOneBase = andOneBase;
        this.foulOutLimit = foulOutLimit;
        this.energyDrainPerPossession = energyDrainPerPossession;
        this.minDrainScale = minDrainScale;
        this.energyRecoveryPerPossession = energyRecoveryPerPossession;
        this.fatigueMaxPenalty = fatigueMaxPenalty;
        this.baseSubEnergyThreshold = baseSubEnergyThreshold;
        this.starterSubThresholdBonus = starterSubThresholdBonus;
        this.baseRotationDepth = baseRotationDepth;
        this.foulTroubleSitProbabilities = foulTroubleSitProbabilities;
        this.foulTroubleStarterBonus = foulTroubleStarterBonus;
        this.foulTroubleBenchDiscountPerSlot = foulTroubleBenchDiscountPerSlot;
        this.foulTroubleMinRosterFactor = foulTroubleMinRosterFactor;
        this.foulTroubleFreshnessMargin = foulTroubleFreshnessMargin;
        this.technicalFoulsPerTeamGame = technicalFoulsPerTeamGame;
        this.flagrantFoulsPerTeamGame = flagrantFoulsPerTeamGame;
        this.flagrantTwoShare = flagrantTwoShare;
        this.nonShootingFoulShare = nonShootingFoulShare;
        this.shotShareDrive = shotShareDrive;
        this.shotSharePerimeter = shotSharePerimeter;
        this.shotSharePost = shotSharePost;
        this.shotShareThree = shotShareThree;
        this.baseAssist = baseAssist;
    }

    /** sim.default-possessions-per-period */
    public int defaultPossessionsPerPeriod() {
        return defaultPossessionsPerPeriod;
    }

    /** sim.ot-possessions-per-period */
    public int otPossessionsPerPeriod() {
        return otPossessionsPerPeriod;
    }

    /** sim.base-drive */
    public double baseDrive() {
        return baseDrive;
    }

    /** sim.base-perimeter */
    public double basePerimeter() {
        return basePerimeter;
    }

    /** sim.base-three */
    public double baseThree() {
        return baseThree;
    }

    /** sim.base-post */
    public double basePost() {
        return basePost;
    }

    /** sim.base-turnover */
    public double baseTurnover() {
        return baseTurnover;
    }

    /** sim.base-no-basket-foul */
    public double baseNoBasketFoul() {
        return baseNoBasketFoul;
    }

    /** sim.foul-mult-drive */
    public double foulMultDrive() {
        return foulMultDrive;
    }

    /** sim.foul-mult-post */
    public double foulMultPost() {
        return foulMultPost;
    }

    /** sim.foul-mult-perimeter */
    public double foulMultPerimeter() {
        return foulMultPerimeter;
    }

    /** sim.foul-mult-three */
    public double foulMultThree() {
        return foulMultThree;
    }

    /** sim.ft-base */
    public double ftBase() {
        return ftBase;
    }

    /** sim.base-offensive-rebound */
    public double baseOffensiveRebound() {
        return baseOffensiveRebound;
    }

    /**
     * sim.offensive-rebounder-shot-weight — §3.22 (#044 A/B): the multiplier on the
     * offensive rebounder's {@code offensiveWeight()} for the NEXT {@code pickShooter}
     * draw only. See the field's javadoc: the realized share is what this produces, not
     * what it sets, and it must not be back-solved from one.
     */
    public double offensiveRebounderShotWeight() {
        return offensiveRebounderShotWeight;
    }

    /** sim.max-offensive-retentions-per-possession */
    public int maxOffensiveRetentionsPerPossession() {
        return maxOffensiveRetentionsPerPossession;
    }

    /** sim.oob-total-weight */
    public double oobTotalWeight() {
        return oobTotalWeight;
    }

    /** sim.oob-defense-weight */
    public double oobDefenseWeight() {
        return oobDefenseWeight;
    }

    /** sim.oob-offense-weight */
    public double oobOffenseWeight() {
        return oobOffenseWeight;
    }

    /** sim.base-block-drive */
    public double baseBlockDrive() {
        return baseBlockDrive;
    }

    /** sim.base-block-post */
    public double baseBlockPost() {
        return baseBlockPost;
    }

    /** sim.base-block-perimeter */
    public double baseBlockPerimeter() {
        return baseBlockPerimeter;
    }

    /** sim.base-block-three */
    public double baseBlockThree() {
        return baseBlockThree;
    }

    /** sim.block-recovered-defense */
    public double blockRecoveredDefense() {
        return blockRecoveredDefense;
    }

    /** sim.block-recovered-offense */
    public double blockRecoveredOffense() {
        return blockRecoveredOffense;
    }

    /** sim.block-oob-defense */
    public double blockOobDefense() {
        return blockOobDefense;
    }

    /** sim.block-oob-offense */
    public double blockOobOffense() {
        return blockOobOffense;
    }

    /** sim.to-weight-stolen */
    public double toWeightStolen() {
        return toWeightStolen;
    }

    /** sim.to-weight-shot-clock */
    public double toWeightShotClock() {
        return toWeightShotClock;
    }

    /** sim.to-weight-offensive-foul */
    public double toWeightOffensiveFoul() {
        return toWeightOffensiveFoul;
    }

    /** sim.to-weight-bad-pass */
    public double toWeightBadPass() {
        return toWeightBadPass;
    }

    /** sim.to-weight-travelling */
    public double toWeightTravelling() {
        return toWeightTravelling;
    }

    /** sim.to-weight-lost-ball-oob */
    public double toWeightLostBallOob() {
        return toWeightLostBallOob;
    }

    /** sim.to-weight-three-seconds */
    public double toWeightThreeSeconds() {
        return toWeightThreeSeconds;
    }

    /** sim.to-weight-eight-seconds-backcourt */
    public double toWeightEightSecondsBackcourt() {
        return toWeightEightSecondsBackcourt;
    }

    /** sim.to-weight-over-and-back */
    public double toWeightOverAndBack() {
        return toWeightOverAndBack;
    }

    /** sim.rebound-foul-base */
    public double reboundFoulBase() {
        return reboundFoulBase;
    }

    /** sim.rebound-foul-defense-weight */
    public double reboundFoulDefenseWeight() {
        return reboundFoulDefenseWeight;
    }

    /** sim.rebound-foul-offense-weight */
    public double reboundFoulOffenseWeight() {
        return reboundFoulOffenseWeight;
    }

    /** sim.bonus-fouls-per-period */
    public int bonusFoulsPerPeriod() {
        return bonusFoulsPerPeriod;
    }

    /** sim.and-one-base */
    public double andOneBase() {
        return andOneBase;
    }

    /** sim.foul-out-limit */
    public int foulOutLimit() {
        return foulOutLimit;
    }

    /** sim.energy-drain-per-possession */
    public double energyDrainPerPossession() {
        return energyDrainPerPossession;
    }

    /** sim.min-drain-scale */
    public double minDrainScale() {
        return minDrainScale;
    }

    /** sim.energy-recovery-per-possession */
    public double energyRecoveryPerPossession() {
        return energyRecoveryPerPossession;
    }

    /** sim.fatigue-max-penalty */
    public double fatigueMaxPenalty() {
        return fatigueMaxPenalty;
    }

    /** sim.base-sub-energy-threshold */
    public double baseSubEnergyThreshold() {
        return baseSubEnergyThreshold;
    }

    /** sim.starter-sub-threshold-bonus */
    public double starterSubThresholdBonus() {
        return starterSubThresholdBonus;
    }

    /** sim.base-rotation-depth */
    public int baseRotationDepth() {
        return baseRotationDepth;
    }

    /** sim.foul-trouble-sit-probabilities */
    public double[] foulTroubleSitProbabilities() {
        return foulTroubleSitProbabilities;
    }

    /** sim.foul-trouble-starter-bonus */
    public double foulTroubleStarterBonus() {
        return foulTroubleStarterBonus;
    }

    /** sim.foul-trouble-bench-discount-per-slot */
    public double foulTroubleBenchDiscountPerSlot() {
        return foulTroubleBenchDiscountPerSlot;
    }

    /** sim.foul-trouble-min-roster-factor */
    public double foulTroubleMinRosterFactor() {
        return foulTroubleMinRosterFactor;
    }

    /** sim.foul-trouble-freshness-margin */
    public double foulTroubleFreshnessMargin() {
        return foulTroubleFreshnessMargin;
    }

    /** sim.technical-fouls-per-team-game */
    public double technicalFoulsPerTeamGame() {
        return technicalFoulsPerTeamGame;
    }

    /** sim.flagrant-fouls-per-team-game */
    public double flagrantFoulsPerTeamGame() {
        return flagrantFoulsPerTeamGame;
    }

    /** sim.flagrant-two-share */
    public double flagrantTwoShare() {
        return flagrantTwoShare;
    }

    /** sim.non-shooting-foul-share */
    public double nonShootingFoulShare() {
        return nonShootingFoulShare;
    }

    /** sim.shot-share-drive — §3.17 (#040 C). A RAW WEIGHT, normalized at the call site. */
    public double shotShareDrive() {
        return shotShareDrive;
    }

    /** sim.shot-share-perimeter — §3.17 (#040 C). A RAW WEIGHT, normalized at the call site. */
    public double shotSharePerimeter() {
        return shotSharePerimeter;
    }

    /** sim.shot-share-post — §3.17 (#040 C). A RAW WEIGHT, normalized at the call site. */
    public double shotSharePost() {
        return shotSharePost;
    }

    /** sim.shot-share-three — §3.17 (#040 C). A RAW WEIGHT, normalized at the call site. */
    public double shotShareThree() {
        return shotShareThree;
    }

    /**
     * §3.17 (decisions.md #040 C): the league's base weight for one {@link ShotType},
     * before the shooter's skill modifier and before the coach's lean. RAW — the caller
     * normalizes, so only ratios matter.
     */
    public double shotShare(ShotType type) {
        return switch (type) {
            case DRIVE -> shotShareDrive;
            case PERIMETER -> shotSharePerimeter;
            case POST -> shotSharePost;
            case THREE -> shotShareThree;
        };
    }

    /** sim.base-assist */
    public double baseAssist() {
        return baseAssist;
    }

    /**
     * §3.15 (decisions.md #035 A/E): a {@code SimConfig} carrying the BASELINE
     * profile, for code outside a Spring context — the ~10 unit tests that used to
     * write {@code new SimConfig()} when every constant was a static.
     *
     * <p><b>It reads {@code application-baseline.properties} — the same file, through
     * the same Spring binder, that the application context binds from.</b> That is the
     * whole point: the values exist in exactly ONE place (#035 A), so a test using this
     * factory and a running engine can never disagree. Hard-coding the 63 values here
     * would reintroduce precisely the second source of truth this design eliminates,
     * and every test would still pass.
     *
     * <p>Not cached: a {@code SimConfig} is immutable, tests build one per class, and
     * a shared mutable static would undercut the per-simulation-input direction #035 B
     * chose instance state for.
     */
    public static SimConfig baseline() {
        Properties props = new Properties();
        try (InputStream in = new ClassPathResource(BASELINE_PROFILE_RESOURCE).getInputStream()) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read " + BASELINE_PROFILE_RESOURCE
                            + " — it is the only copy of the 63 profilable constants", e);
        }
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new PropertiesPropertySource("baseline", props));
        return new Binder(ConfigurationPropertySources.get(env))
                .bind("sim", SimConfig.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not bind sim.* from " + BASELINE_PROFILE_RESOURCE));
    }

    /** The baseline profile file — the single copy of the 63 profilable values. */
    public static final String BASELINE_PROFILE_RESOURCE = "application-baseline.properties";
}
