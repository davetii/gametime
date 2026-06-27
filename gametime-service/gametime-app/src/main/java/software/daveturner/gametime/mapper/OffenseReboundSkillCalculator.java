package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Crashing the offensive glass for putbacks and second chances. Effort
 * (determination, energy) plus size/strength; verticality and length help, and a
 * want-the-ball edge (ego) helps here.
 */
@Component
public class OffenseReboundSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = (player.getEgo() + (player.getDetermination() * 3)
                + (player.getEnergy() * 2) + (player.getIntelligence() * 2)
                + (player.getSize() * 2) + (player.getStrength() * 2)) / 12d;

        value += adj(player.getDetermination());
        value += adj(player.getSize());
        value += adj(player.getVerticality(), COMBO_FACTOR);
        value += adj(player.getWingspan(), COMBO_FACTOR);

        // Want-the-ball factor: ego helps on the offensive glass.
        value += adj(player.getEgo(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
