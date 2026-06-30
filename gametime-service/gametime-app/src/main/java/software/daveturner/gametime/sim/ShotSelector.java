package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class ShotSelector {

    public PlayerGameState pickShooter(List<PlayerGameState> offensivePlayers, RandomGenerator rng) {
        double totalWeight = offensivePlayers.stream()
                .mapToDouble(PlayerGameState::offensiveWeight)
                .sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : offensivePlayers) {
            cumulative += p.offensiveWeight();
            if (roll < cumulative) return p;
        }
        return offensivePlayers.get(offensivePlayers.size() - 1);
    }

    public ShotType pickShotType(PlayerGameState shooter, RandomGenerator rng) {
        ShotType[] types = ShotType.values();
        double totalWeight = 0;
        for (ShotType t : types) {
            totalWeight += shooter.shotTypeWeight(t);
        }
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (ShotType t : types) {
            cumulative += shooter.shotTypeWeight(t);
            if (roll < cumulative) return t;
        }
        return types[types.length - 1];
    }

    public PlayerGameState pickDefender(List<PlayerGameState> defensivePlayers, RandomGenerator rng) {
        double totalWeight = defensivePlayers.stream()
                .mapToDouble(PlayerGameState::getIndividualDefense)
                .sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState d : defensivePlayers) {
            cumulative += d.getIndividualDefense();
            if (roll < cumulative) return d;
        }
        return defensivePlayers.get(defensivePlayers.size() - 1);
    }
}
