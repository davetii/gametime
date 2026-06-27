package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Scoring at the rim — dunks, contested layups, lob catching, finishing through
 * contact. Verticality and athleticism drive it; a powerful frame helps finish
 * through bodies. Declines with age.
 */
@Component
public class FinishingSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getVerticality() * 3) + (player.getAgility() * 2)
                + (player.getStrength() * 2) + player.getHandle()) / 8d;

        value += adj(player.getVerticality());
        value += adj(player.getSpeed(), COMBO_FACTOR);

        // Powerful finisher: size paired with strength.
        value += comboAdj(player.getSize(), player.getStrength(), COMBO_FACTOR);

        // Athletic skill — declines with age.
        if (player.getYearsPro() > 12) value -= 2;
        else if (player.getYearsPro() > 10) value -= 1;

        return round(clamp(value));
    }
}
