package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public interface SkillCalculator {

    /**
     * Attributes and skills live on a 1–20 scale where 10 is league average.
     * The helpers below are calibrated for that scale: an all-average (10) player
     * yields a skill of ~10, an all-elite player approaches 20, and a weak player
     * floors near 1.
     */
    double SCALE_MIN = 1d;
    double SCALE_MAX = 20d;
    double SCALE_AVG = 10d;

    /**
     * Soft-clamp knee. Skills at or below this are returned untouched (the whole
     * crowd + above-average band). Above it, the additive bonuses are compressed
     * so elite skills approach {@link #SCALE_MAX} asymptotically instead of all
     * piling up at a hard 20 — letting stars/superstars separate near the top.
     */
    double SOFT_KNEE = 14d;

    /** Default factor for a single-attribute emphasis bonus. */
    double SINGLE_FACTOR = 0.18d;
    /** Default factor for a two-attribute combination bonus. */
    double COMBO_FACTOR = 0.10d;

    BigDecimal calc(Player player);

    /**
     * Deviation-from-average adjustment for one attribute. Contributes 0 when the
     * attribute equals the league average (10), scales up toward +max as it rises
     * to 20, and down as it falls to 1. Replaces the old threshold ladders, which
     * were calibrated for a 1–10 scale and over-fire on 1–20 data.
     */
    default double adj(int attribute, double factor) {
        return (attribute - SCALE_AVG) * factor;
    }

    /** Single-attribute adjustment using the default emphasis factor. */
    default double adj(int attribute) {
        return adj(attribute, SINGLE_FACTOR);
    }

    /**
     * Deviation adjustment for a two-attribute combination (e.g. speed+strength).
     * Centered at 20 (two average attributes), so two average attributes contribute 0.
     */
    default double comboAdj(int a, int b, double factor) {
        return ((a + b) - (2 * SCALE_AVG)) * factor;
    }

    /** Two-attribute combination using the default combo factor. */
    default double comboAdj(int a, int b) {
        return comboAdj(a, b, COMBO_FACTOR);
    }

    /**
     * Experience curve on yearsPro, centered so a typical-tenure player is neutral.
     * Rookies are penalized, veterans rewarded, with diminishing returns at the top.
     */
    default double experienceAdj(int yearsPro) {
        if (yearsPro <= 1) return -1.5d;
        if (yearsPro == 2) return -1.0d;
        if (yearsPro <= 4) return 0d;
        if (yearsPro <= 6) return 0.5d;
        if (yearsPro <= 9) return 1.0d;
        if (yearsPro <= 12) return 1.5d;
        return 1.0d; // very old: tenure helps IQ but age starts to bite
    }

    /**
     * Bound a raw skill to the 1–20 scale. The floor is hard; the ceiling is
     * <em>soft</em>: values at or below {@link #SOFT_KNEE} pass through unchanged
     * (so average and above-average players are untouched), while values above
     * the knee are compressed via exponential saturation that approaches — but
     * never reaches — {@link #SCALE_MAX}. This replaces the old hard cut at 20,
     * which made every elite skill pile up at exactly 20.0 with no separation.
     */
    default double clamp(double value) {
        if (value <= SOFT_KNEE) {
            return Math.max(SCALE_MIN, value);
        }
        double room = SCALE_MAX - SOFT_KNEE;          // headroom above the knee (e.g. 6)
        double over = value - SOFT_KNEE;              // how far the raw value overshoots
        // 1 - e^(-over/room): 0 at the knee, asymptotically 1 as over -> ∞.
        return SOFT_KNEE + room * (1d - Math.exp(-over / room));
    }

    default BigDecimal round(double d) {
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd;
    }
}
