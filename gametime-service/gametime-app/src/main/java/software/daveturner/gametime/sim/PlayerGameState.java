package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.LineupRole;
import software.daveturner.gametime.model.Player;
import software.daveturner.gametime.model.PlayerSkills;
import software.daveturner.gametime.model.RosterEntry;

import java.math.BigDecimal;

public class PlayerGameState {

    private final String playerId;
    private final String teamId;

    // Rotation identity (§3.5): the roster slot + bench-queue position drive
    // substitution priority (starters pulled later / return first) and the order
    // bench players enter. STARTER carries a null rotationOrder (#014).
    private final boolean starter;
    private final Integer rotationOrder;

    // Fatigue attributes (§3.5): endurance slows the per-possession energy drain;
    // energy seeds a player's starting in-game currentEnergy above/below full.
    private final double endurance;
    private final double energy;

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

    // Fatigue / minutes state (§3.5). currentEnergy drains on-floor and recovers
    // benched; onFloorPossessions accumulates the possession share that maps to
    // minutes at box-score-write time (Decision A). Within-game only — resets each
    // game because a fresh PlayerGameState is built per simulation.
    private double currentEnergy;
    private int onFloorPossessions;

    public PlayerGameState(String playerId, String teamId, RosterEntry entry) {
        this.playerId = playerId;
        this.teamId = teamId;
        Player player = entry.getPlayer();
        // A null lineupRole ⇒ not a starter (the correct default). In production
        // GameSimulator.buildRotation only ever passes STARTER entries or bench
        // entries with a rotationOrder, so the null path is test-only.
        this.starter = entry.getLineupRole() == LineupRole.STARTER;
        this.rotationOrder = entry.getRotationOrder();
        this.endurance = attrToDouble(player.getEndurance());
        this.energy = attrToDouble(player.getEnergy());
        // Everyone tips off with a FULL tank (Decision B: within-game fatigue only,
        // energy resets each game — so all players start equally fresh). The two
        // attributes act per possession, NOT at start: endurance slows the on-floor
        // drain, energy speeds the benched recovery. (This is a deliberate deviation
        // from Decision B's "starting energy derived from endurance/energy" wording
        // — a flat start keeps ×1.0 = a full tank for everyone; see decisions.md
        // #023 Decision B note.)
        this.currentEnergy = SimConfig.MAX_ENERGY;
        PlayerSkills skills = player.getSkills();
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

    // endurance/energy are Integer *attributes* on Player (not skills); null-safe
    // to league average (10), matching the skill toDouble discipline.
    private static double attrToDouble(Integer attr) {
        return attr == null ? SimConfig.SCALE_AVG : attr.doubleValue();
    }

    // --- Fatigue / rotation state (§3.5) ---

    public boolean isStarter() { return starter; }
    public Integer getRotationOrder() { return rotationOrder; }
    public double getEndurance() { return endurance; }
    public double getEnergy() { return energy; }
    public double getCurrentEnergy() { return currentEnergy; }
    public int getOnFloorPossessions() { return onFloorPossessions; }

    /**
     * A player is fouled out (Decision F) once their foul count reaches
     * {@link SimConfig#FOUL_OUT_LIMIT}. A derived predicate over the existing
     * counter — no stored flag; permanent for the game because fouls only grow.
     */
    public boolean isFouledOut() {
        return fouls >= SimConfig.FOUL_OUT_LIMIT;
    }

    /**
     * §3.5 (Decision B): the fatigue multiplier over this player's skills, from
     * currentEnergy. Full energy ⇒ ×1.0, declining modestly as energy drops.
     */
    public double fatigueFactor() {
        double frac = Math.max(0.0, Math.min(1.0, currentEnergy / SimConfig.MAX_ENERGY));
        return 1.0 - SimConfig.FATIGUE_MAX_PENALTY * (1.0 - frac);
    }

    /**
     * Drain energy for one on-floor possession (endurance slows the drain) and
     * accumulate the possession share used to derive minutes (Decision A). Called
     * for every on-floor player once per possession by the rotation step.
     */
    public void drainForPossession() {
        double scale = 1.0 - SimConfig.ENDURANCE_DRAIN_SENSITIVITY
                * (endurance - SimConfig.SCALE_AVG) / SimConfig.SCALE_AVG;
        scale = Math.max(SimConfig.MIN_DRAIN_SCALE, scale);
        currentEnergy = Math.max(0.0,
                currentEnergy - SimConfig.ENERGY_DRAIN_PER_POSSESSION * scale);
        onFloorPossessions++;
    }

    /** Recover energy for one possession spent benched (energy attribute speeds it). */
    public void recoverForPossession() {
        double scale = energy / SimConfig.SCALE_AVG;
        currentEnergy = Math.min(SimConfig.MAX_ENERGY,
                currentEnergy + SimConfig.ENERGY_RECOVERY_PER_POSSESSION * scale);
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
