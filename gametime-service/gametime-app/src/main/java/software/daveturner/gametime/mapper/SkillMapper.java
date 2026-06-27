package software.daveturner.gametime.mapper;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import software.daveturner.gametime.model.Player;
import software.daveturner.gametime.model.PlayerSkills;

@Component
public class SkillMapper {

	@Autowired
    private AcumenSkillCalculator acumenSkillCalculator;

    @Autowired
    private BallSecuritySkillCalculator ballSecuritySkillCalculator;

    @Autowired
    private DefenseReboundSkillCalculator defenseReboundSkillCalculator;

    @Autowired
    private DriveSkillCalculator driveSkillCalculator;

    @Autowired
    private FreeThrowSkillCalculator freeThrowSkillCalculator;

    @Autowired
    private IndividualDefenseSkillCalculator individualDefenseSkillCalculator;

    @Autowired
    private LongRangeSkillCalculator longRangeSkillCalculator;

    @Autowired
    private OffenseReboundSkillCalculator offenseReboundSkillCalculator;

    @Autowired
    private PassingSkillCalculator passingSkillCalculator;

    @Autowired
    private PerimeterScoringSkillCalculator perimeterScoringSkillCalculator;

    @Autowired
    private PostScoringSkillCalculator postScoringSkillCalculator;

    @Autowired
    private TeamDefenseSkillCalculator teamDefenseSkillCalculator;

    @Autowired
    private TeamOffenseSkillCalculator teamOffenseSkillCalculator;

    @Autowired
    private FinishingSkillCalculator finishingSkillCalculator;

    @Autowired
    private TransitionSkillCalculator transitionSkillCalculator;

    @Autowired
    private RimProtectionSkillCalculator rimProtectionSkillCalculator;

    @Autowired
    private StealingSkillCalculator stealingSkillCalculator;

    @Autowired
    private ShotContestSkillCalculator shotContestSkillCalculator;

    @Autowired
    private FoulDrawingSkillCalculator foulDrawingSkillCalculator;

    @Autowired
    private FoulProneSkillCalculator foulProneSkillCalculator;

    @Autowired
    private ClutchSkillCalculator clutchSkillCalculator;

    @Autowired
    private ScreenSettingSkillCalculator screenSettingSkillCalculator;

    @Autowired
    private OffBallMovementSkillCalculator offBallMovementSkillCalculator;

    public PlayerSkills mapSkills(Player player) {
        PlayerSkills skills = new PlayerSkills();
        skills.setAcumen(acumenSkillCalculator.calc(player));
        skills.setBallSecurity(ballSecuritySkillCalculator.calc(player));
        skills.setDefenseRebound(defenseReboundSkillCalculator.calc(player));
        skills.setDrive(driveSkillCalculator.calc(player));
        skills.setFreeThrows(freeThrowSkillCalculator.calc(player));
        skills.setIndividualDefense(individualDefenseSkillCalculator.calc(player));
        skills.setLongRange(longRangeSkillCalculator.calc(player));
        skills.setOffenseRebound(offenseReboundSkillCalculator.calc(player));
        skills.setPassing(passingSkillCalculator.calc(player));
        skills.setPerimeter(perimeterScoringSkillCalculator.calc(player));
        skills.setPost(postScoringSkillCalculator.calc(player));
        skills.setTeamDefense(teamDefenseSkillCalculator.calc(player));
        skills.setTeamOffense(teamOffenseSkillCalculator.calc(player));
        skills.setFinishing(finishingSkillCalculator.calc(player));
        skills.setTransition(transitionSkillCalculator.calc(player));
        skills.setRimProtection(rimProtectionSkillCalculator.calc(player));
        skills.setStealing(stealingSkillCalculator.calc(player));
        skills.setShotContest(shotContestSkillCalculator.calc(player));
        skills.setFoulDrawing(foulDrawingSkillCalculator.calc(player));
        skills.setFoulProne(foulProneSkillCalculator.calc(player));
        skills.setClutch(clutchSkillCalculator.calc(player));
        skills.setScreenSetting(screenSettingSkillCalculator.calc(player));
        skills.setOffBallMovement(offBallMovementSkillCalculator.calc(player));
        return skills;
    }
}
