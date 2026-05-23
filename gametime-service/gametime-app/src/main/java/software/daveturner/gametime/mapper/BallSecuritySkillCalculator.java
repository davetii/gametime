package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class BallSecuritySkillCalculator implements SkillCalculator{

    @Override
    public BigDecimal calc(Player player) {
        // desire, ego, endurance, energy, handle, intelligence, luck
        // desire, handle, intelligence, luck
        double value = ((player.getDetermination() * 2) + (player.getHandle() * 4) + (player.getIntelligence() * 3) + player.getLuck()) / 10d;

        if(player.getEnergy() > 9) { value -= 2; }
        else if(player.getEnergy() > 8) { value -= 1; }

        if(player.getEgo() > 9) { value -= 2; }
        else if(player.getEgo() > 8) { value -= 1; }

        if(player.getEndurance() > 9) {value += 3; }
        else if(player.getEndurance() > 8) {value += 2; }
        else if(player.getEndurance() > 7) {value += 1; }

        return round(value);
    }
}
