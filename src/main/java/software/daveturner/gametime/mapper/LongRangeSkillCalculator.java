package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class LongRangeSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getShotSkill() * 3) +
                (player.getShotSelection() * 4) +
                (player.getLuck() * 2) +
                player.getIntelligence() ) / 10d;

        if(player.getShotSelection() > 9) { value += 6; }
        else if(player.getShotSelection() > 8) { value += 4; }
        else if(player.getShotSelection() > 7) { value += 3; }
        else if(player.getShotSelection() > 6) { value += 2.5; }
        else if(player.getShotSelection() > 5) { value += 1.5; }


        if(player.getShotSelection() < 1) { value -= 5; }
        else if(player.getShotSelection() < 2) { value -= 4; }
        else if(player.getShotSelection() < 3) { value -= 3; }
        else if(player.getShotSelection() < 4) { value -= 1.5; }

        if(player.getShotSkill() > 9) { value += 5; }
        else if(player.getShotSkill() > 8) { value += 4; }
        else if(player.getShotSkill() > 7) { value += 3; }
        else if(player.getShotSkill() > 6) { value += 2; }

        if(player.getShotSkill() < 1) { value -= 4; }
        else if(player.getShotSkill() < 2) { value -= 3; }
        else if(player.getShotSkill() < 3) { value -= 2; }
        else if(player.getShotSkill() < 4) { value -= 1.5; }
        else if(player.getShotSkill() < 5) { value -= .5; }

        if(player.getSize() > 9) { value -= 2.5; }
        else if(player.getSize() > 8) { value -= 2; }
        else if(player.getSize() > 7) { value -= 1.5; }
        else if(player.getSize() > 6) { value -= 1; }
        return round(value);
    }
}
