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
     * <p>A <b>rule of basketball, not a tunable</b> — which is why it lives on the type
     * and deliberately NOT in {@link SimConfig}, the tuning surface.
     *
     * <p><b>⚠ Applies to the STOPPED-shot foul only.</b> An and-1 is always exactly
     * <b>one</b> free throw for every type, a made three included: a made 3 plus a foul
     * is 3 points and 1 FT, not 3 FTs. Do not make {@code AND_ONE_FREE_THROWS} graduate
     * in parallel with this.
     */
    public int freeThrowsIfFouled() {
        return this == THREE ? 3 : 2;
    }
}
