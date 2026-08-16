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
 * <p><b>§3.4 calibration (decisions.md #022, Decision D).</b> The shot/turnover
 * base rates were tuned empirically via {@code CalibrationHarness} (102 games over
 * the H2-seeded league) toward the agreed modern-NBA benchmarks. The landing spot:
 * <pre>
 *   Points/team 111.7 (~112) | FG% 47.1% (~47) | 3P% 34.9% (~36)
 *   Assists/team 27.0 (~26)  | Turnovers/team 15.3 (~14)
 * </pre>
 * Turnovers settle around 15 rather than 14: each possession can run several
 * turnover checks (second-chance possessions after offensive rebounds re-roll the
 * full flow), so the per-check {@code sim.base-turnover} hits diminishing returns
 * below ~0.04 — pushing it lower distorts the steal distribution for &lt;1 TO of
 * gain. 15.3 is within ~10% of target, accepted. Re-run the harness after any
 * change here to re-observe the aggregates (it is disabled in the normal build).
 *
 * <p><b>§3.5 calibration (decisions.md #023, Decision E).</b> With fatigue +
 * substitution on, the §3.4 aggregates still hold (harness, 102 games):
 * <pre>
 *   Points/team 112.4 | FG% 47.2% | 3P% 35.4% | Assists 27.6 | Turnovers 13.4
 * </pre>
 * and the minutes distribution lands on the user-agreed §3.5 targets — top starter
 * ~37, no one over ~42, benches scaling down (34/32/29/27/24/22/20/16). Note: the
 * period-by-period FG% stays roughly flat rather than sagging late — this is the
 * correct emergent behavior, not a miss: substitution pulls tired legs and cycles
 * fresh ones in, so the on-floor FG% holds even as {@code sim.fatigue-max-penalty}
 * bites harder. Fatigue shows up as <i>who is on the floor</i> (the minutes curve),
 * and it degrades players who <i>stay</i> on tired (thin benches, foul trouble,
 * exhausted deep-bench late games).
 *
 * <p><b>§3.7 calibration (decisions.md #025).</b> Blocks convert some would-be
 * makes into blocks, so scoring dropped and one recalibration pass followed. The
 * shot {@code BASE_*} rates were nudged up to refill the removed points and the
 * {@code BASE_BLOCK_*} rates + {@link #BLOCK_SENSITIVITY} tuned toward ~5 blocks/
 * team. The landing spot (harness, 102 games):
 * <pre>
 *   Points/team 112.9 | FG% 47.2% | 3P% 36.0% | Assists 27.1 | Turnovers 13.5
 *   Blocks/team 4.8 (~5)
 * </pre>
 * The §3.4/§3.5 aggregates and the §3.5 minutes distribution still hold. Note the
 * block contest needed its OWN sensitivity ({@link #BLOCK_SENSITIVITY}, far below
 * the global {@link #SENSITIVITY}): blocks are rare enough that the global 0.5
 * makes a good rim protector block ~23% of shots, so skilled defenders alone drove
 * blocks 2–3× over target regardless of the thin base — see the constant's note.
 *
 * <p><b>§3.10 calibration (decisions.md #028, Decision E).</b> Rebounding fouls add
 * bonus free throws AND extra retained possessions, so — unlike §3.8/§3.9 — §3.10
 * was NOT free: +2.7 pts before tuning, of which only ~1.1 was the bonus FTs. Two
 * findings worth keeping:
 * <ul>
 *   <li>{@link #baseNoBasketFoul()} (then named {@code BASE_FOUL}) is a
 *       <b>counter-intuitive lever that moves points the
 *       WRONG way</b> — trimming it 0.15→0.138 <i>raised</i> scoring, because a
 *       shooting foul ENDS a possession for ~1.5 expected FT points, which is worth
 *       less than the live shot attempt it replaces at this FG%. Do not reach for it
 *       to remove points.</li>
 *   <li>The recalibration used the <b>§3.7 lever in reverse</b> — the shot
 *       {@code BASE_*} rates trimmed ~1.2% (the same knob §3.7 nudged UP to refill
 *       points blocks removed).</li>
 * </ul>
 * The landing spot, agreed with the user at 113.8 rather than chasing 112.9 exactly
 * (trimming further pulls FG% below its calibrated target — a bad trade):
 * <pre>
 *   Points/team 113.8 | FG% 46.9% | 3P% 37.6% | Assists 27.7 | Turnovers 14.2
 *   Blocks/team 5.0   | OOB 2.8
 *   Fouls 3.95/team/period | 37.1% of team-periods in the penalty
 *   Rebounding fouls 1.75 def + 0.48 off | Bonus FTA 0.8/team/game
 * </pre>
 * Turnovers 13.5→14.2 is an emergent §3.5 effect, not drift: more glass scrambles ⇒
 * more possessions played ⇒ more fatigue ⇒ worse {@code ballSecurity} in
 * {@code isTurnover}'s fatigue-scaled contest (and 14.2 is closer to the ~14 target).
 *
 * <p><b>§3.11 calibration (decisions.md #029, Decision E).</b> And-1s add free throws
 * on top of shots that already scored, with <b>no offsetting removal</b> — a
 * pure-additive lift, and the harness confirmed the prediction exactly: of the +2.4
 * pts the untuned placeholder added, <b>essentially all of it was the FT channel</b>
 * (the mirror image of §3.10, where retained possessions dominated — an and-1 never
 * forks a possession, so there is no retention channel). The recalibration was
 * therefore a <b>lever choice</b>, and the finding worth keeping is this:
 * <ul>
 *   <li>The placeholder {@code AND_ONE_BASE = 0.11} was wrong on <b>realism</b>
 *       independent of points (11.4% of made contact FG vs. a real ~4–6%), so
 *       trimming it to <b>0.055</b> fixed the rate AND removed ~0.9 of the lift —
 *       a lever §3.10 did not have.</li>
 *   <li>The shot {@code BASE_*} lever costs <b>~0.6% FG% per 1.0 point</b> removed
 *       here — a worse exchange rate than §3.10's, precisely BECAUSE this lift is
 *       free throws, which cost no FG%. Spending calibrated FG% to hide it (only for
 *       §3.12 to re-tune the same number) was rejected: <b>no {@code BASE_*} trim was
 *       taken</b> (user call), leaving points knowingly high for §3.12 to re-center
 *       once. {@link #baseNoBasketFoul()} was again NOT touched (see §3.10
 *       above).</li>
 * </ul>
 * The landing (harness, ~102 games × <b>5 seeds, tuned to the mean</b> — a single run
 * carries enough per-seed noise to bait an over-correction):
 * <pre>
 *   Points/team 115.9 | FG% 46.8% | 3P% 37.6% | Assists 27.5 | Turnovers 13.9
 *   Blocks/team 4.9   | OOB 2.8
 *   And-1s 1.67/team/game (6.4% of made contact FG)
 *   FT sources: SHOOTING 90.6% | AND_ONE 5.8% | BONUS 3.6%
 *   Fouls 4.41/team/period | 43.4% of team-periods in the penalty
 * </pre>
 * FG% and 3P% sit ON their §3.4 targets — that is what the no-trim call bought.
 * <b>Points are deliberately ~3.9 above the ~112 target; that is deferred debt for
 * §3.12, not drift.</b>
 */
@ConfigurationProperties(prefix = "sim")
@Validated
public class SimConfig {

    // Two kinds of constant live here, and the declaration form tells them apart:
    //
    //   public static final       a RULE of basketball or the SHAPE of the model.
    //                             Not a knob. 26 of them.
    //   private final + accessor  a tunable knob, bound by Spring from
    //                             application-baseline.properties. 57 of them.
    //
    // The instance fields take NO INITIALIZERS: the values exist only in the
    // properties file, and a missing key fails the context at startup. Adding a
    // default back here would create a second source of truth (javac rejects it
    // outright, since the fields are final and constructor-assigned).
    //
    // Never add a public static final alias for a tunable constant: javac inlines
    // constant variables into each caller at that caller's compile time, so an
    // alias becomes a stale second value with no link to the real one.
    //
    // SCALE_AVG and MAX_ENERGY are scale DEFINITIONS, not knobs - each is the
    // denominator that gives its family meaning.

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

    // --- Foul base rate: P(a foul STOPPED the shot) on a DRIVE (§3.12 anchor) ---
    // RENAMED from BASE_FOUL by §3.12 (#030 F). The VALUE NEVER MOVED — it is the
    // same §3.4-calibrated 0.15, under a name that says which outcome it governs.
    // "BASE_FOUL" read as "the foul rate" and is not that: it is the possession-
    // ENDING branch (foul, no basket, go to the line), which is exactly why
    // trimming it moves points the WRONG way (#028's measured finding, re-warned in
    // #029). Its pair is AND_ONE_BASE — no basket vs. basket-plus-one.
    //
    // Since §3.12 this is the DRIVE rate and the anchor for the FOUL_MULT_* table
    // below (#030 A2): every shot type's stopped-shot probability is
    // BASE_NO_BASKET_FOUL × FOUL_MULT_<type>. It is deliberately NOT re-derived as
    // a league-wide average — keeping it the drive rate is what makes drive/post
    // behavior bit-identical to §3.11 and §3.12's whole delta attributable to
    // perimeter/three.
    private final double baseNoBasketFoul;

    // --- Per-shot-type foul multipliers (§3.12, decisions.md #030 A1/A2/B) ---
    //
    // ⚠ THESE ARE MULTIPLIERS, NOT PROBABILITIES. They sit beside
    // BASE_NO_BASKET_FOUL = 0.15, where every value looks like a probability, so
    // read them carefully: FOUL_MULT_THREE = 0.133 does NOT mean "13.3% of threes
    // are fouled" — it means "a three draws contact at 13.3% of the rate a drive
    // does", i.e. 0.15 × 0.133 = 0.02 = 2%. (This misreading happened once during
    // the design pass; hence the shouting.)
    //
    // §3.12 DELETED the binary ShotType.isContactType() (#030 A1): before it, a
    // PERIMETER or THREE could not draw a shooting foul or an and-1 at any rate.
    // Now every type can, at a GRADUATED rate — post/drive frequent, perimeter
    // uncommon, three rare (a closeout on a three-point shooter is a real foul).
    // The rate carries the information the gate used to carry, so there is one
    // mechanism instead of a gate plus a rate (the #013/#015 single-source rule).
    //
    // ANCHORED at DRIVE = 1.0 on the untouched BASE_NO_BASKET_FOUL (#030 A2), NOT
    // re-derived as a new league-wide base. The consequence is the point: drive and
    // post foul rates are numerically IDENTICAL to §3.11, so §3.12's entire delta
    // is isolated to the two types that previously could not foul at all — a
    // decomposable, attributable change on the harness. Re-deriving the base would
    // have entangled a calibrated-number re-solve with two new scoring sources in
    // one measurement (the #029 A1 trap).
    //
    // ONE SHARED TABLE drives BOTH foul rolls (#030 B) — FoulResolver.isFoul (the
    // shot was stopped) and FoulResolver.isAndOne (the shot went in anyway). A shot
    // type's propensity to draw contact is a property of THE SHOT, not of which
    // roll is asking. Do NOT add a second, steeper and-1 table: an and-1 on a three
    // being rarer than a foul on a three ALREADY falls out of the two rolls being
    // independent (the shot must also go in), so modeling it again double-counts
    // it. Note the multiplier therefore lands TWICE in the and-1 path (scaling both
    // the stopped-shot roll the shot must survive and the and-1 roll itself), which
    // makes these knobs NON-LINEAR on and-1s: halving FOUL_MULT_THREE more than
    // halves the three's and-1 rate.
    //
    // A multiplier of exactly 0.0 is the ONE true off-switch for a shot type — it
    // scales the whole probability, skill term included. A zero BASE does NOT
    // switch a rare event off (#029's measured finding): base + sensitivity ×
    // (driving − opposing)/10 stays positive off the skill term alone whenever the
    // shooter is favored. Since §3.12 removed the caller's gate, the multiplier is
    // the only remaining honest off-switch.
    //
    // Raising DRIVE above 1.0 would break the attributability A2 was chosen for; if
    // drives should foul more, that is a BASE_NO_BASKET_FOUL conversation, and it
    // reopens a §3.4-calibrated number.
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

    // --- Rebounding (§3.3) ---
    // Base offensive-rebound rate at an average-vs-average contest (NBA ~25–28%).
    // Tuned empirically in §3.4. Rebound contests reuse the global SENSITIVITY.
    private final double baseOffensiveRebound;
    // The ONLY bound on PossessionEngine.resolvePossession's second-chance
    // `while (true)` loop: how many times the offense may keep the ball and run the
    // flow again within one possession. Once reached, the retaining path is refused
    // and the possession ends (a missed shot is forced to a defensive rebound; a
    // §3.14b flagrant still awards its free throws, then ends it).
    //
    // ⚠ RENAMED from MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION (2026-08, post-§3.14b) —
    // a pure rename, the value is UNCHANGED at its §3.3-era calibrated 3. The old
    // name described only the original §3.3 path, but FIVE paths now count against
    // this cap and FOUR of them are not rebounds: §3.7 block recovery, §3.8
    // OOB-offense, §3.10's rebounding foul and §3.14b's flagrant retention. Every one
    // of them is a RETENTION — the offense kept the ball — which is the word the rest
    // of the engine already uses (BlockRecovery/MissedShotOutcome/ReboundFoulResult
    // all expose offenseRetains()). The local counter in resolvePossession was
    // renamed to `offensiveRetentions` in the same change; PlayerGameState's
    // `offensiveRebounds` is the BOX-SCORE STAT and is deliberately untouched.
    //
    // Raising it 3 → 5 is a SEPARATE, parked tuning idea (ideas.md): it fires on the
    // common path, so it would add offensive rebounds, shot attempts and points across
    // every game and needs its own recalibration pass. Do not change the value here.
    private final int maxOffensiveRetentionsPerPossession;

    // --- Missed-shot out of bounds (§3.8, decisions.md #026) ---
    // A missed shot resolves to one of FOUR outcomes in a single draw (Decision A):
    // offensive rebound / defensive rebound / OOB-offense / OOB-defense. The OOB
    // share is carved off FIRST by these flat, skill-INDEPENDENT weights (a fixed
    // defensive lean, NOT a second skilled contest — Decision A); only the clean-
    // rebound remainder runs ReboundResolver's skill contest. oobTotalWeight is
    // the fraction of missed shots that leave the court (sail-out untouched or
    // tipped out in a scramble); the two slices below split THAT share, leaning
    // defensive (a loose ball in a scrum favors the defense). MissedShotResolver
    // normalizes the two slices by their sum, so they need not add to anything in
    // particular — only their ratio and oobTotalWeight matter. Placeholders,
    // settled by the CalibrationHarness OOB line (Decision D): OOB removes some
    // second-chance possessions, so its rate must be visible and the §3.4/§3.5
    // aggregates re-confirmed. Kept smaller than the two rebound outcomes.
    private final double oobTotalWeight;
    private final double oobDefenseWeight;
    private final double oobOffenseWeight;

    // --- Blocked shots (§3.7, decisions.md #025) ---
    // Per-shot-type block base rate at an average-vs-average contest (defender
    // block skill vs. shooter finishing, both 10). A block is carved off the top
    // of the shot outcome (Decision A1) before the make/miss contest runs on the
    // remainder. Ordering DRIVE ≥ POST > PERIMETER ≫ THREE (Decision C): rim
    // attempts are far more blockable than threes; THREE is very-low (a rare
    // closeout swat) but not flat-zero (which would make threes unblockable, an
    // artifact for no benefit). Placeholders — tuned empirically by the
    // CalibrationHarness toward ~5 blocks/team/game (Decision C), same as the
    // §3.4 BASE_* shot rates. Blocks reuse the global SENSITIVITY.
    private final double baseBlockDrive;
    private final double baseBlockPost;
    private final double baseBlockPerimeter;
    private final double baseBlockThree;
    // Block-specific contest sensitivity (Decision B: "same logistic shape"). The
    // global SENSITIVITY (0.5) is far too steep for blocks — a real event so rare
    // that a good rim protector blocks only ~5–6% of opponent attempts, so a ±0.5
    // swing per 10 skill points swamps the thin base and drives blocks 2–3× over
    // target. Blocks therefore use their own, much gentler sensitivity: the defender
    // still matters (elite rim protectors block more, elite finishers get blocked
    // less — Decision B2) but the base rate stays the dominant term, keeping blocks
    // a thin slice off the top (Decision A1). Tuned with BASE_BLOCK_* toward ~5/team.
    public static final double BLOCK_SENSITIVITY = 0.12;

    // Flat four-way loose-ball recovery weights (Decision D). After a block, a
    // single roll picks where the swatted ball goes. This is FLAT — fixed weights
    // identical for every block, no skill input — because a blocked ball is a
    // chaotic loose ball dominated by physics/chance, not the offenseRebound-vs-
    // defenseRebound box-out contest ReboundResolver models. The split is
    // defense-leaning (RECOVERED_DEFENSE > RECOVERED_OFFENSE > the two OOB slices).
    // Weights are raw (BlockResolver normalizes by their sum) and are unsourced
    // placeholders — no citable NBA block-recovery distribution exists — settled
    // by the harness against the ~5/team target. Defense keeps the ball on
    // RECOVERED_DEFENSE + OOB_DEFENSE (OOB off the shooter/offense).
    private final double blockRecoveredDefense;
    private final double blockRecoveredOffense;
    private final double blockOobDefense;
    private final double blockOobOffense;

    // --- Turnover sub-cause weights (§3.9, decisions.md #027) ---
    // Once the (unchanged) turnover gate fires, TurnoverResolver.pickCause runs a
    // single weighted categorical draw over the nine TurnoverCause values — a pure
    // RE-PARTITION of a turnover that already occurred, so it can shift the mix but
    // NEVER the count (Decision A: the count is fixed by the untouched gate). These
    // are RAW base weights (pickCause normalizes them to sum to 1.0 on EACH declared
    // turnover), tiered by relative magnitude (Decision B): STOLEN dominant, then
    // high / mid / low / super-low. STOLEN is kept ~56% of the mix so BoxScore.steals
    // (the one calibrated number a turnover taxonomy could disturb) does not drift —
    // its share is the tier weight here; WHO steals is still shaped by pickStealer's
    // stealing-weighted draw. The other eight causes carve out of the old ~40%
    // LOST_BALL bucket, which is RETIRED as the catch-all. Placeholders — settled by
    // the CalibrationHarness per-cause line (Decision E) so the mix looks plausible;
    // there is NO hard per-cause target and these do NOT touch the aggregates (the
    // turnover count is free by construction). Same static-base-plus-modest-lean
    // pattern as the block-recovery / OOB weights above.
    private final double toWeightStolen;
    private final double toWeightShotClock;
    private final double toWeightOffensiveFoul;
    private final double toWeightBadPass;
    private final double toWeightTravelling;
    private final double toWeightLostBallOob;
    private final double toWeightThreeSeconds;
    private final double toWeightEightSecondsBackcourt;
    private final double toWeightOverAndBack;

    // Modest avg-10 deviation sensitivity for the four LEANED causes (Decision C).
    // Only four causes scale (the rest are flat tier weights): SHOT_CLOCK_VIOLATION
    // (ball-handler acumen ↓ + defending coach defensiveScheme/defensivePressure ↑),
    // OFFENSIVE_FOUL and BAD_PASS (offense teamOffense ↓ — a poorly-coordinated
    // offense charges/throws it away more). The lean multiplies that cause's base
    // weight by (1 + TO_CAUSE_SENSITIVITY × deviation/10) before the per-turnover
    // normalization, so the leans shift the RELATIVE shares only — no lean can change
    // the turnover count (Decision C). Kept modest and single-form (the #022 shape).
    // Placeholder, settled by the harness line alongside the weights above.
    public static final double TO_CAUSE_SENSITIVITY = 0.20;

    // --- Rebounding fouls + team-foul / bonus substrate (§3.10, decisions.md #028) ---
    // A non-shooting foul during the rebound phase — a defensive box-out push or an
    // offensive over-the-back. Carved off the TOP of the miss flow (Decision C, the
    // §3.7 block-carve shape): rolled BEFORE the four-way board draw and short-
    // circuiting it on a hit, so the rebound-foul rate stays independently tunable
    // and never entangles with the rebound weights.
    //
    // reboundFoulBase is the per-missed-shot probability at an average-vs-average
    // contest, scaled by the same discipline/pressure inputs the shooting foul uses
    // (foulProne / defensivePressure) in the avg-10 form (#021 C / #022). It must
    // stay SMALL: every hit either hands the offense a second chance or (in the
    // bonus) two free throws, so this is the knob that drives §3.10's scoring lift.
    private final double reboundFoulBase;
    // Two-sided split (Decision A2), DEFENSE-LEANING: box-out contact dominates,
    // over-the-back is the genuine minority. Raw weights — the resolver normalizes
    // by their sum, so only the ratio matters.
    private final double reboundFoulDefenseWeight;
    private final double reboundFoulOffenseWeight;
    // Rebound-foul contest sensitivity — its OWN, far below the global SENSITIVITY
    // (0.5), for the same reason BLOCK_SENSITIVITY is (§3.7): at a ~0.03 base, a
    // ±0.5 swing per 10 skill points swamps the base entirely and lets skill alone
    // drive the rate several-fold over target. The skills still matter (an
    // undisciplined five fouls more on the glass) but the base stays dominant, so
    // this remains a thin, independently-tunable slice off the top (#028 C).
    public static final double REBOUND_FOUL_SENSITIVITY = 0.10;

    // Team fouls per period after which the OTHER team is in the bonus (penalty) —
    // the modern-NBA 5th team foul. The predicate is DERIVED from the FOUL event log
    // (GameData.isInBonus), never stored (#028 A1); this is the only constant it
    // needs. EMIT-THEN-COUNT: the Nth foul is emitted first, so it awards the bonus
    // itself.
    private final int bonusFoulsPerPeriod;

    // --- And-1 / shooting foul on a made basket (§3.11, decisions.md #029) ---
    // An and-1 is a SECOND, post-make foul roll (#029 A1) carved beside the assist:
    // the pre-shot foul branch and baseNoBasketFoul are untouched, so "P(a foul
    // stops the shot)" keeps its §3.4 meaning and this rate stays independently
    // tunable — the §3.7 block / §3.10 rebound-foul carve, a third time.
    //
    // andOneBase is P(the make also drew a foul) at an average-vs-average contest,
    // rolled ONLY on a made DRIVE/POST (#029 A2 — widening to all shot types is
    // §3.12). It must stay THIN: every hit is a pure-additive point (a made FG plus
    // one FT with no offsetting removal), so this is the knob that drives §3.11's
    // scoring lift. Placeholder, settled by the CalibrationHarness and-1 line (E).
    private final double andOneBase;
    // And-1 contest sensitivity — its OWN, far below the global SENSITIVITY (0.5),
    // for the same reason BLOCK_SENSITIVITY (§3.7) and REBOUND_FOUL_SENSITIVITY
    // (§3.10) are: at a thin base, a ±0.5 swing per 10 skill points swamps the base
    // and lets skill alone drive the rate several-fold over target. The skills still
    // matter (a strong foul-drawer converts more contact) but the base stays
    // dominant (#029 C). Note this rate rides rareEventProbability, NOT
    // contestProbability — the PROB_FLOOR (0.02) would make a thin base tunable
    // only UPWARD (the #028 trap), and §3.11's recalibration needs it to go down.
    public static final double AND_ONE_SENSITIVITY = 0.10;
    // An and-1 is ALWAYS exactly one free throw, by rule — independent of the bonus
    // (#029 B). Threaded through awardFreeThrows as the per-situation count, the
    // same seam §3.12 will reuse to pass 3 for a fouled three.
    public static final int AND_ONE_FREE_THROWS = 1;

    // --- Coach / chemistry modifiers (§3.4, decisions.md #022) ---
    // Single avg-10 deviation sensitivity shared by all coach effects
    // (pace / offensiveScheme / defensiveScheme, plus the §3.5 rotationDepth /
    // substitutionAggressiveness). attr 10 ⇒ ×1.0. Split per-effect only if
    // calibration shows one knob can't fit all effects.
    public static final double COACH_SENSITIVITY = 0.20;

    // --- Minutes / fatigue / substitution (§3.5, decisions.md #023) ---
    // Foul-out disqualification limit (Decision F). A player with >= this many
    // fouls is forced off the floor and ineligible to return (a derived predicate
    // over the existing PlayerGameState.fouls counter — no stored flag).
    private final int foulOutLimit;

    // Wall-clock minutes a full regulation game represents (PERIODS × MINUTES_PER
    // = 48) and per-OT (Decision A). Minutes are a possession-share projection:
    // team on-floor possessions map to (regulation + OT) minutes by ratio.
    public static final int MINUTES_PER_PERIOD = 12;
    public static final int OT_MINUTES = 5;

    // Energy (Decision B): a single per-player currentEnergy, full at tip-off,
    // draining per on-floor possession and recovering while benched. Effect is one
    // fatigue multiplier over the player's skills at contest time. All within-game.
    public static final double MAX_ENERGY = 100.0;
    // Base drain per possession a player is on the floor, before the endurance
    // scale. At endurance 10 this is the raw drain; higher endurance drains less.
    private final double energyDrainPerPossession;
    // How strongly endurance slows the drain (avg-10 deviation): drain is scaled
    // by 1 − ENDURANCE_DRAIN_SENSITIVITY × (endurance − 10)/10, clamped ≥ a floor
    // so an elite-endurance player still tires, just far slower.
    public static final double ENDURANCE_DRAIN_SENSITIVITY = 0.6;
    private final double minDrainScale;
    // Recovery per possession spent benched.
    private final double energyRecoveryPerPossession;
    // Fatigue multiplier over skills: at full energy ×1.0; as energy falls toward
    // 0 the multiplier falls toward (1 − fatigueMaxPenalty). Tuned in §3.5
    // calibration so tired players degrade visibly (late-period FG% sags a touch)
    // while still a thumb on the scale, not a cliff (Decision B).
    private final double fatigueMaxPenalty;

    // Substitution (Decisions C/D): a tired on-floor starter is pulled when their
    // energy drops below a threshold. The base threshold is scaled per-coach by
    // substitutionAggressiveness (higher ⇒ pull earlier ⇒ higher threshold).
    private final double baseSubEnergyThreshold;
    // Starters tolerate more fatigue before being pulled (Decision C star
    // retention): their effective threshold is lowered by this many energy points,
    // so a starter is pulled later than a bench player at the same energy. Tuned in
    // §3.5 calibration to land the top starter near ~36 min (not 38+).
    private final double starterSubThresholdBonus;
    // Base bench depth (players drawn off the rotationOrder queue) at an average
    // (10) rotationDepth coach, scaled by rotationDepthFactor. Full squad is always
    // available for forced (foul-out) subs regardless of this.
    private final int baseRotationDepth;

    // --- Foul trouble (§3.13, decisions.md #031 B) ---
    // The SOFT benching rule: a player carrying fouls is a bench CANDIDATE, and
    // whether he actually sits is a per-possession probability, not a threshold.
    //
    // UNITS — read this before touching the numbers below (the #030 G lesson):
    //   foulTroubleSitProbabilities()[f] is a PROBABILITY (per rotation check) that
    //   an average-value player under an average (10) substitutionAggressiveness
    //   coach is benched at foul count f. It is NOT a multiplier.
    //   FOUL_TROUBLE_VALUE_SENSITIVITY is a MULTIPLIER sensitivity (avg-10
    //   deviation form) — it scales the probability above, it is not one.
    //
    // Shape (#031 B, the user's stated intent and the acceptance criterion): one
    // curve scaled by the coach factor, NOT three thresholds — a high-aggressiveness
    // coach starts thinking about sitting at 3, a medium one at 4, a low one at 5.
    // So index 3 is deliberately small (only an aggressive coach's multiplier lifts
    // it to something that fires often) and index 5 is high (nearly everyone sits).
    // Counts 0–2 are ZERO: no coach benches a player for 2 fouls. Index 6 is
    // foulOutLimit — a foul-out is the HARD rule's business, not this one.
    //
    // These are per-CHECK probabilities and substitution is re-decided ~100 times
    // per team per game, so even a small value fires reliably given exposure; the
    // curve is what decides HOW EARLY, not whether. Tuned against the harness in
    // §3.13 Step 6 (foul-outs + the 4/5/6 distribution + the per-slot minutes cost).
    //
    // THE CURVE IS SATURATED — do not reach for it to move foul-outs further. §3.13
    // measured this directly: raising it to {0.25, 0.75, 0.95} moved foul-outs 0.377
    // → 0.407, and {0.40, 0.90, 0.98} → 0.382, i.e. nothing, despite ~60% more subs.
    // The binding constraint is not how readily the coach sits the player; it is that
    // #031 D sends him BACK (via the ordinary freshness path, by design) into the same
    // over-dispersed defender draw that gave him the fouls. Getting below ~0.38 needs
    // a different lever than this one — see #031's implementation note.
    private final double[] foulTroubleSitProbabilities;

    // How strongly the player's VALUE composite (PlayerGameState.valueComposite())
    // bends the sit probability, in the avg-10 deviation form the rest of this class
    // uses: factor = 1 + FOUL_TROUBLE_VALUE_SENSITIVITY × (value − 10)/10.
    //
    // DIRECTION IS DELIBERATE AND INVERTS THE FATIGUE RULE (#031 B): a POSITIVE
    // sensitivity means a BETTER player is MORE likely to be sat at the same foul
    // count. You ride your star when he's tired (starterSubThresholdBonus lets
    // starters tolerate more fatigue); you PROTECT him when he's in foul trouble.
    // This looks like an inconsistency and is not — do not "fix" it.
    public static final double FOUL_TROUBLE_VALUE_SENSITIVITY = 0.55;

    // The extra protection the roster signal adds on top of the value composite
    // (#031 B: the two signals are COMBINED, not substituted). A starter is a player
    // the coach has already committed to, so he is managed a little more tightly
    // still; bench players get a mild discount that deepens down the rotationOrder
    // queue, capped so a deep reserve is never fully exempt.
    private final double foulTroubleStarterBonus;
    private final double foulTroubleBenchDiscountPerSlot;
    private final double foulTroubleMinRosterFactor;

    // How much fresher (energy points) a bench player must be before the SOFT
    // foul-trouble rule will sit an on-floor player for him. This is the
    // anti-oscillation guard, and it is what makes #031 D's sticky sit actually
    // stick in BOTH directions without a stored flag or a countdown.
    //
    // Why it is needed: a player benched for foul trouble rests to full, returns via
    // the ordinary freshness path, and is then — for a few possessions — barely less
    // fresh than the bench. With NO margin at all the rule re-fires almost at once and
    // the player visibly flickers on and off across consecutive possessions, which is
    // exactly the behavior #031 D set out to prevent (measured during §3.13, not
    // hypothetical).
    //
    // The value is NOT "as large as possible" — it is a genuine optimum, and the
    // §3.13 sweep is worth recording because the direction is counter-intuitive. A
    // LARGER margin makes foul-outs WORSE, because it blocks legitimate sits: at
    // margin 12 it vetoed ~69% of fired rolls and foul-outs sat at 0.53; at 8 → 0.46;
    // at 3 → 0.45; at 1 → 0.38. But 0 is also worse (0.40) than 1 — a bare `>` lets
    // the rule swap for a replacement who is fresher by a rounding error, which
    // churns without removing exposure. 1.0 is the floor of the useful band.
    //
    // Note this is BELOW one possession's drain (energyDrainPerPossession = 2.6),
    // so it does not by itself keep a just-returned player on the floor for a fixed
    // number of possessions. It doesn't need to: the anti-oscillation guarantee comes
    // from the combination of this margin and the fact that a returning player enters
    // at or near a full tank, and it is pinned by RotationStateTest's average-sit-
    // length assertion rather than by this constant's size.
    //
    // A margin, in energy points — NOT a probability and NOT a multiplier.
    private final double foulTroubleFreshnessMargin;

    // --- Technical fouls (§3.14a, decisions.md #032 B2/E/F) ---
    //
    // ⚠ THIS IS A PER-TEAM-PER-GAME RATE, NOT A PER-CHECK PROBABILITY. It sits in a
    // file where nearly every double is a probability, so read it as what it is: the
    // number of technical fouls one team is expected to commit in one game. The
    // per-check probability the engine actually rolls is ~0.00175 and is DERIVED, by
    // technicalFoulProbability() below (which also explains why that is half the
    // ~0.0035 #032 B2 estimated).
    //
    // Stored this way deliberately (#032 B2): 0.00175 has no intuitive meaning, cannot
    // be sanity-checked by eye, is not comparable to anything in calibration.md, and
    // would silently drift if defaultPossessionsPerPeriod or PERIODS ever moved.
    // 0.35 is the number a tuner reasons about.
    //
    // Set from the user's real-world figure of 0.6–0.8 technicals per GAME
    // league-wide, i.e. ~0.3–0.4 per TEAM per game. UNSOURCED, like every row in
    // calibration.md — §3.16's job (1) owns verifying it.
    //
    // A ballpark, NOT a target (#032 J): nothing in the engine is tuned toward it —
    // the constant is set from the real-world figure directly, so the harness line is
    // a correctness check that the roll fires at the rate configured, not a
    // calibration objective. Judge it at 5 SEEDS ONLY (11.8% relative sd at 102
    // games; 5.3% at 5 seeds).
    private final double technicalFoulsPerTeamGame;

    // Two technicals in one game is an automatic ejection (#032 F). A monotonic
    // counter exactly like foulOutLimit, which is why PlayerGameState.isEjected()
    // is a DERIVED predicate with no stored flag — #023 F's discipline applies
    // unchanged. (The stored-state exception belongs to §3.14b's flagrant-2, which
    // is a severity grade with no counter behind it.)
    public static final int TECHNICAL_EJECTION_LIMIT = 2;

    // A technical is exactly ONE free throw (FREE_THROWS_PER_FOUL is 2,
    // AND_ONE_FREE_THROWS is 1). Named separately rather than sharing AND_ONE's
    // constant: the two are 1 for unrelated reasons, and §3.12 already showed what
    // happens when one count is assumed to follow another (#030 C).
    public static final int TECHNICAL_FREE_THROWS = 1;

    // --- Flagrant fouls (§3.14b, decisions.md #034 A/E/G) ---
    //
    // ⚠ THIS IS A PER-TEAM-PER-GAME RATE, NOT A PER-FOUL PROBABILITY — the same
    // shape as technicalFoulsPerTeamGame above, and read the same way: the
    // number of flagrant fouls one team is expected to commit in one game. The
    // per-foul probability the engine actually rolls is ~0.0084 and is DERIVED, by
    // flagrantFoulProbability() below.
    //
    // Stored this way deliberately (#032 B2's argument, #034 G): 0.0084 has no
    // intuitive meaning, cannot be sanity-checked by eye, and is not comparable to
    // anything in calibration.md. 0.16 is the number a tuner reasons about.
    //
    // Back-solved from the real-world figure of ~0.25-0.40 flagrants per GAME
    // league-wide, i.e. ~0.13-0.20 per TEAM per game. UNSOURCED, like every row in
    // calibration.md — §3.16's job (1) owns verifying it.
    //
    // A ballpark, NOT a target (#034 H): nothing in the engine is tuned toward it —
    // the constant is set from the real-world figure directly, so the harness line is
    // a correctness check that the roll fires at the rate configured, not a
    // calibration objective. JUDGE IT AT 5 SEEDS ONLY — at 102 games this is ~33
    // events (relative sd 17.4%) and at 5 seeds ~166 (7.8%), the COARSEST row in
    // calibration.md. A single-seed reading is useless.
    private final double flagrantFoulsPerTeamGame;

    // What fraction of flagrants are FLAGRANT-2 — the grade that ejects immediately
    // (#034 E). A flat CONDITIONAL SHARE, rolled only once a flagrant has already
    // happened, with no causal input: the engine cannot distinguish excessive from
    // ordinary contact, and foulProne has already had its say in who was selected as
    // the committer.
    //
    // ⚠ NOT a probability in the clamped sense — it is a share of an already-rare
    // parent event, so it needs NO clamp (neither clampProbability nor
    // clampRareProbability). It inherits the parent rate's resolvability rather than
    // having its own: a separately-tuned flagrant-2 RATE was rejected because ~5
    // events per 102-game run cannot be resolved at any seed count (#034 E).
    private final double flagrantTwoShare;

    // ONE flagrant-2 is an automatic ejection (#034 E/F). Named rather than inlined as
    // `>= 1` so the third disqualification threshold reads identically to the other two
    // (foulOutLimit = 6, TECHNICAL_EJECTION_LIMIT = 2) — the shape is the point:
    // PlayerGameState.isEjectedForFlagrant() is a DERIVED predicate over a monotonic
    // counter, and a limit of 1 changes nothing about that. It is what makes the
    // stored-state exception predicted by #031 H / #032 F unnecessary a second time.
    public static final int FLAGRANT_EJECTION_LIMIT = 1;

    // A flagrant is exactly TWO free throws, at every site and for both grades (#034
    // C/E). Named separately rather than sharing FREE_THROWS_PER_FOUL (also 2): the
    // two are 2 for unrelated reasons, and §3.12 already showed what happens when one
    // count is assumed to follow another (#030 C) — the same argument that gave
    // TECHNICAL_FREE_THROWS its own name.
    //
    // ⚠ THIS REPLACES THE UNDERLYING FOUL'S AWARD, IT DOES NOT ADD TO IT. A flagrant
    // stopped THREE is 2 FTs (not 3, not 5); a flagrant and-1 is 2 (not 1 + 2). See
    // PossessionEngine.awardFlagrant.
    public static final int FLAGRANT_FREE_THROWS = 2;

    // The divisor for flagrantFoulProbability() — personal fouls per team per game.
    //
    // ⚠ A NAMED CONSTANT, NOT A MAGIC NUMBER, because it is an ASSUMPTION ABOUT THE
    // ENGINE'S CURRENT BEHAVIOR rather than a rule: it must be greppable when §3.16
    // invalidates it. Measured, not configured — §3.14a's harness landing of ~19.4
    // `Fouls / team / game` MINUS its 0.367 technicals (that line tallies ALL FOUL
    // events), i.e. the personal-foul rate alone, which §3.13 measured at 19.0.
    public static final double PERSONAL_FOULS_PER_TEAM_GAME = 19.0;

    // Base probability that a made field goal is assisted, at an average passing
    // supporting cast (the other 4 offensive players ≈ 10). Scaled up/down by how
    // much the supporting cast's passing deviates from average. Tuned in §3.4
    // calibration toward ~26 assists/team/game.
    private final double baseAssist;
    // How strongly the supporting cast's average passing deviation bends the
    // assist rate (avg-10 deviation form).
    public static final double ASSIST_SENSITIVITY = 0.30;

    // acumen → a small shot-make-probability bonus (better shot selection ⇒
    // higher-quality looks). Modest thumb on the scale, not a shot-type reweight.
    public static final double ACUMEN_SENSITIVITY = 0.05;
    // teamOffense/teamDefense → a single possession-level efficiency multiplier on
    // the shot make rate (offense lifts, defense suppresses). Modest.
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
     * #PROB_CEILING}] — the <b>floor-free</b> sibling of {@link #clampProbability},
     * and the single owner of an expression that had been hand-rolled character-for-
     * character at four independent sites.
     *
     * <p><b>Why the {@link #PROB_FLOOR} must not apply to a rare event.</b> The
     * global floor (0.02) exists so a skill mismatch can never make a <i>normal</i>
     * outcome impossible. Applied to a deliberately-rare carve it does the opposite:
     * it becomes a floor the base rate cannot go below, so the constant is tunable
     * only UPWARD and a "turn it down" recalibration silently does nothing. The
     * margin is not subtle — §3.14a's per-check technical probability is ~0.00175, so
     * {@link #PROB_FLOOR} is more than <b>10×</b> the rate itself and flooring would
     * inflate technicals by that factor. (#032 H estimated ~6× off the design's
     * ~0.0035; the true per-check rate is half that, so the margin is wider still —
     * see {@link #technicalFoulProbability}.) It also destroys the true off-switch a
     * 0.0 multiplier gives {@link FoulResolver#isFoul} (#030 A1).
     *
     * <p><b>The four sites this consolidates</b> (#030 set the trigger — two is a
     * coincidence, a third is the signal; §3.13 added the third and §3.14a the
     * fourth): {@link #rareEventProbability} (§3.10), {@link
     * #foulTroubleSitProbability} (§3.13), {@link FoulResolver#isFoul}'s floor-free
     * multiply (§3.12), and {@link #technicalFoulProbability} (§3.14a). The
     * consolidation is <b>behavior-neutral</b> — no number moves, no recalibration —
     * and the proof is that §3.10's and §3.13's existing tests pass unchanged. A
     * fifth floor-free site now costs one call, not a fourth copy.
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
     * <p><b>Note that figure corrects #032 B2's "~0.0035".</b> That estimate assumed
     * ~100 checks per team per game — one per possession the team plays. The actual
     * count is <b>~200</b>: {@link PossessionEngine#simulate} advances BOTH teams'
     * rotations on EVERY possession, so a team is checked on its defensive
     * possessions too. The divisor below is the true call count, which is what makes
     * the harness land on the configured rate; using 100 would have doubled
     * technicals to ~0.7 per team per game. The design's arithmetic slipped, not its
     * intent — the constant it reasons about is unchanged.
     *
     * <p><b>There is no contest here, and that is a positive design claim rather than
     * a simplification (#032 B).</b> Every other foul in the engine hangs off a
     * contest — a defender is drawn, skills are contested, a foul falls out. A
     * technical has no contest to hang off: it is not caused by the shot, the
     * matchup, or the rebound. So this method takes <b>no skills, no coach factor,
     * and no game situation</b> — a blowout, a rivalry and a walkover all produce
     * technicals at the same rate. {@code foulProne} weights only WHICH of the
     * on-floor five wears it (#032 C), never whether one happens.
     *
     * <p><b>The divisor is NOMINAL, not actual, and the gap is deliberate.</b> The
     * real possession count is pace-scaled per game ({@link
     * PossessionEngine#simulate} blends both coaches' {@code paceMultiplier}) and
     * overtime adds more, so a fast-paced game takes more checks and draws
     * <b>slightly more</b> technicals than the constant nominally says. That is
     * correct behavior — a longer game has more opportunity — but it means the
     * constant reads as "technicals per team per game <i>at nominal pace</i>", and
     * <b>a harness landing a few percent off the constant is NOT a bug and NOT
     * drift.</b> Recorded because the alternative reading — treating the miss as a
     * calibration error and back-solving the constant — would chase noise.
     *
     * <p>Clamped through {@link #clampRareProbability}: the {@link #PROB_FLOOR}
     * (0.02) is more than <b>ten times</b> this rate and would inflate it by that
     * factor, making the constant tunable only upward (#032 H).
     *
     * <p>The ×2 in the divisor is the correction above: {@code advancePossession}
     * runs once per team per possession and both teams advance on every possession,
     * so a team gets {@code possessionsPerPeriod × PERIODS × 2} checks in a nominal
     * game.
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
     * <p><b>This is a severity roll layered ON TOP of an existing foul, not a foul
     * rate.</b> It is asked only after {@link FoulResolver#isFoul}, {@link
     * FoulResolver#isAndOne} or {@link FoulResolver#resolveReboundFoul} has already
     * returned a foul, so nothing is re-partitioned and <b>no existing rate moves by
     * construction</b> (#034 A). That is why the divisor is the FOUL count and not a
     * possession count: the roll fires per foul, so any other divisor would misstate
     * the relationship and break the moment the foul rate moved.
     *
     * <p><b>⚠ THE DIVISOR IS EMERGENT, AND THAT IS THIS CONSTANT'S HONEST COST (#034
     * G).</b> {@link #technicalFoulProbability}'s divisor is NOMINAL — derived from
     * config ({@code defaultPossessionsPerPeriod × PERIODS × 2}), so it moves only
     * when a constant moves. This one is different in kind: {@link
     * #PERSONAL_FOULS_PER_TEAM_GAME} is a <b>measured</b> quantity, an assumption about
     * what the engine currently does. <b>So §3.16 — or any pass that moves the foul
     * rate — moves the flagrant rate too, without touching {@link
     * #FLAGRANT_FOULS_PER_TEAM_GAME}.</b> Directionally that is correct (more fouls,
     * more chances for one to be excessive), but it means this constant is <b>not a
     * standalone dial</b>, and the coupling must be stated rather than discovered.
     *
     * <p><b>Clamped through {@link #clampRareProbability}</b> — #032 H's <b>fifth</b>
     * site, one call rather than a fifth hand-rolled copy. The floor argument holds but
     * is <b>thinner than §3.14a's</b> and so is argued rather than assumed: {@link
     * #PROB_FLOOR} (0.02) is ~<b>2.4×</b> this rate, against the >10× margin the
     * technical rate enjoys. Still decisive — flooring would inflate flagrants by 2.4×
     * and make the constant tunable only upward (the #028 trap) — but a future rate
     * increase could bring it near territory where the floor-free choice stops being
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
     * here. {@link #reboundFoulBase()} sits at ~0.03, close enough to 0.02 for the
     * floor to bite. This is the same class of problem {@link #BLOCK_SENSITIVITY}
     * solved for §3.7 — a global constant tuned for common events being wrong for a
     * rare one.
     *
     * <p>§3.14a (#032 H) routed the hand-rolled clamp here through the shared
     * helper. Behavior-neutral: the expression is identical.
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

    // Constructor binding is what lets every field be final with no initializer.
    // Accessor names map to keys directly: baseThree() -> sim.base-three.

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
     * factory and a running engine can never disagree. Hard-coding the 57 values here
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
                            + " — it is the only copy of the 57 profilable constants", e);
        }
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new PropertiesPropertySource("baseline", props));
        return new Binder(ConfigurationPropertySources.get(env))
                .bind("sim", SimConfig.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not bind sim.* from " + BASELINE_PROFILE_RESOURCE));
    }

    /** The baseline profile file — the single copy of the 57 profilable values. */
    public static final String BASELINE_PROFILE_RESOURCE = "application-baseline.properties";
}
