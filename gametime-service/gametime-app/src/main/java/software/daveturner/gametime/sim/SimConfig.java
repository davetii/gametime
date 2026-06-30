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
    // (pace / offensiveScheme / defensiveScheme). attr 10 ⇒ ×1.0. Split
    // per-effect only if calibration shows one knob can't fit all effects.
    public static final double COACH_SENSITIVITY = 0.20;

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
