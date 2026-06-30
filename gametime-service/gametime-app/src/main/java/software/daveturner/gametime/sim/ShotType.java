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

    public boolean isContactType() {
        return this == DRIVE || this == POST;
    }
}
