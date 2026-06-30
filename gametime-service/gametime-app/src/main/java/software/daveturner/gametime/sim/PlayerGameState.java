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

    // Rebounding skills (§3.3)
    private final double offenseRebound;
    private final double defenseRebound;

    // Team chemistry skills (§3.4)
    private final double teamOffense;
    private final double teamDefense;
    private final double passing;
    private final double acumen;

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
    private int offensiveRebounds;
    private int defensiveRebounds;
    private int assists;

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
        this.offenseRebound = toDouble(skills.getOffenseRebound());
        this.defenseRebound = toDouble(skills.getDefenseRebound());
        this.teamOffense = toDouble(skills.getTeamOffense());
        this.teamDefense = toDouble(skills.getTeamDefense());
        this.passing = toDouble(skills.getPassing());
        this.acumen = toDouble(skills.getAcumen());
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
    public double getOffenseRebound() { return offenseRebound; }
    public double getDefenseRebound() { return defenseRebound; }
    public double getTeamOffense() { return teamOffense; }
    public double getTeamDefense() { return teamDefense; }
    public double getPassing() { return passing; }
    public double getAcumen() { return acumen; }

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
    public int getOffensiveRebounds() { return offensiveRebounds; }
    public int getDefensiveRebounds() { return defensiveRebounds; }
    public int getAssists() { return assists; }

    public void recordFieldGoalAttempt() { fieldGoalsAttempted++; }
    public void recordFieldGoalMade(int pts) { fieldGoalsMade++; points += pts; }
    public void recordThreePointAttempt() { threePointersAttempted++; }
    public void recordThreePointMade() { threePointersMade++; }
    public void recordFreeThrowAttempt() { freeThrowsAttempted++; }
    public void recordFreeThrowMade() { freeThrowsMade++; points += 1; }
    public void recordTurnover() { turnovers++; }
    public void recordSteal() { steals++; }
    public void recordFoul() { fouls++; }
    public void recordOffensiveRebound() { offensiveRebounds++; }
    public void recordDefensiveRebound() { defensiveRebounds++; }
    public void recordAssist() { assists++; }
}
