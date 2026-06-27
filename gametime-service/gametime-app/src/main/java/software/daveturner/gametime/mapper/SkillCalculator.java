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

    /** Bound a raw skill value to the 1–20 scale. */
    default double clamp(double value) {
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, value));
    }

    default BigDecimal round(double d) {
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd;
    }
}
