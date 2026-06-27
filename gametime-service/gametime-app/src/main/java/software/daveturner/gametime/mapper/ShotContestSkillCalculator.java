package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Challenging shots without fouling — closing out, getting a hand up. Length
 * (wingspan) and verticality carry it; quickness closes out, composure avoids the
 * foul on the closeout.
 */
@Component
public class ShotContestSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getVerticality() * 2) + (player.getWingspan() * 3)
                + (player.getSpeed() * 2) + player.getAwareness()) / 8d;

        value += adj(player.getWingspan());
        value += adj(player.getVerticality(), COMBO_FACTOR);
        value += adj(player.getComposure(), COMBO_FACTOR);
        value += adj(player.getAgility(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
