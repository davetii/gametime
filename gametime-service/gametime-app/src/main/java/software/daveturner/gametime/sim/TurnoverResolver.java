package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class TurnoverResolver {

    private final SimConfig config;

    public TurnoverResolver(SimConfig config) {
        this.config = config;
    }

    public boolean isTurnover(PlayerGameState ballHandler,
                              List<PlayerGameState> defenders, RandomGenerator rng) {
        double avgDefense = defenders.stream()
                .mapToDouble(d -> (d.getStealing() + d.getIndividualDefense()) / 2.0)
                .average()
                .orElse(SimConfig.SCALE_AVG);
        double prob = config.contestProbability(
                SimConfig.BASE_TURNOVER, avgDefense, ballHandler.getBallSecurity());
        return rng.nextDouble() < prob;
    }

    public boolean isStolen(RandomGenerator rng) {
        return rng.nextDouble() < 0.6;
    }

    public PlayerGameState pickStealer(List<PlayerGameState> defenders, RandomGenerator rng) {
        double totalWeight = defenders.stream()
                .mapToDouble(PlayerGameState::getStealing)
                .sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState d : defenders) {
            cumulative += d.getStealing();
            if (roll < cumulative) return d;
        }
        return defenders.get(defenders.size() - 1);
    }
}
