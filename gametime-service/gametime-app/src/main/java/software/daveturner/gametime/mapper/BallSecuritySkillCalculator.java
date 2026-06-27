package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Protecting the ball and avoiding turnovers. Driven by handle and intelligence;
 * composure steadies it, while reckless high energy/ego costs a little control.
 */
@Component
public class BallSecuritySkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getDetermination() * 2) + (player.getHandle() * 4)
                + (player.getIntelligence() * 3) + player.getLuck()) / 10d;

        value += adj(player.getHandle());
        value += adj(player.getComposure());

        // Reckless play: very high energy or ego costs a little ball control.
        if (player.getEnergy() > 14) value -= 1;
        if (player.getEgo() > 14) value -= 1;

        return round(clamp(value));
    }
}
