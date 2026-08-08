package software.daveturner.gametime.sim;

/**
 * §3.11 (decisions.md #029 D): what sent a shooter to the line. Every {@code
 * FREE_THROW} event carries its source, so a free throw is <b>self-describing</b>.
 *
 * <p>This retires the ambiguity §3.10 knowingly accepted (#028 D: "a bonus FT is
 * indistinguishable from a shooting-foul FT except by its preceding {@code FOUL}").
 * That backward-join was tolerable at two sources; §3.11 adds a third (the and-1),
 * and a Phase-4 stats consumer wanting FT% by source should not have to replay the
 * event sequence to get it (#020 — derive nothing at read time).
 *
 * <p><b>No schema change</b>: the source is folded into the existing free-text
 * {@code outcome} string (#020) as a {@code MADE_*} / {@code MISSED_*} suffix, so
 * made-vs-missed stays readable by the {@code startsWith("MADE")} tests the box
 * score and the harness already use. The strings cannot collide with the {@code
 * FOUL} outcomes ({@code SHOOTING_FOUL} / {@code REBOUNDING_FOUL_*} / {@code
 * AND_ONE}) — those live on a different {@code PlayType}.
 *
 * <p>The count of free throws is a separate, per-situation parameter (an and-1 is
 * always exactly 1, #029 B; a shooting foul and a bonus trip are {@link
 * SimConfig#FREE_THROWS_PER_FOUL}) — source and count are independent, which is
 * what lets §3.12 pass 3 for a fouled three without touching this enum.
 */
public enum FreeThrowSource {

    /** A shooting foul that STOPPED the shot — the pre-shot foul branch (§3.2). */
    SHOOTING("SHOOTING"),
    /** A bonus (penalty) trip from a rebounding foul once in the penalty (§3.10). */
    BONUS("BONUS"),
    /** The bonus free throw riding a made basket (§3.11 — always exactly one). */
    AND_ONE("AND_ONE");

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
