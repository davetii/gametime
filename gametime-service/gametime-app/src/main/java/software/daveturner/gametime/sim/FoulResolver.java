package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
public class FoulResolver {

    private final SimConfig config;

    public FoulResolver(SimConfig config) {
        this.config = config;
    }

    public boolean isFoul(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, RandomGenerator rng) {
        return isFoul(shotType, shooter, defender, 1.0, rng);
    }

    /**
     * §3.4: {@code defensivePressure} (the defending coach's defensiveScheme
     * modifier) scales the foul rate — an aggressive, gambling defense both forces
     * turnovers and concedes more fouls (the pressure/breakdown trade-off in
     * coach.md). 1.0 = neutral.
     */
    public boolean isFoul(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, double defensivePressure,
                          RandomGenerator rng) {
        if (!shotType.isContactType()) return false;
        // Both foulDrawing and foulProne increase foul probability.
        // foulProne is inverted: a high value means the defender fouls more (low discipline).
        double effectiveDefense = SimConfig.SCALE_AVG * 2 - defender.getFoulProne();
        double prob = config.clampProbability(defensivePressure * config.contestProbability(
                SimConfig.BASE_FOUL, shooter.getFoulDrawing(), effectiveDefense));
        return rng.nextDouble() < prob;
    }

    public boolean isFreeThrowMade(PlayerGameState shooter, RandomGenerator rng) {
        double prob = config.freeThrowProbability(shooter.getFreeThrows());
        return rng.nextDouble() < prob;
    }
}
