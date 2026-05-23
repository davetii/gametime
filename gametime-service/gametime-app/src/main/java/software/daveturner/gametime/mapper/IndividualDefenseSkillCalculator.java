package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;

@Component
public class IndividualDefenseSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        double value = ((player.getAgility() * 3) + (player.getDetermination() * 3) + (player.getIntelligence() * 2) +
                player.getEgo() + (player.getEndurance() * 2) + player.getHandle() + player.getLuck()) / 13d;

        value += maybeAddIndividualDefense(player);
        value -= maybeLowerIndividualDefense(player);
        if(player.getYearsPro() > 12) { value -= 2.5d; }
        else if ( player.getYearsPro() > 9) { value -= 2d; }
        else if ( player.getYearsPro() > 6) { value -= 1d; }
        return round(value);
    }

    private double maybeAddIndividualDefense(Player a) {
        double result = 0d;
        int speedStrength = a.getStrength() + a.getSpeed();
        int strengthSize = a.getStrength() + a.getSize();

        if (speedStrength > 18) {
            result += 6;
        } else if (speedStrength > 17) {
            result += 4.5;
        } else if (speedStrength > 16) {
            result += 3.5;
        } else if (speedStrength > 15) {
            result += 2;
        } else if (speedStrength > 14) {
            result += 1;
        } else if (strengthSize > 18) {
            result += 3.5;
        } else if (strengthSize > 17) {
            result += 2.5;
        } else if (strengthSize > 16) {
            result += 1.5;
        } else if (strengthSize > 15) {
            result += 1;
        } else if (strengthSize > 14) {
            result += .5;
        } else if (a.getSpeed() > 9) {
            result += 3;
        } else if (a.getSpeed() > 8) {
            result += 2;
        } else if (a.getSpeed() > 7) {
            result += 2;
        }
        return result;
    }

    protected double maybeLowerIndividualDefense(Player a) {
        double result = 0d;
        int speedStrength = a.getStrength() + a.getSpeed();

        if (speedStrength < 2) {
            result += 5;
        } else if (speedStrength < 3) {
            result += 4;
        } else if (speedStrength < 4) {
            result += 3;
        } else if (speedStrength < 5) {
            result += 2;
        } else if (speedStrength < 6) {
            result += 1;
        }

        if (a.getSize() > 7) {
            if(a.getStrength() < 4) { result += 5; }
            else if(a.getStrength() < 5) { result += 4; }
            else if(a.getStrength() < 6) { result += 3; }
            else if(a.getStrength() < 7) { result += 2; }
        }

        if (a.getSize() < 4) {
            if(a.getSpeed() < 2) { result += 5; }
            else if(a.getSpeed() < 3) { result += 4; }
            else if(a.getSpeed() < 4) { result += 3; }
            else if(a.getSpeed() < 5) { result += 2; }
        }
        return result;
    }


}
