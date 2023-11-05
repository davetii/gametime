package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class PostScoringSkillCalculator implements SkillCalculator {
    @Override
    public BigDecimal calc(Player player) {

        double value = ((player.getSize() * 2) + (player.getStrength() * 4) + (player.getShotSelection() * 3) + player.getIntelligence()  ) / 10d;

        if(player.getDetermination() > 8) { value += 1.5; }
        else if(player.getDetermination() > 7) { value += 1; }

        if(player.getSize() > 7) {
            if(player.getAgility() > 7) { value += 2.5; }
            else if(player.getAgility() > 6) { value += 1.5; }
        }

        if(player.getSize() > 6) {
            if(player.getDetermination() > 8) { value += 4; }
            if(player.getDetermination() > 7) { value += 2.5; }
            else if(player.getDetermination() > 6) { value += 1.5; }
        }


        if(player.getSize() > 5) {
            if(player.getShotSkill() > 8) { value += 4; }
            if(player.getShotSkill() > 7) { value += 2.5; }
            else if(player.getShotSkill() > 6) { value += 1.5; }
        }

        if(player.getSize() > 6) {
            if(player.getLuck() < 3) { value -= 2.5; }
            else if(player.getLuck() < 6) { value -= 1.5; }
            else if(player.getLuck() < 8) { value -= .5; }
        }

        if(player.getShotSkill() < 1) { value -= 4; }
        else if(player.getShotSkill() < 2) { value -= 3; }
        else if(player.getShotSkill() < 3) { value -= 2; }
        else if(player.getShotSkill() < 4) { value -= 1; }

        if(player.getDetermination() < 2) { value -= 3.5; }
        else if(player.getDetermination() < 3) { value -= 2; }
        else if(player.getDetermination() < 4) { value -= 1; }

        if(player.getSize() < 2) { value -= 3; }
        if(player.getSize() < 3) { value -= 2; }

        return round(value);
    }
}
