package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Active hands, passing-lane disruption, on-ball pickpocketing. Awareness reads the
 * play; quickness and length finish it. Composure guards against reaching fouls.
 */
@Component
public class StealingSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAwareness() * 3) + (player.getSpeed() * 2)
                + (player.getAgility() * 2) + player.getWingspan()) / 8d;

        value += adj(player.getAwareness());
        value += adj(player.getWingspan(), COMBO_FACTOR);
        value += adj(player.getIntelligence(), COMBO_FACTOR);

        // Impatience (low composure) leads to reaching fouls, not steals.
        if (player.getComposure() < 8) value -= (8 - player.getComposure()) * 0.15;

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
