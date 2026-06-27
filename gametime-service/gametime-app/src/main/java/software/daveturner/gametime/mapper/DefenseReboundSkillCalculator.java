package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Boxing out and securing defensive boards. Size, strength and determination
 * carry it; length (wingspan) and a want-the-ball edge (ego) help on the new scale.
 */
@Component
public class DefenseReboundSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getDetermination() * 3) + player.getEnergy()
                + player.getIntelligence() + (player.getSize() * 2)
                + (player.getStrength() * 2) + (player.getHealth())) / 10d;

        value += adj(player.getSize());
        value += adj(player.getStrength());
        value += adj(player.getWingspan(), COMBO_FACTOR);
        value += adj(player.getVerticality(), COMBO_FACTOR);

        // Want-the-ball factor: ego helps here (double-edged elsewhere).
        value += adj(player.getEgo(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
