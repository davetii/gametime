package software.daveturner.gametime.sim;

public enum ShotType {
    DRIVE(2),
    PERIMETER(2),
    POST(2),
    THREE(3);

    private final int points;

    ShotType(int points) {
        this.points = points;
    }

    public int getPoints() { return points; }

    /**
     * §3.12 (decisions.md #030 C): how many free throws a foul that <b>stopped</b>
     * this shot awards — <b>3</b> for a {@link #THREE}, 2 for everything else.
     *
     * <p>This fixes a latent bug §3.12 activates. The pre-shot foul branch used to
     * pass a flat {@code FREE_THROWS_PER_FOUL = 2}, so a fouled three would have
     * awarded 2; it never fired only because {@code THREE} was not a "contact type"
     * and so could not be fouled at all. Widening contact to every shot type
     * (#030 A1) makes the path reachable, so the count must graduate with it.
     *
     * <p>This is a <b>rule of basketball, not a tunable</b> — which is why it lives
     * on the type and deliberately NOT in {@link SimConfig}. {@code SimConfig} is
     * the tuning surface, and housing a rule there invites someone to tune it.
     *
     * <p><b>Applies to the STOPPED-shot foul only.</b> An and-1 — a foul on a shot
     * that still went in — is always exactly <b>one</b> free throw for every shot
     * type, a made three included ({@code AND_ONE_FREE_THROWS}, unchanged). A made
     * 3 plus a foul is 3 points and 1 FT, not 3 FTs; do not make that count
     * graduate in parallel with this one.
     */
    public int freeThrowsIfFouled() {
        return this == THREE ? 3 : 2;
    }
}
