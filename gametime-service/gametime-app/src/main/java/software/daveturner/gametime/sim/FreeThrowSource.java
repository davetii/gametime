package software.daveturner.gametime.sim;

/**
 * §3.11 (decisions.md #029 D): what sent a shooter to the line. Every {@code
 * FREE_THROW} event carries its source, so a free throw is <b>self-describing</b>.
 *
 * <p><b>No schema change</b>: the source is folded into the free-text {@code outcome}
 * string (#020) as a {@code MADE_*} / {@code MISSED_*} suffix, so made-vs-missed stays
 * readable by the {@code startsWith("MADE")} tests the box score and the harness already
 * use. It cannot collide with the {@code FOUL} outcomes — those live on a different
 * {@code PlayType}.
 *
 * <p><b>⚠ Source and count are INDEPENDENT.</b> The count is a separate per-situation
 * parameter (an and-1 is always 1, #029 B; a shooting foul and a bonus trip are {@link
 * SimConfig#FREE_THROWS_PER_FOUL}), which is what lets §3.12 pass 3 for a fouled three
 * without touching this enum.
 *
 * <p><b>⚠ The harness reads FT source straight off this suffix</b> (#029 D), so a new
 * award site needs its OWN value: reusing one silently inflates a real source's share on
 * the instrument that phase is judged by, and an untagged outcome buckets as {@code
 * UNKNOWN}. This is why {@code TECHNICAL} and {@code FLAGRANT} exist.
 */
public enum FreeThrowSource {

    /** A shooting foul that STOPPED the shot — the pre-shot foul branch (§3.2). */
    SHOOTING("SHOOTING"),
    /** A bonus (penalty) trip from a rebounding foul once in the penalty (§3.10). */
    BONUS("BONUS"),
    /** The bonus free throw riding a made basket (§3.11 — always exactly one). */
    AND_ONE("AND_ONE"),
    /**
     * §3.14a (decisions.md #032 G): the single free throw for a TECHNICAL foul — ⚠ the
     * one source where <b>nobody was fouled</b>, so the offended team chooses its best
     * shooter rather than the fouled player shooting.
     */
    TECHNICAL("TECHNICAL"),
    /**
     * §3.14b (decisions.md #034): the <b>two</b> free throws for a FLAGRANT foul — flat,
     * at all three foul sites, <b>replacing</b> whatever the underlying foul would have
     * awarded rather than adding to it (#034 C).
     *
     * <p><b>⚠ The grade rides the FOUL event's outcome suffix</b> ({@code FLAGRANT_FOUL_1}
     * / {@code _2}), not this enum: both grades award exactly two free throws (#034 E), so
     * splitting the source by grade would carry a distinction the free throws do not have.
     */
    FLAGRANT("FLAGRANT");

    private final String suffix;

    FreeThrowSource(String suffix) {
        this.suffix = suffix;
    }

    /**
     * The {@code outcome} string for one attempt from this source — e.g. {@code
     * MADE_AND_ONE} / {@code MISSED_BONUS}. The {@code MADE}/{@code MISSED} prefix
     * leads so the existing prefix reads keep working (#029 D's constraint).
     */
    public String outcome(boolean made) {
        return (made ? "MADE_" : "MISSED_") + suffix;
    }
}
