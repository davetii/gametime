package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class PassingSkillCalculator implements SkillCalculator {
    @Override
    public BigDecimal calc(Player player) {
        //handle, intelligence, luck, ego, shot select, speed
        double value = ((player.getIntelligence() * 2) + (player.getHandle() * 3) + player.getLuck()) / 6d;

        value = adjust(player.getHandle(), value);
        value = adjust(player.getIntelligence(), value);

        if(player.getYearsPro() > 14) { value += 4;}
        else if(player.getYearsPro() > 12) { value += 3;}
        else if(player.getYearsPro() > 10) { value += 2.5d;}
        else if(player.getYearsPro() > 9) { value += 2;}
        else if(player.getYearsPro() > 8) { value += 1;}

        return round(value);
    }

    private double adjust(int attrib,  double value) {
        if(attrib > 9) { return value += 5;}
        else if(attrib > 8) { return value += 4;}
        else if(attrib > 7) { return value += 3;}
        else if(attrib > 6) { return value += 2;}
        else if(attrib > 5) { return value += 1.5;}
        else if(attrib < 1) { return value -= 4;}
        else if(attrib < 2) { return value -= 3;}
        else if(attrib < 3) { return value -= 2;}
        else if(attrib < 4) { return value -= 1;}
        return value;
    }
}
