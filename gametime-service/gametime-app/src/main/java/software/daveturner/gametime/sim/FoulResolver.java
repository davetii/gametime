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
        if (!shotType.isContactType()) return false;
        // Both foulDrawing and foulProne increase foul probability.
        // foulProne is inverted: a high value means the defender fouls more (low discipline).
        double effectiveDefense = SimConfig.SCALE_AVG * 2 - defender.getFoulProne();
        double prob = config.contestProbability(
                SimConfig.BASE_FOUL, shooter.getFoulDrawing(), effectiveDefense);
        return rng.nextDouble() < prob;
    }

    public boolean isFreeThrowMade(PlayerGameState shooter, RandomGenerator rng) {
        double prob = config.freeThrowProbability(shooter.getFreeThrows());
        return rng.nextDouble() < prob;
    }
}
