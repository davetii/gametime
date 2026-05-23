package software.daveturner.gametime.mapper;

import org.springframework.stereotype.*;
import software.daveturner.gametime.model.*;

import java.math.*;

@Component
public class AcumenSkillCalculator implements SkillCalculator{

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getIntelligence() * 2) + player.getHandle() + player.getLuck() + (player.getCohesion()*2)) / 6d;
        if(player.getAgility() > 8) { value++;}
        if(player.getSpeed() > 8) { value++;}

        if(player.getEgo() > 9) { value -= 3;}
        else if(player.getEgo() > 8) { value -= 2;}
        else if(player.getEgo() > 7) { value --;}
        value = repeatAdjustment(player.getHandle(), value);
        value = repeatAdjustment(player.getIntelligence(), value);
        value = repeatAdjustment(player.getLuck(), value);
        value = repeatAdjustment(player.getCohesion(), value);

        if(player.getShotSelection() > 10) { value += 3;}
        else if(player.getShotSelection() > 9) { value += 2;}
        else if(player.getShotSelection() > 8) { value += 1;}

        if(player.getYearsPro() > 11) { value += 4;}
        else if(player.getYearsPro() > 10) { value += 3.5d;}
        else if(player.getYearsPro() > 9) { value += 3;}
        else if(player.getYearsPro() > 8) { value += 2.5d;}
        else if(player.getYearsPro() > 7) { value += 2;}
        else if(player.getYearsPro() > 5) { value += 1;}

        if(player.getYearsPro() == 1) { value -= 2;}
        else if(player.getYearsPro() == 2) { value -= 1;}
        return round(value);
    }

    protected double repeatAdjustment(Integer i, double d) {
        if(i > 10) { return d += 5; }
        if(i > 9) { return d += 3; }
        if(i > 8) { return d += 2; }
        if(i > 7) { return d += 1; }
        if(i < 1) { return d -= 4; }
        if(i < 2) { return d -= 3; }
        if(i < 3) { return d -= 2; }
        if(i < 4) { return d -= 1; }
        return d;
    }
}
