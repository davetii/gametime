package software.daveturner.gametime.sim;

/**
 * §3.7 (decisions.md #025 D): where a swatted ball ends up after a block. A flat
 * four-way loose-ball recovery — the OFFENSE-recovered outcomes ({@link
 * #RECOVERED_OFFENSE}, {@link #OOB_OFFENSE}) re-enter the second-chance loop at
 * ShotSelector (Decision E); the DEFENSE-recovered outcomes end the possession.
 * OOB is kept as two distinct outcomes so it stays a visible play-by-play event
 * while still forking possession correctly.
 */
public enum BlockRecovery {
    /** In-bounds, defense secures it — possession over. */
    RECOVERED_DEFENSE(false),
    /** In-bounds, offense secures it — second-chance possession. */
    RECOVERED_OFFENSE(true),
    /** Out of bounds off the shooter/offense — defense's ball, possession over. */
    OOB_DEFENSE(false),
    /** Out of bounds off the blocker/defense — offense retains, second-chance. */
    OOB_OFFENSE(true);

    private final boolean offenseRetains;

    BlockRecovery(boolean offenseRetains) {
        this.offenseRetains = offenseRetains;
    }

    /**
     * True when the offense keeps the ball (a second-chance possession follows);
     * false when the possession is over. Lets {@code PossessionEngine} fork on the
     * recovery without switching on all four cases.
     */
    public boolean offenseRetains() {
        return offenseRetains;
    }
}
