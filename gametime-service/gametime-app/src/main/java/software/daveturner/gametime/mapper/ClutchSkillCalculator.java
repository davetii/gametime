package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Performance under pressure — late game, playoffs, close-score situations.
 * Composure carries it; ego wants the big moment, determination and shot skill
 * deliver it. Rookies wilt; veterans steady.
 */
@Component
public class ClutchSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getComposure() * 3) + (player.getDetermination() * 2)
                + (player.getShotSkill() * 2) + player.getIntelligence()) / 8d;

        value += adj(player.getComposure());
        value += adj(player.getEgo(), COMBO_FACTOR);
        value += adj(player.getDetermination(), COMBO_FACTOR);

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
