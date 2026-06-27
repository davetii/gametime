package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Court vision, finding open teammates, pass accuracy. Handle and intelligence
 * carry it; awareness sharpens the read; experience adds polish.
 */
@Component
public class PassingSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getIntelligence() * 2) + (player.getHandle() * 3)
                + player.getLuck()) / 6d;

        value += adj(player.getHandle());
        value += adj(player.getIntelligence());
        value += adj(player.getAwareness(), COMBO_FACTOR);

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
