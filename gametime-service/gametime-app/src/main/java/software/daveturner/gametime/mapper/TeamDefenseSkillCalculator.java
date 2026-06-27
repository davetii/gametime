package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Help defense, rotations, communication, team schemes. Intelligence and cohesion
 * drive it; ego is a double-edged penalty (selfish defenders break the scheme).
 */
@Component
public class TeamDefenseSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getIntelligence() * 2) + player.getStrength()
                + player.getSpeed() + (player.getCohesion() * 2)) / 6d;

        value += adj(player.getCohesion());
        value += adj(player.getIntelligence());
        value += adj(player.getAwareness(), COMBO_FACTOR);

        // Selfish players hurt team defense; unselfish ones help.
        value -= adj(player.getEgo(), COMBO_FACTOR);

        return round(clamp(value));
    }
}
