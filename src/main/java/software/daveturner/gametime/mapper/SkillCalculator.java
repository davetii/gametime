package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public interface SkillCalculator {

    BigDecimal calc(Player player);

    default BigDecimal round(double d) {
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd;
    }
}
