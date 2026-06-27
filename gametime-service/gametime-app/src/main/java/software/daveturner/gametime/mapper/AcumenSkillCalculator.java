package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * In-game basketball IQ — reading plays, smart real-time decisions. Driven by
 * intelligence and cohesion; ego is a double-edged penalty and experience helps.
 */
@Component
public class AcumenSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getIntelligence() * 2) + player.getHandle()
                + player.getLuck() + (player.getCohesion() * 2)) / 6d;

        value += adj(player.getIntelligence());
        value += adj(player.getCohesion());
        value += adj(player.getAwareness());
        value += adj(player.getShotSelection(), COMBO_FACTOR);

        // Ego works against in-game IQ (forcing plays, ignoring the read).
        value -= adj(player.getEgo(), COMBO_FACTOR);

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
