package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Tendency to commit fouls. HIGHER = WORSE (more fouls committed).
 *
 * Unlike other skills there is no "average of attributes" that means average foul
 * rate, so the baseline is the scale average (10). Aggression and reckless energy
 * push it up; composure, awareness and experience pull it down. The result is still
 * centered at 10 for an average player, consistent with every other skill.
 */
@Component
public class FoulProneSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = SCALE_AVG;

        // More fouls: aggressive, high-motor players reach and bang.
        value += adj(player.getAggression());
        value += adj(player.getEnergy(), COMBO_FACTOR);

        // Fewer fouls: steadiness and anticipation keep a player out of trouble.
        value -= adj(player.getComposure());
        value -= adj(player.getAwareness(), COMBO_FACTOR);

        // Veterans learn to defend without fouling.
        value -= experienceAdj(player.getYearsPro());

        return round(clamp(value));
    }
}
