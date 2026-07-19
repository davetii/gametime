package software.daveturner.gametime.sim;

/**
 * §3.8 (decisions.md #026 A): the four-way outcome of a missed shot. In a single
 * weighted draw a miss resolves to exactly one of these — the OFFENSE-retained
 * outcomes ({@link #OFFENSIVE_REBOUND}, {@link #OOB_OFFENSE}) re-enter the
 * second-chance loop at ShotSelector; the DEFENSE outcomes end the possession.
 *
 * <p>Mirrors {@link BlockRecovery} in shape, but is produced by a resolver that
 * WRAPS a skill contest ({@link ReboundResolver}) rather than a flat roll — the
 * rebound-vs-rebound balance is skill-weighted, while the two OOB slices are a
 * flat, defense-leaning lean carved off the top (Decision A, C). OOB outcomes
 * credit NO rebounder (Decision E), so they must be excluded from the rebound
 * reconciliation invariant.
 */
public enum MissedShotOutcome {
    /** Offense secured the board — second-chance possession. Credits a rebounder. */
    OFFENSIVE_REBOUND(true),
    /** Defense secured the board — possession over. Credits a rebounder. */
    DEFENSIVE_REBOUND(false),
    /** Ball left the court, offense retains (last touch defense) — second chance. No rebounder. */
    OOB_OFFENSE(true),
    /** Ball left the court, defense's ball (last touch offense) — possession over. No rebounder. */
    OOB_DEFENSE(false);

    private final boolean offenseRetains;

    MissedShotOutcome(boolean offenseRetains) {
        this.offenseRetains = offenseRetains;
    }

    /**
     * True when the offense keeps the ball (a second-chance possession follows);
     * false when the possession is over. Lets {@code PossessionEngine} fork on the
     * outcome without switching on all four cases.
     */
    public boolean offenseRetains() {
        return offenseRetains;
    }

    /**
     * True for the two rebound outcomes (a rebounder was secured and must be
     * credited); false for the two OOB outcomes (nobody secured the ball, so no
     * {@code recordOffensiveRebound()}/{@code recordDefensiveRebound()} — Decision E).
     */
    public boolean creditsRebounder() {
        return this == OFFENSIVE_REBOUND || this == DEFENSIVE_REBOUND;
    }
}
