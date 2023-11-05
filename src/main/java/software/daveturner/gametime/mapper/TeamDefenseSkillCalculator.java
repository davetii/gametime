package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class TeamDefenseSkillCalculator implements SkillCalculator {
    @Override
    public BigDecimal calc(Player player) {

        double value = ((player.getIntelligence() * 2) + player.getStrength() + player.getSpeed() + (player.getCohesion()*2)) / 6d;

        if(player.getEgo() > 9) { value -= 2;}
        else if(player.getEgo() > 6) { value -= 1;}
        else if(player.getEgo() < 2) { value += 2;}
        else if(player.getEgo() < 4) { value += 1;}

        value = adjustValue(player.getCohesion(), value);
        value = adjustValue(player.getIntelligence(), value);
        value = adjustValue(player.getEnergy(), value);
        value = adjustValue(player.getStrength(), value);
        value = adjustValue(player.getSpeed(), value);

        return round(value);
    }

    double adjustValue(int attrib, double value) {
        if(attrib > 9) { value += 6;}
        else if (attrib > 8) { return value += 4;}
        else if (attrib > 7) { return value += 3;}
        else if (attrib > 6) { return value += 2;}
        else if (attrib > 5) { return value += 1.5;}
        else if (attrib < 1) { return value -= 6;}
        else if (attrib < 2) { return value -= 4;}
        else if (attrib < 3) { return value -= 2;}
        else if (attrib < 4) { return value -= 1.5;}
        else if (attrib < 5) { return value -= 1;}
        return value;
    }
}
