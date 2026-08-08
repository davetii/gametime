package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

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
 * full flow), so the per-check {@code BASE_TURNOVER} hits diminishing returns
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
 * fresh ones in, so the on-floor FG% holds even as {@code FATIGUE_MAX_PENALTY}
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
 *   <li>{@link #BASE_NO_BASKET_FOUL} (then named {@code BASE_FOUL}) is a
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
 *       once. {@link #BASE_NO_BASKET_FOUL} was again NOT touched (see §3.10
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
@Component
public class SimConfig {

    // --- Possession count (Decision B) ---
    public static final int DEFAULT_POSSESSIONS_PER_PERIOD = 25;
    public static final int PERIODS = 4;
    public static final int OT_POSSESSIONS_PER_PERIOD = 5;

    // --- Probability sensitivity (Decision C) ---
    public static final double SENSITIVITY = 0.5;
    public static final double PROB_FLOOR = 0.02;
    public static final double PROB_CEILING = 0.97;
    public static final double SCALE_AVG = 10.0;

    // --- Shot base rates (calibrated §3.4 against ~47% FG / ~36% 3P) ---
    public static final double BASE_DRIVE = 0.5975;
    public static final double BASE_PERIMETER = 0.4375;
    public static final double BASE_THREE = 0.3375;
    public static final double BASE_POST = 0.4975;

    // --- Turnover base rate (per possession; calibrated §3.4 toward ~14 TO/team) ---
    public static final double BASE_TURNOVER = 0.038;

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
    public static final double BASE_NO_BASKET_FOUL = 0.15;

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
    public static final double FOUL_MULT_DRIVE = 1.0;
    public static final double FOUL_MULT_POST = 1.0;
    public static final double FOUL_MULT_PERIMETER = 0.30;
    public static final double FOUL_MULT_THREE = 0.133;

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
            case DRIVE -> FOUL_MULT_DRIVE;
            case POST -> FOUL_MULT_POST;
            case PERIMETER -> FOUL_MULT_PERIMETER;
            case THREE -> FOUL_MULT_THREE;
        };
    }

    // --- Free throw ---
    public static final double FT_BASE = 0.75;
    public static final double FT_SENSITIVITY = 0.20;
    public static final int FREE_THROWS_PER_FOUL = 2;

    // --- Rebounding (§3.3) ---
    // Base offensive-rebound rate at an average-vs-average contest (NBA ~25–28%).
    // Tuned empirically in §3.4. Rebound contests reuse the global SENSITIVITY.
    public static final double BASE_OFFENSIVE_REBOUND = 0.27;
    // Cap on offensive rebounds per possession to bound the second-chance loop;
    // after the cap, a missed shot is forced to a defensive rebound.
    public static final int MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION = 3;

    // --- Missed-shot out of bounds (§3.8, decisions.md #026) ---
    // A missed shot resolves to one of FOUR outcomes in a single draw (Decision A):
    // offensive rebound / defensive rebound / OOB-offense / OOB-defense. The OOB
    // share is carved off FIRST by these flat, skill-INDEPENDENT weights (a fixed
    // defensive lean, NOT a second skilled contest — Decision A); only the clean-
    // rebound remainder runs ReboundResolver's skill contest. OOB_TOTAL_WEIGHT is
    // the fraction of missed shots that leave the court (sail-out untouched or
    // tipped out in a scramble); the two slices below split THAT share, leaning
    // defensive (a loose ball in a scrum favors the defense). MissedShotResolver
    // normalizes the two slices by their sum, so they need not add to anything in
    // particular — only their ratio and OOB_TOTAL_WEIGHT matter. Placeholders,
    // settled by the CalibrationHarness OOB line (Decision D): OOB removes some
    // second-chance possessions, so its rate must be visible and the §3.4/§3.5
    // aggregates re-confirmed. Kept smaller than the two rebound outcomes.
    public static final double OOB_TOTAL_WEIGHT = 0.07;
    public static final double OOB_DEFENSE_WEIGHT = 0.60;
    public static final double OOB_OFFENSE_WEIGHT = 0.40;

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
    public static final double BASE_BLOCK_DRIVE = 0.056;
    public static final double BASE_BLOCK_POST = 0.048;
    public static final double BASE_BLOCK_PERIMETER = 0.023;
    public static final double BASE_BLOCK_THREE = 0.005;
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
    public static final double BLOCK_RECOVERED_DEFENSE = 0.45;
    public static final double BLOCK_RECOVERED_OFFENSE = 0.30;
    public static final double BLOCK_OOB_DEFENSE = 0.13;
    public static final double BLOCK_OOB_OFFENSE = 0.12;

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
    public static final double TO_WEIGHT_STOLEN = 56.0;
    public static final double TO_WEIGHT_SHOT_CLOCK = 10.0;
    public static final double TO_WEIGHT_OFFENSIVE_FOUL = 9.0;
    public static final double TO_WEIGHT_BAD_PASS = 8.0;
    public static final double TO_WEIGHT_TRAVELLING = 6.0;
    public static final double TO_WEIGHT_LOST_BALL_OOB = 4.0;
    public static final double TO_WEIGHT_THREE_SECONDS = 3.0;
    public static final double TO_WEIGHT_EIGHT_SECONDS_BACKCOURT = 2.0;
    public static final double TO_WEIGHT_OVER_AND_BACK = 2.0;

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
    // REBOUND_FOUL_BASE is the per-missed-shot probability at an average-vs-average
    // contest, scaled by the same discipline/pressure inputs the shooting foul uses
    // (foulProne / defensivePressure) in the avg-10 form (#021 C / #022). It must
    // stay SMALL: every hit either hands the offense a second chance or (in the
    // bonus) two free throws, so this is the knob that drives §3.10's scoring lift.
    public static final double REBOUND_FOUL_BASE = 0.055;
    // Two-sided split (Decision A2), DEFENSE-LEANING: box-out contact dominates,
    // over-the-back is the genuine minority. Raw weights — the resolver normalizes
    // by their sum, so only the ratio matters.
    public static final double REBOUND_FOUL_DEFENSE_WEIGHT = 0.75;
    public static final double REBOUND_FOUL_OFFENSE_WEIGHT = 0.25;
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
    public static final int BONUS_FOULS_PER_PERIOD = 5;

    // --- And-1 / shooting foul on a made basket (§3.11, decisions.md #029) ---
    // An and-1 is a SECOND, post-make foul roll (#029 A1) carved beside the assist:
    // the pre-shot foul branch and BASE_NO_BASKET_FOUL are untouched, so "P(a foul
    // stops the shot)" keeps its §3.4 meaning and this rate stays independently
    // tunable — the §3.7 block / §3.10 rebound-foul carve, a third time.
    //
    // AND_ONE_BASE is P(the make also drew a foul) at an average-vs-average contest,
    // rolled ONLY on a made DRIVE/POST (#029 A2 — widening to all shot types is
    // §3.12). It must stay THIN: every hit is a pure-additive point (a made FG plus
    // one FT with no offsetting removal), so this is the knob that drives §3.11's
    // scoring lift. Placeholder, settled by the CalibrationHarness and-1 line (E).
    public static final double AND_ONE_BASE = 0.055;
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
    public static final int FOUL_OUT_LIMIT = 6;

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
    public static final double ENERGY_DRAIN_PER_POSSESSION = 2.6;
    // How strongly endurance slows the drain (avg-10 deviation): drain is scaled
    // by 1 − ENDURANCE_DRAIN_SENSITIVITY × (endurance − 10)/10, clamped ≥ a floor
    // so an elite-endurance player still tires, just far slower.
    public static final double ENDURANCE_DRAIN_SENSITIVITY = 0.6;
    public static final double MIN_DRAIN_SCALE = 0.25;
    // Recovery per possession spent benched.
    public static final double ENERGY_RECOVERY_PER_POSSESSION = 5.5;
    // Fatigue multiplier over skills: at full energy ×1.0; as energy falls toward
    // 0 the multiplier falls toward (1 − FATIGUE_MAX_PENALTY). Tuned in §3.5
    // calibration so tired players degrade visibly (late-period FG% sags a touch)
    // while still a thumb on the scale, not a cliff (Decision B).
    public static final double FATIGUE_MAX_PENALTY = 0.28;

    // Substitution (Decisions C/D): a tired on-floor starter is pulled when their
    // energy drops below a threshold. The base threshold is scaled per-coach by
    // substitutionAggressiveness (higher ⇒ pull earlier ⇒ higher threshold).
    public static final double BASE_SUB_ENERGY_THRESHOLD = 62.0;
    // Starters tolerate more fatigue before being pulled (Decision C star
    // retention): their effective threshold is lowered by this many energy points,
    // so a starter is pulled later than a bench player at the same energy. Tuned in
    // §3.5 calibration to land the top starter near ~36 min (not 38+).
    public static final double STARTER_SUB_THRESHOLD_BONUS = 8.0;
    // Base bench depth (players drawn off the rotationOrder queue) at an average
    // (10) rotationDepth coach, scaled by rotationDepthFactor. Full squad is always
    // available for forced (foul-out) subs regardless of this.
    public static final int BASE_ROTATION_DEPTH = 4;

    // Base probability that a made field goal is assisted, at an average passing
    // supporting cast (the other 4 offensive players ≈ 10). Scaled up/down by how
    // much the supporting cast's passing deviates from average. Tuned in §3.4
    // calibration toward ~26 assists/team/game.
    public static final double BASE_ASSIST = 0.62;
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
     * passing (10) yields {@code BASE_ASSIST}; better-passing casts assist more.
     */
    public double assistProbability(double supportingCastPassing) {
        double p = BASE_ASSIST
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
     * the avg-10 deviation form, floored at {@link #MIN_DRAIN_SCALE} so even an
     * elite-endurance player still tires (just far more slowly).
     */
    public double energyDrain(double endurance) {
        double scale = 1.0 - ENDURANCE_DRAIN_SENSITIVITY * (endurance - SCALE_AVG) / SCALE_AVG;
        scale = Math.max(MIN_DRAIN_SCALE, scale);
        return ENERGY_DRAIN_PER_POSSESSION * scale;
    }

    /**
     * §3.5 (Decision B): the fatigue multiplier over a player's skills at contest
     * time. Full energy ⇒ ×1.0; as energy falls to 0 the multiplier falls linearly
     * to {@code 1 − FATIGUE_MAX_PENALTY}. A modest thumb on the scale, not a cliff.
     */
    public double fatigueFactor(double energy) {
        double frac = Math.max(0.0, Math.min(1.0, energy / MAX_ENERGY));
        return 1.0 - FATIGUE_MAX_PENALTY * (1.0 - frac);
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
        int depth = (int) Math.round(BASE_ROTATION_DEPTH * rotationDepthFactor);
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
        double threshold = BASE_SUB_ENERGY_THRESHOLD * subAggressivenessFactor;
        if (starter) {
            threshold -= STARTER_SUB_THRESHOLD_BONUS;
        }
        return threshold;
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
     * §3.10 (decisions.md #028 C): the probability of a deliberately-RARE carved-off
     * event, contested in the usual avg-10 form but clamped WITHOUT the {@link
     * #PROB_FLOOR}. The global floor (0.02) exists so a skill mismatch can never make
     * a normal outcome impossible; applied to a rare carve it does the opposite —
     * it becomes a FLOOR the base rate cannot go below, so the constant is only
     * tunable upward and a "turn it down" recalibration silently does nothing.
     * {@link #REBOUND_FOUL_BASE} sits at ~0.03, close enough to 0.02 for that to
     * bite. This is the same class of problem {@link #BLOCK_SENSITIVITY} solved for
     * §3.7 — a global constant tuned for common events being wrong for a rare one.
     * Floored at 0 (never negative) and ceilinged normally.
     */
    public double rareEventProbability(double base, double drivingSkill,
                                       double opposingSkill, double sensitivity) {
        double p = base + sensitivity * (drivingSkill - opposingSkill) / SCALE_AVG;
        return Math.max(0.0, Math.min(PROB_CEILING, p));
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
        double p = FT_BASE + (freeThrowSkill - SCALE_AVG) / SCALE_AVG * FT_SENSITIVITY;
        return clampProbability(p);
    }
}
