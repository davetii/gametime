package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Cutting, spacing, finding open spots, relocating without the ball. Awareness and
 * IQ read the floor; speed gets open; cohesion plays within the system. Ego
 * (ball-watching, standing still) works against it.
 */
@Component
public class OffBallMovementSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAwareness() * 3) + (player.getSpeed() * 2)
                + (player.getIntelligence() * 2) + player.getCohesion()) / 8d;

        value += adj(player.getAwareness());
        value += adj(player.getIntelligence(), COMBO_FACTOR);
        value += adj(player.getSpeed(), COMBO_FACTOR);
        value += adj(player.getCohesion(), COMBO_FACTOR);

        // Ball-watchers stand still.
        value -= adj(player.getEgo(), COMBO_FACTOR);

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
