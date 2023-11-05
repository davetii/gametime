package software.daveturner.gametime.entity;

public enum Position {

    PG("PG", "Point Guard", 1),
    CG("CG", "Combo Guard", 2),
    BG("BG", "Two Guard", 3),
    WING("W", "Wing", 4),
    SF("SF", "Small Forward", 5),
    F("F", "Forward", 6),
    PF("PF", "Power Forward", 7),
    FC("FC", "Forward Center", 8),
    C("C", "Center", 9);


    public final String id;
    public final String name;
    public final int sort;
    Position(String id, String name, int sort) {
        this.id = id;
        this.name = name;
        this.sort = sort;
    }
}
