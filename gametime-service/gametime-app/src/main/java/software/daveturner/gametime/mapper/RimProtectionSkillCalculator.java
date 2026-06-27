package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Shot blocking, paint deterrence, interior defense. Verticality and size carry it,
 * length (wingspan) and awareness sharpen it; small players can't protect the rim.
 */
@Component
public class RimProtectionSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getSize() * 2) + (player.getVerticality() * 3)
                + (player.getAwareness() * 2) + player.getIntelligence()) / 8d;

        value += adj(player.getVerticality());
        value += adj(player.getSize());
        value += adj(player.getWingspan(), COMBO_FACTOR);

        // Genuinely small players can't protect the rim, beyond the size term above.
        if (player.getSize() < 8) value -= (8 - player.getSize()) * 0.4;

        return round(clamp(value));
    }
}
