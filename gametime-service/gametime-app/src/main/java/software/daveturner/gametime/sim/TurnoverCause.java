package software.daveturner.gametime.sim;

/**
 * §3.9 (decisions.md #027 B): the cause of a declared turnover. One weighted draw
 * ({@link TurnoverResolver#pickCause}) picks a cause after the unchanged gate fires — a
 * pure re-partition, so the turnover <b>count never moves</b> (Decision A). Each carries
 * the free-text {@code outcome} string for the {@code TURNOVER} event (#020).
 *
 * <p><b>Attribution (Decision B):</b> every cause charges the current ball-handler.
 * {@link #STOLEN} is the sole two-sided one — a stealer is credited separately via
 * {@code pickStealer}. ⚠ The three team-level violations ({@link #SHOT_CLOCK_VIOLATION},
 * {@link #EIGHT_SECONDS_BACKCOURT_VIOLATION}, {@link #OVER_AND_BACK}) are team failures
 * in reality but are charged to the on-ball player anyway, rather than fabricating a
 * separate "who caused the team violation" picker.
 *
 * <p>⚠ <b>{@link #LOST_BALL_OUT_OF_BOUNDS} is NOT §3.8's {@code OUT_OF_BOUNDS_*}</b> —
 * this is a live-ball handling turnover on {@code TURNOVER}; those are a missed shot
 * leaving the court, on {@code REBOUND} (#026). They must never read as the same thing.
 * ⚠ The two digit-leading causes carry the digit form in the {@code outcome} string
 * while the constant uses the letter form (a constant cannot start with a digit).
 */
public enum TurnoverCause {
    /** Live-ball steal — the plurality of turnovers; also credits a stealer. */
    STOLEN("STOLEN"),
    /** Offense failed to get a shot off in time (a stalled/pressured possession). */
    SHOT_CLOCK_VIOLATION("SHOT_CLOCK_VIOLATION"),
    /** Charge / illegal screen — an offensive foul charged as a turnover. */
    OFFENSIVE_FOUL("OFFENSIVE_FOUL"),
    /** A pass thrown away / intercepted-ish handling error (unforced). */
    BAD_PASS("BAD_PASS"),
    /** A travelling violation. */
    TRAVELLING("TRAVELLING"),
    /** Lost the handle / stripped, ball out of bounds off the offense (live ball). */
    LOST_BALL_OUT_OF_BOUNDS("LOST_BALL_OUT_OF_BOUNDS"),
    /** Offensive three-seconds-in-the-lane violation. */
    THREE_SECONDS_VIOLATION("3_SECONDS_VIOLATION"),
    /** Failed to advance the ball past half-court in time. */
    EIGHT_SECONDS_BACKCOURT_VIOLATION("8_SECONDS_BACKCOURT_VIOLATION"),
    /** Over-and-back — ball returned to the backcourt after crossing half. */
    OVER_AND_BACK("OVER_AND_BACK");

    private final String outcome;

    TurnoverCause(String outcome) {
        this.outcome = outcome;
    }

    /**
     * The {@code outcome} string emitted on the {@code TURNOVER} event for this
     * cause. For the two digit-leading causes this is the digit form (the enum
     * constant is the letter form).
     */
    public String outcome() {
        return outcome;
    }
}
