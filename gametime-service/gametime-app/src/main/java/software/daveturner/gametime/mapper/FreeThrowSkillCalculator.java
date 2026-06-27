package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * Free throw shooting. Mostly raw shot skill, with composure handling the
 * repetition/pressure element and experience providing a small refinement.
 */
@Component
public class FreeThrowSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getShotSkill() * 4) + player.getShotSelection()
                + player.getLuck() + player.getIntelligence()) / 7d;

        value += adj(player.getShotSkill());
        value += adj(player.getComposure(), COMBO_FACTOR);

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
