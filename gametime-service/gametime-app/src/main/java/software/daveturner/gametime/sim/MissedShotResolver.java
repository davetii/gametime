package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * §3.8 (decisions.md #026): resolves "a shot missed, now what?" as a single
 * four-way outcome — offensive rebound / defensive rebound / OOB-offense /
 * OOB-defense (Decision A). Owns the full missed-shot decision and forks the
 * possession; {@link PossessionEngine}'s old {@code // 4. Rebound} block shrinks
 * to one call here.
 *
 * <p><b>Wraps, does not replace, {@link ReboundResolver}</b> (Decision B): the
 * skill-weighted two-way board contest + rebounder selection stays in
 * {@code ReboundResolver} (unchanged, still crediting a rebounder on its two
 * outcomes); this resolver applies the OOB lean on top.
 *
 * <p><b>Carve order (Decision A — the crux):</b> split off OOB vs. clean-rebound
 * <i>FIRST</i>, by the flat, skill-INDEPENDENT, defense-leaning {@code SimConfig}
 * OOB weights; run {@code ReboundResolver}'s skill contest <i>only</i> on the
 * clean-rebound remainder. This is deliberately NOT "run the board contest, then
 * flip some results to OOB on the same skill-decided side" — that would make OOB
 * inherit the board winner (a dominant offensive rebounder's OOBs skewing
 * offense), which #026 A rejects. Net: the rebound-vs-rebound balance is
 * skill-weighted (the deliberate difference from the flat {@link BlockResolver},
 * Decision C), while the OOB slices are flat + defense-leaning.
 *
 * <p><b>OOB credits no rebounder</b> (Decision E): {@link Result#rebounder()} is
 * populated only on the two rebound outcomes; on OOB it is {@code null} and the
 * engine records no rebound, so OOB events stay out of the rebound reconciliation
 * invariant.
 *
 * <p><b>Cap (one of three retention paths):</b> {@code capReached} is passed in so
 * the resolver never returns an offense-retained outcome when the second-chance
 * cap is hit — a would-be {@code OFFENSIVE_REBOUND} is forced to
 * {@code DEFENSIVE_REBOUND} and a would-be {@code OOB_OFFENSE} to
 * {@code OOB_DEFENSE} (the outcome <i>family</i> is preserved — an OOB stays an
 * OOB event, a rebound stays a rebound event). The loop itself still owns
 * {@code continue} vs. {@code return}; this just centralizes the "force an ending
 * outcome when capped" logic that was duplicated at the block fork + rebound branch.
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
                && reboundResolver.isOffensiveRebound(offRebounder, defRebounder, rng);
        if (offensiveRebound) {
            return new Result(MissedShotOutcome.OFFENSIVE_REBOUND, offRebounder);
        }
        return new Result(MissedShotOutcome.DEFENSIVE_REBOUND, defRebounder);
    }
}
