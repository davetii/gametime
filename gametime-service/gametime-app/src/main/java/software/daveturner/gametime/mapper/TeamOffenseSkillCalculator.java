package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Playing within a system — ball movement, spacing, unselfish play. Intelligence
 * and cohesion drive it; ego is a double-edged penalty.
 */
@Component
public class TeamOffenseSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getIntelligence() * 2) + player.getHandle()
                + player.getEnergy() + player.getShotSkill()
                + player.getShotSelection() + player.getDetermination()
                + (player.getCohesion() * 2)) / 9d;

        value += adj(player.getCohesion());
        value += adj(player.getIntelligence());

        // Ball-stoppers hurt team offense.
        value -= adj(player.getEgo(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
