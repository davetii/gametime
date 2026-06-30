package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.Coach;

/**
 * The Decision-A (decisions.md #022) avg-10 deviation multipliers a coach applies
 * to a team's possession flow, computed once per team and threaded through the
 * engine (via {@link TeamContext}). §3.4 reads {@code pace} /
 * {@code offensiveScheme} / {@code defensiveScheme} ONLY — {@code rotationDepth}
 * and {@code substitutionAggressiveness} are §3.5 (Decision E) and are never read
 * here.
 *
 * <p>Each multiplier is {@code 1 + COACH_SENSITIVITY × (attr − 10) / 10}, so an
 * attribute of 10 (or a missing coach) yields ×1.0 — no effect. This is a value
 * object, not a Spring component: it carries per-team data, not a singleton.
 */
public final class CoachModifiers {

    private final double paceMultiplier;
    private final double shotMixLean;
    private final double defensivePressure;

    private CoachModifiers(double paceMultiplier, double shotMixLean, double defensivePressure) {
        this.paceMultiplier = paceMultiplier;
        this.shotMixLean = shotMixLean;
        this.defensivePressure = defensivePressure;
    }

    /**
     * Build the modifiers from a (possibly null) coach. A null coach, or null
     * attributes, fall back to league average via {@link SimConfig#coachModifier}
     * ⇒ every multiplier is ×1.0 (a coach-less team plays the baseline).
     */
    public static CoachModifiers from(Coach coach, SimConfig config) {
        if (coach == null) {
            return new CoachModifiers(1.0, 1.0, 1.0);
        }
        return new CoachModifiers(
                config.coachModifier(coach.getPace()),
                config.coachModifier(coach.getOffensiveScheme()),
                config.coachModifier(coach.getDefensiveScheme()));
    }

    /** Neutral modifiers (all ×1.0) — for tests and coach-less simulation. */
    public static CoachModifiers neutral() {
        return new CoachModifiers(1.0, 1.0, 1.0);
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
}
