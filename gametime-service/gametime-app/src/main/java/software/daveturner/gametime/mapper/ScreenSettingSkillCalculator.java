package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Pick quality, screen angles, roll/pop timing. Strength and size set the screen;
 * cohesion makes it unselfish, awareness times the roll. Ego works against setting
 * hard screens.
 */
@Component
public class ScreenSettingSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getStrength() * 3) + (player.getSize() * 2)
                + (player.getCohesion() * 2) + player.getAwareness()) / 8d;

        value += adj(player.getStrength());
        value += adj(player.getSize(), COMBO_FACTOR);
        value += adj(player.getAwareness(), COMBO_FACTOR);
        value += adj(player.getCohesion(), COMBO_FACTOR);

        // Selfish players don't set hard screens.
        value -= adj(player.getEgo(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
