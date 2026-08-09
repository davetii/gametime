package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.*;

import java.math.BigDecimal;

class TestPlayerFactory {

    static PlayerGameState create(String id, String teamId, double allSkillLevel) {
        return create(id, teamId, allSkillLevel, allSkillLevel, allSkillLevel,
                allSkillLevel, allSkillLevel, allSkillLevel, allSkillLevel,
                allSkillLevel, allSkillLevel, allSkillLevel, allSkillLevel,
                allSkillLevel, allSkillLevel, allSkillLevel, allSkillLevel);
    }

    // 13-skill overload (no rebound skills) — rebounds default to league average.
    static PlayerGameState create(String id, String teamId,
                                  double drive, double finishing, double perimeter,
                                  double post, double longRange, double ballSecurity,
                                  double freeThrows, double foulDrawing,
                                  double individualDefense, double rimProtection,
                                  double shotContest, double stealing, double foulProne) {
        return create(id, teamId, drive, finishing, perimeter, post, longRange,
                ballSecurity, freeThrows, foulDrawing, individualDefense,
                rimProtection, shotContest, stealing, foulProne,
                SimConfig.SCALE_AVG, SimConfig.SCALE_AVG);
    }

    // 15-skill overload — adds offenseRebound/defenseRebound (§3.3); chemistry
    // skills (§3.4) default to league average.
    static PlayerGameState create(String id, String teamId,
                                  double drive, double finishing, double perimeter,
                                  double post, double longRange, double ballSecurity,
                                  double freeThrows, double foulDrawing,
                                  double individualDefense, double rimProtection,
                                  double shotContest, double stealing, double foulProne,
                                  double offenseRebound, double defenseRebound) {
        return create(id, teamId, drive, finishing, perimeter, post, longRange,
                ballSecurity, freeThrows, foulDrawing, individualDefense,
                rimProtection, shotContest, stealing, foulProne,
                offenseRebound, defenseRebound,
                SimConfig.SCALE_AVG, SimConfig.SCALE_AVG,
                SimConfig.SCALE_AVG, SimConfig.SCALE_AVG);
    }

    // 19-skill overload — adds teamOffense/teamDefense/passing/acumen (§3.4).
    static PlayerGameState create(String id, String teamId,
                                  double drive, double finishing, double perimeter,
                                  double post, double longRange, double ballSecurity,
                                  double freeThrows, double foulDrawing,
                                  double individualDefense, double rimProtection,
                                  double shotContest, double stealing, double foulProne,
                                  double offenseRebound, double defenseRebound,
                                  double teamOffense, double teamDefense,
                                  double passing, double acumen) {
        Player player = new Player();
        player.setId(id);
        PlayerSkills skills = new PlayerSkills();
        skills.setDrive(bd(drive));
        skills.setFinishing(bd(finishing));
        skills.setPerimeter(bd(perimeter));
        skills.setPost(bd(post));
        skills.setLongRange(bd(longRange));
        skills.setBallSecurity(bd(ballSecurity));
        skills.setFreeThrows(bd(freeThrows));
        skills.setFoulDrawing(bd(foulDrawing));
        skills.setIndividualDefense(bd(individualDefense));
        skills.setRimProtection(bd(rimProtection));
        skills.setShotContest(bd(shotContest));
        skills.setStealing(bd(stealing));
        skills.setFoulProne(bd(foulProne));
        skills.setOffenseRebound(bd(offenseRebound));
        skills.setDefenseRebound(bd(defenseRebound));
        skills.setTeamOffense(bd(teamOffense));
        skills.setTeamDefense(bd(teamDefense));
        skills.setPassing(bd(passing));
        skills.setAcumen(bd(acumen));
        player.setSkills(skills);

        RosterEntry entry = new RosterEntry();
        entry.setPlayer(player);
        entry.setLineupRole(LineupRole.STARTER);

        return new PlayerGameState(id, teamId, entry);
    }

