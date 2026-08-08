package software.daveturner.gametime.sim;

/**
 * §3.9 (decisions.md #027 B): the cause of a declared turnover. Once the
 * (unchanged) turnover gate fires, a single weighted draw ({@link
 * TurnoverResolver#pickCause}) picks exactly one of these nine causes — a pure
 * re-partition of an event that already fires, so the turnover <i>count</i> never
 * moves (Decision A). Each cause carries the {@code outcome} string emitted on the
 * {@code TURNOVER} {@link PlayType} event; the field is open-ended free text
 * (#020), so there is no schema or OpenAPI cost to the nine labels.
 *
 * <p><b>Attribution (Decision B).</b> Every cause credits the current ball-handler
 * ({@code shooter.recordTurnover()}, {@code primaryPlayerId = shooter}) exactly as
 * the pre-§3.9 binary did — the per-player turnover count and reconciliation are
 * byte-unchanged; only the label differs. {@link #STOLEN} is the sole two-sided
 * cause: the ball-loser is on the event and a stealer is credited separately (via
 * {@code pickStealer} + {@code recordSteal()}), unchanged. The three team-level
 * violations ({@link #SHOT_CLOCK_VIOLATION}, {@link #EIGHT_SECONDS_BACKCOURT_VIOLATION},
 * {@link #OVER_AND_BACK}) are team failures in reality but are charged to the
 * on-ball player anyway — reusing the ball-handler charge avoids fabricating a
 * separate "who caused the team violation" picker (the #014/#017/#020 discipline).
 *
 * <p><b>Naming (Decision D).</b> {@link #LOST_BALL_OUT_OF_BOUNDS} is a live-ball
 * handling turnover on the {@code TURNOVER} {@code PlayType} — deliberately
 * distinct from §3.8's {@code OUT_OF_BOUNDS_*} outcomes on the {@code REBOUND}
 * {@code PlayType} (a missed shot leaving the court, #026). They must never read as
 * the same thing. The two digit-leading causes carry the digit form in the
 * {@code outcome} string ({@code "3_SECONDS_VIOLATION"} /
 * {@code "8_SECONDS_BACKCOURT_VIOLATION"}) while the enum constant uses the letter
 * form (a constant cannot start with a digit).
 *
 * <p>{@code LOST_BALL} (the pre-§3.9 unforced catch-all) is <b>retired</b> — its
 * old ~40% share is split across the eight non-{@code STOLEN} causes, so no generic
 * unforced bucket survives (Decision B).
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
