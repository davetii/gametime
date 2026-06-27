package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Three-point shooting. Shot selection and raw shot skill dominate; big men
 * shoot a touch less efficiently from deep.
 */
@Component
public class LongRangeSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getShotSkill() * 3) + (player.getShotSelection() * 4)
                + (player.getLuck() * 2) + player.getIntelligence()) / 10d;

        value += adj(player.getShotSelection());
        value += adj(player.getShotSkill());

        // Bigs are slightly less efficient from range.
        value -= adj(player.getSize(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
