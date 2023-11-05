package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class FreeThrowSkillCalculator implements SkillCalculator{
    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getShotSkill() * 4) + player.getShotSelection() + player.getLuck() + player.getIntelligence()) / 7d;
        if (player.getYearsPro() > 11) { value += 3; }
        else if (player.getYearsPro() > 9) { value += 2.5; }
        else if (player.getYearsPro() > 7) { value += 2; }
        else if (player.getYearsPro() > 6) { value += 1.5; }
        else if (player.getYearsPro() > 5) { value += 1; }
        return round(value);
    }
}
