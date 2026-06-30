package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Resolves who collects a missed shot (§3.3). Mirrors {@link FoulResolver}:
 * constructor-injected {@link SimConfig}, pure math, seeded RNG. The offensive
 * vs. defensive contest reuses the logistic-contest formula
 * ({@link SimConfig#contestProbability}); rebounder selection reuses the
 * skill-weighted draw pattern from {@link ShotSelector}.
 */
@Component
public class ReboundResolver {

    private final SimConfig config;

    public ReboundResolver(SimConfig config) {
        this.config = config;
    }

    /**
     * Contest the offensive rebounder's {@code offenseRebound} against the
     * defensive rebounder's {@code defenseRebound}. Two average players yield
     * {@link SimConfig#BASE_OFFENSIVE_REBOUND}.
     */
    public boolean isOffensiveRebound(PlayerGameState offRebounder,
                                      PlayerGameState defRebounder,
                                      RandomGenerator rng) {
        double prob = config.contestProbability(
                SimConfig.BASE_OFFENSIVE_REBOUND,
                offRebounder.getOffenseRebound(),
                defRebounder.getDefenseRebound());
        return rng.nextDouble() < prob;
    }

    /** Pick the offensive rebounder by {@code offenseRebound} skill weight. */
    public PlayerGameState pickOffensiveRebounder(List<PlayerGameState> offense,
                                                  RandomGenerator rng) {
        return pickWeighted(offense, rng, PlayerGameState::getOffenseRebound);
    }

    /** Pick the defensive rebounder by {@code defenseRebound} skill weight. */
    public PlayerGameState pickDefensiveRebounder(List<PlayerGameState> defense,
                                                  RandomGenerator rng) {
        return pickWeighted(defense, rng, PlayerGameState::getDefenseRebound);
    }

    private PlayerGameState pickWeighted(List<PlayerGameState> players,
                                         RandomGenerator rng,
                                         java.util.function.ToDoubleFunction<PlayerGameState> weight) {
        double totalWeight = players.stream().mapToDouble(weight).sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : players) {
            cumulative += weight.applyAsDouble(p);
            if (roll < cumulative) return p;
        }
        return players.get(players.size() - 1);
    }
}
