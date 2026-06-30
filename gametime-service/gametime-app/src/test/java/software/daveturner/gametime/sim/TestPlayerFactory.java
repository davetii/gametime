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

    private static BigDecimal bd(double val) {
        return BigDecimal.valueOf(val);
    }
}
