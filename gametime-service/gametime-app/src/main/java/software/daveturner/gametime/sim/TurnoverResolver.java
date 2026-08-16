package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class TurnoverResolver {

    private final SimConfig config;

    public TurnoverResolver(SimConfig config) {
        this.config = config;
    }

    public boolean isTurnover(PlayerGameState ballHandler,
                              List<PlayerGameState> defenders, RandomGenerator rng) {
        return isTurnover(ballHandler, defenders, 1.0, rng);
    }

    /**
     * §3.4: {@code defensivePressure} (the defending coach's defensiveScheme
     * modifier) scales the turnover rate — a more aggressive, gambling defense
     * forces more turnovers (1.0 = neutral).
     */
    public boolean isTurnover(PlayerGameState ballHandler, List<PlayerGameState> defenders,
                              double defensivePressure, RandomGenerator rng) {
        // §3.5: fatigue scales each contestant's skill — tired defenders force
        // fewer turnovers, a tired ball handler protects the ball worse.
        double avgDefense = defenders.stream()
                .mapToDouble(d -> (d.getStealing() + d.getIndividualDefense()) / 2.0
                        * d.fatigueFactor())
                .average()
                .orElse(SimConfig.SCALE_AVG);
        double ballSecurity = ballHandler.getBallSecurity() * ballHandler.fatigueFactor();
        double prob = config.clampProbability(defensivePressure * config.contestProbability(
                config.baseTurnover(), avgDefense, ballSecurity));
        return rng.nextDouble() < prob;
    }

    /**
     * §3.9 (decisions.md #027 A/C): given a turnover has ALREADY been declared by
     * {@link #isTurnover}, pick which of the nine {@link TurnoverCause} values it
     * was — a single weighted categorical draw that replaces the pre-§3.9
     * {@code isStolen} binary. This runs ONLY after the (unchanged) gate fires, so
     * it re-partitions the existing turnovers: it can shift the mix but never the
     * count (Decision A).
     *
     * <p>Each cause's raw weight is its {@code SimConfig.TO_WEIGHT_*} tier base,
     * multiplied by a modest avg-10 lean on the four scaled causes (Decision C):
     * {@code SHOT_CLOCK_VIOLATION} rises as the ball-handler's {@code acumen} falls
     * and as the defending coach's {@code defensivePressure} (the {@code
     * defensiveScheme} multiplier, already computed per-possession) rises;
     * {@code OFFENSIVE_FOUL} / {@code BAD_PASS} rise as the offense's
     * {@code teamOffense} falls. The weights are normalized to sum to 1.0 on THIS
     * turnover (the cumulative-sum walk {@code pickStealer}/{@code pickShooter}
     * already use), so the leans move only the relative shares — no lean can change
     * the turnover total (Decision C).
     *
     * @param ballHandler      the on-ball player who lost it (charged the turnover)
     * @param teamOffense      the offense's average {@code teamOffense} this
     *                         possession (leans {@code OFFENSIVE_FOUL}/{@code BAD_PASS})
     * @param defensivePressure the defending coach's {@code defensiveScheme}
     *                         multiplier (leans {@code SHOT_CLOCK_VIOLATION})
     */
    public TurnoverCause pickCause(PlayerGameState ballHandler, double teamOffense,
                                   double defensivePressure, RandomGenerator rng) {
        TurnoverCause[] causes = TurnoverCause.values();
        double[] weights = new double[causes.length];
        double total = 0;
        for (int i = 0; i < causes.length; i++) {
            double w = causeWeight(causes[i], ballHandler.getAcumen(),
                    teamOffense, defensivePressure);
            weights[i] = w;
            total += w;
        }
        double roll = rng.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < causes.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return causes[i];
        }
        return causes[causes.length - 1];
    }

    /**
     * The raw (pre-normalization) weight of one cause: its {@code SimConfig}
     * tier base, times a modest avg-10 lean on the four scaled causes (Decision C).
     * The lean form is {@code 1 + TO_CAUSE_SENSITIVITY × deviation/10}, clamped ≥ a
     * small floor so a lean can shrink but never zero-out a cause. All non-leaned
     * causes return their flat tier base.
     */
    private double causeWeight(TurnoverCause cause, double acumen,
                               double teamOffense, double defensivePressure) {
        return switch (cause) {
            case STOLEN -> config.toWeightStolen();
            // ↑ as ball-handler acumen falls below 10 AND as the defending scheme
            // pressures more (defensivePressure > 1.0 for an aggressive scheme).
            case SHOT_CLOCK_VIOLATION -> config.toWeightShotClock()
                    * config.turnoverCauseLean(SimConfig.SCALE_AVG - acumen)
                    * defensivePressure;
            // ↑ as the offense's teamOffense falls below 10 (a weaker, less
            // coordinated offense charges / throws it away more).
            case OFFENSIVE_FOUL -> config.toWeightOffensiveFoul()
                    * config.turnoverCauseLean(SimConfig.SCALE_AVG - teamOffense);
            case BAD_PASS -> config.toWeightBadPass()
                    * config.turnoverCauseLean(SimConfig.SCALE_AVG - teamOffense);
            case TRAVELLING -> config.toWeightTravelling();
            case LOST_BALL_OUT_OF_BOUNDS -> config.toWeightLostBallOob();
            case THREE_SECONDS_VIOLATION -> config.toWeightThreeSeconds();
            case EIGHT_SECONDS_BACKCOURT_VIOLATION -> config.toWeightEightSecondsBackcourt();
            case OVER_AND_BACK -> config.toWeightOverAndBack();
        };
    }

    public PlayerGameState pickStealer(List<PlayerGameState> defenders, RandomGenerator rng) {
        double totalWeight = defenders.stream()
                .mapToDouble(PlayerGameState::getStealing)
                .sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState d : defenders) {
            cumulative += d.getStealing();
            if (roll < cumulative) return d;
        }
        return defenders.get(defenders.size() - 1);
    }
}
