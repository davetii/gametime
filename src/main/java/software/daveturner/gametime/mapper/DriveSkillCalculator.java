package software.daveturner.gametime.mapper;


import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class DriveSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAgility() * 3) + player.getDetermination() + (player.getHandle() * 2) + player.getSpeed()) / 7d;
        value = calc(player.getEgo(), value);
        value = calc(player.getShotSkill(), value);
        value = calc(player.getSpeed(), value);
        value = calc(player.getStrength(), value);

        if(player.getSize() > 9) { value -= 3;}
        else if(player.getSize()  > 7) { value -= 2;}
        else if(player.getSize()  > 6) { value -= 1;}

        if(player.getEnergy() < 1) { value -= 4;}
        else if(player.getEnergy()  < 3) { value -= 3;}
        else if(player.getEnergy()  < 4) { value -= 1;}

        if(player.getYearsPro() > 14) { value -= 4; }
        else if(player.getYearsPro() > 12) { value -= 3; }
        else if(player.getYearsPro() > 10) { value -= 2; }
        else if(player.getYearsPro() > 8) { value -= 1; }

        return round(value);
    }

    private double calc(int a, double currentVal) {
        if(a > 9) { currentVal += 3;}
        else if(a > 7) { currentVal += 2;}
        else if(a > 6) { currentVal += 1;}
        else if(a < 2) { currentVal -= 3;}
        else if(a < 3) { currentVal -= 2;}
        else if(a < 4) { currentVal -= 1;}
        return currentVal;
    }
}
