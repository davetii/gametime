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
        return pickShotType(shooter, 1.0, rng);
    }

    /**
     * §3.4: {@code shotMixLean} (the offensive coach's offensiveScheme modifier)
     * leans the draw toward perimeter shooting — the PERIMETER and THREE weights
     * are scaled by it, so a high-offensiveScheme coach takes more jumpers and a
     * low one leans inside (drive/post). 1.0 = the raw skill-weighted draw.
     */
    public ShotType pickShotType(PlayerGameState shooter, double shotMixLean,
                                 RandomGenerator rng) {
        ShotType[] types = ShotType.values();
        double totalWeight = 0;
        for (ShotType t : types) {
            totalWeight += leanedWeight(shooter, t, shotMixLean);
        }
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (ShotType t : types) {
            cumulative += leanedWeight(shooter, t, shotMixLean);
            if (roll < cumulative) return t;
        }
        return types[types.length - 1];
    }

    private double leanedWeight(PlayerGameState shooter, ShotType type, double shotMixLean) {
        double w = shooter.shotTypeWeight(type);
        return (type == ShotType.PERIMETER || type == ShotType.THREE) ? w * shotMixLean : w;
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
