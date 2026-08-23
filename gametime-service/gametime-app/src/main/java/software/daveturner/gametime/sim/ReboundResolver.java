package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Resolves who collects a missed shot (§3.3). Mirrors {@link FoulResolver}:
 * constructor-injected {@link SimConfig}, pure math, seeded RNG. The offensive
 * vs. defensive contest reuses the logistic-contest formula
 * ({@link SimConfig#contestProbability}); rebounder selection reuses the
 * skill-weighted draw pattern from {@link ShotSelector}.
 */
@Component
public class ReboundResolver {

    private final SimConfig config;

    public ReboundResolver(SimConfig config) {
        this.config = config;
    }

    /**
     * Contest the offensive rebounder's {@code offenseRebound} against the
     * defensive rebounder's {@code defenseRebound}. Two average players yield
     * {@link SimConfig#baseOffensiveRebound()}.
     */
    public boolean isOffensiveRebound(PlayerGameState offRebounder,
                                      PlayerGameState defRebounder,
                                      RandomGenerator rng) {
        return isOffensiveRebound(offRebounder, defRebounder,
                config.baseOffensiveRebound(), rng);
    }

    /**
     * §3.21 (decisions.md #043 D): the same contest at a CALLER-SUPPLIED base, for the
     * one site where the base is not the ordinary board's — the free-throw board, where
     * the defense has inside position by rule and the base is
     * {@code baseOffensiveRebound() × }{@link SimConfig#FREE_THROW_REBOUND_LEAN}.
     *
     * <p><b>Only the BASE moves; the contest itself is identical</b> — same logistic
     * form, same skills, same fatigue scaling. This is deliberately not a second
     * contest: a free-throw board is still {@code offenseRebound} vs.
     * {@code defenseRebound} between the same ten players, and the only thing the rule
     * changes is where they start from.
     *
     * <p>⚠ <b>The relationship between the base and the realized share is logistic, so
     * the base is NOT the share.</b> #043 D set the base once from the real ~0.19
     * offensive share and MEASURED what came out; do not back-solve it, and do not
     * iterate on it (the whole plausible spread is smaller than the residual it would
     * chase).
     */
    public boolean isOffensiveRebound(PlayerGameState offRebounder,
                                      PlayerGameState defRebounder,
                                      double base,
                                      RandomGenerator rng) {
        // §3.5: fatigue scales each rebounder's skill — a tired crasher and a tired
        // box-out man both work the glass worse. Full energy ⇒ ×1.0.
        double prob = config.contestProbability(
                base,
                offRebounder.getOffenseRebound() * offRebounder.fatigueFactor(),
                defRebounder.getDefenseRebound() * defRebounder.fatigueFactor());
        return rng.nextDouble() < prob;
    }

    /** Pick the offensive rebounder by {@code offenseRebound} skill weight. */
    public PlayerGameState pickOffensiveRebounder(List<PlayerGameState> offense,
                                                  RandomGenerator rng) {
        return pickWeighted(offense, rng, PlayerGameState::getOffenseRebound);
    }

    /** Pick the defensive rebounder by {@code defenseRebound} skill weight. */
    public PlayerGameState pickDefensiveRebounder(List<PlayerGameState> defense,
                                                  RandomGenerator rng) {
        return pickWeighted(defense, rng, PlayerGameState::getDefenseRebound);
    }

    private PlayerGameState pickWeighted(List<PlayerGameState> players,
                                         RandomGenerator rng,
                                         java.util.function.ToDoubleFunction<PlayerGameState> weight) {
        double totalWeight = players.stream().mapToDouble(weight).sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : players) {
            cumulative += weight.applyAsDouble(p);
            if (roll < cumulative) return p;
        }
        return players.get(players.size() - 1);
    }
}
