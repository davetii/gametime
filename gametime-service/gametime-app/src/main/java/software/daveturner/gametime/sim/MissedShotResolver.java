package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * §3.8 (decisions.md #026): resolves "a shot missed, now what?" as a single four-way
 * outcome — offensive rebound / defensive rebound / OOB-offense / OOB-defense
 * (Decision A). <b>Wraps, does not replace, {@link ReboundResolver}</b> (Decision B):
 * the skill-weighted board contest stays there; this applies the OOB carve on top.
 *
 * <p><b>⚠ CARVE ORDER IS THE CRUX (Decision A).</b> OOB is split off FIRST, by flat
 * skill-INDEPENDENT defense-leaning weights, and the skill contest runs ONLY on the
 * clean-rebound remainder. Deliberately NOT "run the board contest, then flip some
 * results to OOB" — that would make OOB inherit the board winner, so a dominant
 * offensive rebounder would skew the OOB split too (#026 A rejects it).
 *
 * <p><b>OOB credits no rebounder</b> (Decision E): {@link Result#rebounder()} is null on
 * the OOB outcomes, keeping them out of the rebound reconciliation invariant.
 *
 * <p><b>Cap:</b> {@code capReached} forces an ending outcome — {@code OFFENSIVE_REBOUND}
 * becomes {@code DEFENSIVE_REBOUND}, {@code OOB_OFFENSE} becomes {@code OOB_DEFENSE}.
 * ⚠ The outcome <i>family</i> is preserved: an OOB stays an OOB event, a rebound stays a
 * rebound event. The loop still owns {@code continue} vs. {@code return}.
 */
@Component
public class MissedShotResolver {

    private final ReboundResolver reboundResolver;
    private final SimConfig config;

    public MissedShotResolver(ReboundResolver reboundResolver, SimConfig config) {
        this.reboundResolver = reboundResolver;
        this.config = config;
    }

    /**
     * The four-way outcome plus the rebounder to credit. {@code rebounder} is
     * non-null only when {@code outcome.creditsRebounder()} (the two rebound
     * outcomes); it is {@code null} on the two OOB outcomes (Decision E).
     */
    public record Result(MissedShotOutcome outcome, PlayerGameState rebounder) {
    }

    /**
     * Resolve the missed shot. When {@code capReached}, no offense-retained outcome
     * is returned (the second-chance loop is bounded by
     * {@link SimConfig#maxOffensiveRetentionsPerPossession()}).
     *
     * @param offense     the on-floor offensive five (rebounder pool)
     * @param defense     the on-floor defensive five (rebounder pool)
     * @param capReached  true once the offensive-rebound cap is hit this possession
     */
    public Result resolve(List<PlayerGameState> offense, List<PlayerGameState> defense,
                          boolean capReached, RandomGenerator rng) {
        return resolve(offense, defense, capReached, config.baseOffensiveRebound(), rng);
    }

    /**
     * §3.21 (decisions.md #043 C/D): the same resolution at a CALLER-SUPPLIED contest
     * base, for the free-throw board — where the defense has inside position by rule and
     * the base is {@code baseOffensiveRebound() × }{@link
     * SimConfig#FREE_THROW_REBOUND_LEAN}.
     *
     * <p><b>Reused WHOLE and deliberately so</b> (#043 C): the OOB carve, cap forcing and
     * rebounder selection all come for free. ⚠ <b>The OOB slices come along, and that is
     * right</b> — ~7% of free-throw rebounds resolve {@code OUT_OF_BOUNDS_*}; a missed FT
     * going out of bounds is a real outcome, not a defect of the reuse.
     *
     * <p>⚠ <b>Only the rebound CONTEST reads the supplied base.</b> The OOB carve stays
     * flat and skill-independent — a free throw does not sail out of bounds more or less
     * often because of where the players stand.
     */
    public Result resolve(List<PlayerGameState> offense, List<PlayerGameState> defense,
                          boolean capReached, double offensiveReboundBase,
                          RandomGenerator rng) {
        // 1. Carve OOB off the top FIRST — a flat, skill-independent share of misses
        //    leave the court. Skill plays NO part in whether the ball goes OOB.
        if (rng.nextDouble() < config.oobTotalWeight()) {
            // 2a. Split the OOB share defense/offense by the flat, defense-leaning
            //     weights (also skill-independent). Cap forces OOB_DEFENSE.
            double oobTotal = config.oobDefenseWeight() + config.oobOffenseWeight();
            boolean oobOffense = !capReached
                    && rng.nextDouble() * oobTotal >= config.oobDefenseWeight();
            MissedShotOutcome outcome = oobOffense
                    ? MissedShotOutcome.OOB_OFFENSE
                    : MissedShotOutcome.OOB_DEFENSE;
            return new Result(outcome, null); // OOB credits no rebounder (E)
        }

        // 2b. Clean rebound remainder — the ONLY branch that runs the skill contest.
        PlayerGameState offRebounder = reboundResolver.pickOffensiveRebounder(offense, rng);
        PlayerGameState defRebounder = reboundResolver.pickDefensiveRebounder(defense, rng);
        boolean offensiveRebound = !capReached
                && reboundResolver.isOffensiveRebound(offRebounder, defRebounder,
                        offensiveReboundBase, rng);
        if (offensiveRebound) {
            return new Result(MissedShotOutcome.OFFENSIVE_REBOUND, offRebounder);
        }
        return new Result(MissedShotOutcome.DEFENSIVE_REBOUND, defRebounder);
    }
}
