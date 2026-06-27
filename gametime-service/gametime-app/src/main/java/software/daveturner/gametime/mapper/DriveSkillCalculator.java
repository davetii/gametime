package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Attacking the basket off the dribble. Agility, handle and speed drive it;
 * verticality helps finish through contact; big men and aging legs are penalized.
 */
@Component
public class DriveSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAgility() * 3) + player.getDetermination()
                + (player.getHandle() * 2) + player.getSpeed()) / 7d;

        value += adj(player.getSpeed());
        value += adj(player.getShotSkill(), COMBO_FACTOR);
        value += adj(player.getStrength(), COMBO_FACTOR);
        value += adj(player.getVerticality(), COMBO_FACTOR);

        // Big men can't drive as well; subtract their size advantage.
        value -= adj(player.getSize());

        // Drive is an athletic skill — it declines with age rather than benefiting
        // from veteran IQ, so apply a one-sided age penalty (not experienceAdj).
        if (player.getYearsPro() > 14) value -= 2.5;
        else if (player.getYearsPro() > 12) value -= 2;
        else if (player.getYearsPro() > 10) value -= 1;

        return round(clamp(value));
    }
}
