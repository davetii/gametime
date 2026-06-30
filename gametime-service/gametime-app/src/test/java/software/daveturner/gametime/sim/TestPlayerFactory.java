package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.*;

import java.math.BigDecimal;

class TestPlayerFactory {

    static PlayerGameState create(String id, String teamId, double allSkillLevel) {
        return create(id, teamId, allSkillLevel, allSkillLevel, allSkillLevel,
                allSkillLevel, allSkillLevel, allSkillLevel, allSkillLevel,
                allSkillLevel, allSkillLevel, allSkillLevel, allSkillLevel,
                allSkillLevel, allSkillLevel);
    }

    static PlayerGameState create(String id, String teamId,
                                  double drive, double finishing, double perimeter,
                                  double post, double longRange, double ballSecurity,
                                  double freeThrows, double foulDrawing,
                                  double individualDefense, double rimProtection,
                                  double shotContest, double stealing, double foulProne) {
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
