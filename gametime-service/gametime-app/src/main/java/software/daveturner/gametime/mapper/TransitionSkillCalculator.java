package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Fast-break play — scoring, decision-making, and execution in the open court.
 * Speed and motor (energy) carry it; awareness reads the break, handle pushes the
 * ball. Big men lag in transition.
 */
@Component
public class TransitionSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getSpeed() * 3) + (player.getEnergy() * 2)
                + (player.getAwareness() * 2) + player.getHandle()) / 8d;

        value += adj(player.getSpeed());
        value += adj(player.getAwareness(), COMBO_FACTOR);
        value += adj(player.getHandle(), COMBO_FACTOR);

        // Bigs lag in the open court.
        value -= adj(player.getSize(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
