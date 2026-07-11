package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.Coach;

/**
 * The Decision-A (decisions.md #022) avg-10 deviation multipliers a coach applies
 * to a team's possession flow, computed once per team and threaded through the
 * engine (via {@link TeamContext}). §3.4 reads {@code pace} /
 * {@code offensiveScheme} / {@code defensiveScheme}; §3.5 (decisions.md #023,
 * Decision D) adds {@code rotationDepth} / {@code substitutionAggressiveness},
 * consumed by the substitution check in {@link RotationState}.
 *
 * <p>Each multiplier is {@code 1 + COACH_SENSITIVITY × (attr − 10) / 10}, so an
 * attribute of 10 (or a missing coach) yields ×1.0 — no effect. This is a value
 * object, not a Spring component: it carries per-team data, not a singleton.
 */
public final class CoachModifiers {

    private final double paceMultiplier;
    private final double shotMixLean;
    private final double defensivePressure;
    private final double rotationDepthFactor;
    private final double subAggressivenessFactor;

    private CoachModifiers(double paceMultiplier, double shotMixLean, double defensivePressure,
                           double rotationDepthFactor, double subAggressivenessFactor) {
        this.paceMultiplier = paceMultiplier;
        this.shotMixLean = shotMixLean;
        this.defensivePressure = defensivePressure;
        this.rotationDepthFactor = rotationDepthFactor;
        this.subAggressivenessFactor = subAggressivenessFactor;
    }

    /**
     * Build the modifiers from a (possibly null) coach. A null coach, or null
     * attributes, fall back to league average via {@link SimConfig#coachModifier}
     * ⇒ every multiplier is ×1.0 (a coach-less team plays the baseline).
     */
    public static CoachModifiers from(Coach coach, SimConfig config) {
        if (coach == null) {
            return neutral();
        }
        return new CoachModifiers(
                config.coachModifier(coach.getPace()),
                config.coachModifier(coach.getOffensiveScheme()),
                config.coachModifier(coach.getDefensiveScheme()),
                config.rotationModifier(coach.getRotationDepth()),
                config.rotationModifier(coach.getSubstitutionAggressiveness()));
    }

    /** Neutral modifiers (all ×1.0) — for tests and coach-less simulation. */
    public static CoachModifiers neutral() {
        return new CoachModifiers(1.0, 1.0, 1.0, 1.0, 1.0);
    }

    /**
     * Scales the team's possession count (Decision A: pace drives how many
     * possessions a game runs, not merely shot urgency).
     */
    public double paceMultiplier() {
        return paceMultiplier;
    }

    /**
     * Leans the shot-type draw toward perimeter/three (&gt;1.0) or inside/post
     * (&lt;1.0); applied in {@link ShotSelector}.
     */
    public double shotMixLean() {
        return shotMixLean;
    }

    /**
     * Scales this team's defensive pressure (turnover/foul forcing) when it is the
     * defense; &gt;1.0 is a more aggressive, gambling defense.
     */
    public double defensivePressure() {
        return defensivePressure;
    }

    /**
     * §3.5 (Decision D): the avg-10 multiplier over how far down the {@code
     * rotationOrder} bench queue fatigue subs draw. &gt;1.0 = a deeper rotation
     * (more bench players see minutes); &lt;1.0 = a tighter one. Applied to
     * {@link SimConfig#BASE_ROTATION_DEPTH} via {@link SimConfig#rotationDepth}.
     */
    public double rotationDepthFactor() {
        return rotationDepthFactor;
    }

    /**
     * §3.5 (Decision D): the avg-10 multiplier over the fatigue-sub energy
     * threshold. &gt;1.0 = pull tired starters earlier (higher threshold); &lt;1.0
     * = ride them longer. Applied to {@link SimConfig#BASE_SUB_ENERGY_THRESHOLD}
     * via {@link SimConfig#subEnergyThreshold}.
     */
    public double subAggressivenessFactor() {
        return subAggressivenessFactor;
    }
}
