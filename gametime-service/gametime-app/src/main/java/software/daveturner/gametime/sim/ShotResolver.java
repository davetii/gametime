package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
public class ShotResolver {

    private final SimConfig config;

    public ShotResolver(SimConfig config) {
        this.config = config;
    }

    public boolean isMade(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, RandomGenerator rng) {
        double offense = shooter.offenseSkillForShot(shotType);
        double defense = defenseSkillForShot(shotType, defender);
        double base = baseProbability(shotType);
        double prob = config.contestProbability(base, offense, defense);
        return rng.nextDouble() < prob;
    }

    double baseProbability(ShotType shotType) {
        return switch (shotType) {
            case DRIVE -> SimConfig.BASE_DRIVE;
            case PERIMETER -> SimConfig.BASE_PERIMETER;
            case POST -> SimConfig.BASE_POST;
            case THREE -> SimConfig.BASE_THREE;
        };
    }

    double defenseSkillForShot(ShotType shotType, PlayerGameState defender) {
        return switch (shotType) {
            case DRIVE -> defender.getRimProtection();
            case PERIMETER -> (defender.getIndividualDefense() + defender.getShotContest()) / 2.0;
            case POST -> defender.getIndividualDefense();
            case THREE -> defender.getShotContest();
        };
    }
}
