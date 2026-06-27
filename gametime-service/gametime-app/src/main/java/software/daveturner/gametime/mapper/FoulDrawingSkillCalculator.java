package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Getting to the free throw line — initiating contact, selling fouls. Aggression
 * drives it; quickness and IQ create the angles, composure sells it calmly. Refs
 * call fewer fouls for big men.
 */
@Component
public class FoulDrawingSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAggression() * 3) + (player.getAgility() * 2)
                + (player.getIntelligence() * 2) + player.getSpeed()) / 8d;

        value += adj(player.getAggression());
        value += adj(player.getHandle(), COMBO_FACTOR);
        value += adj(player.getComposure(), COMBO_FACTOR);

        // Fearlessness attacking the basket.
        value += adj(player.getEgo(), COMBO_FACTOR);

        // Refs are less likely to call fouls for big players.
        value -= adj(player.getSize(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
