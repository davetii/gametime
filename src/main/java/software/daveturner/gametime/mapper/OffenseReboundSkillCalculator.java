package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class OffenseReboundSkillCalculator implements SkillCalculator{

    @Override
    public BigDecimal calc(Player player) {
        double value =
                (player.getEgo() + (player.getDetermination() * 3) +
                        (player.getEnergy() * 2) + (player.getIntelligence() * 2) +
                        (player.getSize() * 2) + (player.getStrength() * 2)) / 12d;

        value = adjustValueUp(player.getDetermination(), value);
        value = adjustValueUp(player.getAgility(), value);
        value = adjustValueUp(player.getSize(), value);

        if(player.getSpeed() > 8) { value += 2;}
        else if(player.getSpeed() > 6) { value +=1; }

        if(player.getEgo() > 9) { value += 3;}
        else if(player.getEgo() > 7) { value +=1; }

        if(player.getEgo() < 2) { value -= 3;}
        else if(player.getEgo() < 3) { value -=1.5; }
        value = adjustValueDown(player.getEndurance(), value);
        value = adjustValueDown(player.getSize(), value);
        value = adjustValueDown(player.getDetermination(), value);
        return round(value);
    }

    private double adjustValueUp(int attrib,  double value) {
        if(attrib > 9) { value += 3.5;}
        else if(attrib > 8) { value += 2.5;}
        else if(attrib > 7) { value +=1.5; }
        else if(attrib > 6) { value +=1; }
        return value;
    }

    private double adjustValueDown(int attrib,  double value) {
        if(attrib < 1) { value -= 4;}
        else if(attrib < 2) { value -=3.5; }
        else if(attrib < 3) { value -=2; }
        else if(attrib < 4) { value -=1; }
        return value;
    }
}
