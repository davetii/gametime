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
    public static final double BASE_DRIVE = 0.59;
    public static final double BASE_PERIMETER = 0.43;
    public static final double BASE_THREE = 0.31;
    public static final double BASE_POST = 0.49;

    // --- Turnover base rate (per possession; calibrated §3.4 toward ~14 TO/team) ---
    public static final double BASE_TURNOVER = 0.038;

    // --- Foul base rate (on drive/post attempts) ---
    public static final double BASE_FOUL = 0.15;

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

    public double clampProbability(double p) {
        return Math.max(PROB_FLOOR, Math.min(PROB_CEILING, p));
    }

    public double contestProbability(double base, double offenseSkill, double defenseSkill) {
        double p = base + SENSITIVITY * (offenseSkill - defenseSkill) / SCALE_AVG;
        return clampProbability(p);
    }

    public double freeThrowProbability(double freeThrowSkill) {
        double p = FT_BASE + (freeThrowSkill - SCALE_AVG) / SCALE_AVG * FT_SENSITIVITY;
        return clampProbability(p);
    }
}
