package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Mid-range and perimeter shot-making. Shot skill dominates, with shot selection
 * and quickness contributing; big men score a touch less from the perimeter.
 */
@Component
public class PerimeterScoringSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getShotSkill() * 4) + (player.getShotSelection() * 2)
                + player.getLuck() + player.getIntelligence()
                + player.getAgility() + player.getSpeed()) / 10d;

        value += adj(player.getShotSkill());
        value += adj(player.getShotSelection(), COMBO_FACTOR);

        // Bigs are slightly less effective from the perimeter.
        value -= adj(player.getSize(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
