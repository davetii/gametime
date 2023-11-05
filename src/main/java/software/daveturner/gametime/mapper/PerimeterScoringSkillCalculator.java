package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;
import java.math.BigDecimal;

@Component
public class PerimeterScoringSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getShotSkill() * 4) +
                (player.getShotSelection() * 2) +
                player.getLuck() +
                player.getIntelligence() +
                player.getAgility() +
                player.getSpeed()) / 10d;
        value= adjust(player.getShotSkill(), value);

        if(player.getShotSelection() > 9) { value += 4; }
        else if(player.getShotSelection() > 8) { value += 3; }
        else if(player.getShotSelection() > 7) { value += 2; }

        if(player.getSize() > 9) { value -= 2; }
        else if(player.getSize() > 8) { value -= 1.5; }
        else if(player.getSize() > 7) { value -= 1; }

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
        else if(attrib < 3) { return value -= 2.5;}
        else if(attrib < 4) { return value -= 1.5;}
        return value;
    }
}
