package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

/**
 * 1-on-1 defense — staying in front, contesting, forcing tough shots.
 *
 * Base is a weighted average of the attributes that define on-ball defense.
 * On the 1–20 scale this base already yields ~10 for an average player, so the
 * adjustments below use the shared deviation helpers (0 at average, scaling toward
 * the 1–20 bounds) rather than the old threshold ladders.
 */
@Component
public class IndividualDefenseSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAgility() * 3) + (player.getDetermination() * 3)
                + (player.getIntelligence() * 2) + player.getEgo()
                + (player.getEndurance() * 2) + player.getHandle()
                + player.getLuck() + (player.getHealth() * 2)) / 15d;

        // Active-defense emphasis: lateral quickness, effort, reading the ball.
        value += adj(player.getAgility());
        value += adj(player.getDetermination());
        value += adj(player.getAwareness());

        // Athletic stopper: speed+strength combo lets a defender handle more matchups.
        value += comboAdj(player.getSpeed(), player.getStrength());

        // Length helps contest without fouling.
        value += adj(player.getWingspan(), COMBO_FACTOR);

        value += experienceAdj(player.getYearsPro());
        return round(clamp(value));
    }
}
