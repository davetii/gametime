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
        return isMade(shotType, shooter, defender, 1.0, rng);
    }

    /**
     * §3.4 (Decision C): {@code chemistryMultiplier} bends the contested make
     * probability — the shooter's {@code acumen} (shot-quality nudge) and the
     * team-efficiency edge ({@code teamOffense} − opponent {@code teamDefense}),
     * combined in {@link SimConfig#chemistryMakeMultiplier}. 1.0 = the raw
     * player-skill contest (a modest thumb on the scale, not a replacement).
     */
    public boolean isMade(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, double chemistryMultiplier,
                          RandomGenerator rng) {
        double offense = shooter.offenseSkillForShot(shotType);
        double defense = defenseSkillForShot(shotType, defender);
        double base = baseProbability(shotType);
        double prob = config.clampProbability(
                chemistryMultiplier * config.contestProbability(base, offense, defense));
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
