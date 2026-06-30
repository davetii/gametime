package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

@Component
public class SimConfig {

    // --- Possession count (Decision B) ---
    public static final int DEFAULT_POSSESSIONS_PER_PERIOD = 25;
    public static final int PERIODS = 4;
    public static final int OT_POSSESSIONS_PER_PERIOD = 5;

    // --- Probability sensitivity (Decision C) ---
    public static final double SENSITIVITY = 0.5;
    public static final double PROB_FLOOR = 0.02;
    public static final double PROB_CEILING = 0.97;
    public static final double SCALE_AVG = 10.0;

    // --- Shot base rates ---
    public static final double BASE_DRIVE = 0.60;
    public static final double BASE_PERIMETER = 0.42;
    public static final double BASE_THREE = 0.36;
    public static final double BASE_POST = 0.48;

    // --- Turnover base rate (per possession) ---
    public static final double BASE_TURNOVER = 0.13;

    // --- Foul base rate (on drive/post attempts) ---
    public static final double BASE_FOUL = 0.15;

    // --- Free throw ---
    public static final double FT_BASE = 0.75;
    public static final double FT_SENSITIVITY = 0.20;
    public static final int FREE_THROWS_PER_FOUL = 2;

    public double clampProbability(double p) {
        return Math.max(PROB_FLOOR, Math.min(PROB_CEILING, p));
    }

    public double contestProbability(double base, double offenseSkill, double defenseSkill) {
        double p = base + SENSITIVITY * (offenseSkill - defenseSkill) / SCALE_AVG;
        return clampProbability(p);
    }

    public double freeThrowProbability(double freeThrowSkill) {
        double p = FT_BASE + (freeThrowSkill - SCALE_AVG) / SCALE_AVG * FT_SENSITIVITY;
        return clampProbability(p);
    }
}
