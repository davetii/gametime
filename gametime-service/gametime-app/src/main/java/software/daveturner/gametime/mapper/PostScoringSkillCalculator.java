package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Scoring in the low post — back-to-basket moves, hooks, turnarounds. Strength and
 * size carry it, gated by combinations: a strong-willed big and a skilled big both
 * score well, while small players struggle.
 */
@Component
public class PostScoringSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getSize() * 2) + (player.getStrength() * 4)
                + (player.getShotSelection() * 3) + player.getIntelligence()) / 10d;

        value += adj(player.getStrength());

        // Dominant will: a big who also brings determination.
        value += comboAdj(player.getSize(), player.getDetermination());
        // Skilled big: size paired with touch.
        value += comboAdj(player.getSize(), player.getShotSkill());

        // Small players can't operate in the post.
        value -= adj(player.getSize());

        return round(clamp(value));
    }
}