    /**
     * §3.5 overload: an all-average-skill player with an explicit rotation slot
     * (lineupRole + rotationOrder) and endurance/energy attributes, for testing
     * fatigue, substitution, and minutes. Skills are all {@code allSkillLevel}.
     */
    static PlayerGameState createRotationPlayer(String id, String teamId, double allSkillLevel,
                                                LineupRole role, Integer rotationOrder,
                                                int endurance, int energy) {
        Player player = new Player();
        player.setId(id);
        player.setEndurance(endurance);
        player.setEnergy(energy);
        PlayerSkills skills = new PlayerSkills();
        skills.setDrive(bd(allSkillLevel));
        skills.setFinishing(bd(allSkillLevel));
        skills.setPerimeter(bd(allSkillLevel));
        skills.setPost(bd(allSkillLevel));
        skills.setLongRange(bd(allSkillLevel));
        skills.setBallSecurity(bd(allSkillLevel));
        skills.setFreeThrows(bd(allSkillLevel));
        skills.setFoulDrawing(bd(allSkillLevel));
        skills.setIndividualDefense(bd(allSkillLevel));
        skills.setRimProtection(bd(allSkillLevel));
        skills.setShotContest(bd(allSkillLevel));
        skills.setStealing(bd(allSkillLevel));
        skills.setFoulProne(bd(allSkillLevel));
        skills.setOffenseRebound(bd(allSkillLevel));
        skills.setDefenseRebound(bd(allSkillLevel));
        skills.setTeamOffense(bd(allSkillLevel));
        skills.setTeamDefense(bd(allSkillLevel));
        skills.setPassing(bd(allSkillLevel));
        skills.setAcumen(bd(allSkillLevel));
        player.setSkills(skills);

        RosterEntry entry = new RosterEntry();
        entry.setPlayer(player);
        entry.setLineupRole(role);
        entry.setRotationOrder(rotationOrder);

        return new PlayerGameState(id, teamId, entry);
    }

    /**
     * §3.13 overload: a rotation player whose skills are all {@code allSkillLevel}
     * EXCEPT the five that feed {@code valueComposite()} (individualDefense,
     * rimProtection, defenseRebound, and the five offense skills), which are set to
     * {@code valueSkillLevel}. Because the composite is built only from those, this
     * gives a player an exact, predictable value while leaving foulProne and the
     * rest at league average — so foul-trouble tests vary value alone.
     */
    static PlayerGameState createValuedRotationPlayer(String id, String teamId,
                                                      double allSkillLevel, double valueSkillLevel,
                                                      LineupRole role, Integer rotationOrder,
                                                      int endurance, int energy) {
        Player player = new Player();
        player.setId(id);
        player.setEndurance(endurance);
        player.setEnergy(energy);
        PlayerSkills skills = new PlayerSkills();
        // The five offense skills + the three defense/rebound skills the composite reads.
        skills.setDrive(bd(valueSkillLevel));
        skills.setFinishing(bd(valueSkillLevel));
        skills.setPerimeter(bd(valueSkillLevel));
        skills.setPost(bd(valueSkillLevel));
        skills.setLongRange(bd(valueSkillLevel));
        skills.setIndividualDefense(bd(valueSkillLevel));
        skills.setRimProtection(bd(valueSkillLevel));
        skills.setDefenseRebound(bd(valueSkillLevel));
        // Everything else stays at the baseline level.
        skills.setBallSecurity(bd(allSkillLevel));
        skills.setFreeThrows(bd(allSkillLevel));
        skills.setFoulDrawing(bd(allSkillLevel));
        skills.setShotContest(bd(allSkillLevel));
        skills.setStealing(bd(allSkillLevel));
        skills.setFoulProne(bd(allSkillLevel));
        skills.setOffenseRebound(bd(allSkillLevel));
        skills.setTeamOffense(bd(allSkillLevel));
        skills.setTeamDefense(bd(allSkillLevel));
        skills.setPassing(bd(allSkillLevel));
        skills.setAcumen(bd(allSkillLevel));
        player.setSkills(skills);

        RosterEntry entry = new RosterEntry();
        entry.setPlayer(player);
        entry.setLineupRole(role);
        entry.setRotationOrder(rotationOrder);

        return new PlayerGameState(id, teamId, entry);
    }

    private static BigDecimal bd(double val) {
        return BigDecimal.valueOf(val);
    }
}
