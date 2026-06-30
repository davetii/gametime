package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.PlayerSkills;
import software.daveturner.gametime.model.RosterEntry;

import java.math.BigDecimal;

public class PlayerGameState {

    private final String playerId;
    private final String teamId;

    // Offensive skills (converted to double at engine boundary)
    private final double drive;
    private final double finishing;
    private final double perimeter;
    private final double post;
    private final double longRange;
    private final double ballSecurity;
    private final double freeThrows;
    private final double foulDrawing;

    // Defensive skills
    private final double individualDefense;
    private final double rimProtection;
    private final double shotContest;
    private final double stealing;
    private final double foulProne;

    // Box score accumulators
    private int points;
    private int fieldGoalsAttempted;
    private int fieldGoalsMade;
    private int threePointersAttempted;
    private int threePointersMade;
    private int freeThrowsAttempted;
    private int freeThrowsMade;
    private int turnovers;
    private int steals;
    private int fouls;

    public PlayerGameState(String playerId, String teamId, RosterEntry entry) {
        this.playerId = playerId;
        this.teamId = teamId;
        PlayerSkills skills = entry.getPlayer().getSkills();
        this.drive = toDouble(skills.getDrive());
        this.finishing = toDouble(skills.getFinishing());
        this.perimeter = toDouble(skills.getPerimeter());
        this.post = toDouble(skills.getPost());
        this.longRange = toDouble(skills.getLongRange());
        this.ballSecurity = toDouble(skills.getBallSecurity());
        this.freeThrows = toDouble(skills.getFreeThrows());
        this.foulDrawing = toDouble(skills.getFoulDrawing());
        this.individualDefense = toDouble(skills.getIndividualDefense());
        this.rimProtection = toDouble(skills.getRimProtection());
        this.shotContest = toDouble(skills.getShotContest());
        this.stealing = toDouble(skills.getStealing());
        this.foulProne = toDouble(skills.getFoulProne());
    }

    private static double toDouble(BigDecimal bd) {
        return bd == null ? SimConfig.SCALE_AVG : bd.doubleValue();
    }

    public double offensiveWeight() {
        return drive + finishing + perimeter + post + longRange;
    }

    public double shotTypeWeight(ShotType type) {
        return switch (type) {
            case DRIVE -> drive + finishing;
            case PERIMETER -> perimeter;
            case POST -> post;
            case THREE -> longRange;
        };
    }

    public double offenseSkillForShot(ShotType type) {
        return switch (type) {
            case DRIVE -> (drive + finishing) / 2.0;
            case PERIMETER -> perimeter;
            case POST -> post;
            case THREE -> longRange;
        };
    }

    // Getters
    public String getPlayerId() { return playerId; }
    public String getTeamId() { return teamId; }
    public double getDrive() { return drive; }
    public double getFinishing() { return finishing; }
    public double getPerimeter() { return perimeter; }
    public double getPost() { return post; }
    public double getLongRange() { return longRange; }
    public double getBallSecurity() { return ballSecurity; }
    public double getFreeThrows() { return freeThrows; }
    public double getFoulDrawing() { return foulDrawing; }
    public double getIndividualDefense() { return individualDefense; }
    public double getRimProtection() { return rimProtection; }
    public double getShotContest() { return shotContest; }
    public double getStealing() { return stealing; }
    public double getFoulProne() { return foulProne; }

    // Box score
    public int getPoints() { return points; }
    public int getFieldGoalsAttempted() { return fieldGoalsAttempted; }
    public int getFieldGoalsMade() { return fieldGoalsMade; }
    public int getThreePointersAttempted() { return threePointersAttempted; }
    public int getThreePointersMade() { return threePointersMade; }
    public int getFreeThrowsAttempted() { return freeThrowsAttempted; }
    public int getFreeThrowsMade() { return freeThrowsMade; }
    public int getTurnovers() { return turnovers; }
    public int getSteals() { return steals; }
    public int getFouls() { return fouls; }

    public void recordFieldGoalAttempt() { fieldGoalsAttempted++; }
    public void recordFieldGoalMade(int pts) { fieldGoalsMade++; points += pts; }
    public void recordThreePointAttempt() { threePointersAttempted++; }
    public void recordThreePointMade() { threePointersMade++; }
    public void recordFreeThrowAttempt() { freeThrowsAttempted++; }
    public void recordFreeThrowMade() { freeThrowsMade++; points += 1; }
    public void recordTurnover() { turnovers++; }
    public void recordSteal() { steals++; }
    public void recordFoul() { fouls++; }
}
