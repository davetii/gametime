package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class TeamOffenseSkillCalculator implements SkillCalculator {
    @Override
    public BigDecimal calc(Player player) {

        /*
        ego
        energy
        intel
        cohesion
        shot select
        shot skill
        determination
        handle
         */
        double value = (
                (player.getIntelligence() * 2) +
                        player.getHandle() +
                        player.getEnergy() +
                        player.getShotSkill() +
                        player.getShotSelection() +
                        player.getDetermination() +
                        (player.getCohesion()*2)) / 9d;

        if(player.getEgo() > 9) { value -= 3;}
        else if(player.getEgo() > 8) { value -= 2;}
        else if(player.getEgo() > 7) { value -= 1;}

        if(player.getIntelligence() > 9) { value += 2; }
        else if(player.getIntelligence() > 8) { value += 1.5; }
        else if(player.getIntelligence() > 7) { value += 1; }

        return round(value);
    }
}
