package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

/**
 * §3.7 (decisions.md #025 D): resolves where a blocked shot's loose ball ends up.
 * A FLAT four-way roll — fixed weights identical for every block, no skill input —
 * because a swatted ball is a chaotic loose ball dominated by physics/chance, not
 * the {@code offenseRebound}-vs-{@code defenseRebound} box-out contest {@link
 * ReboundResolver} models. Keeping it flat is the more accurate model AND keeps
 * this resolver architecturally distinct from {@code ReboundResolver} (Decision D).
 * The split is defense-leaning; the weights are unsourced placeholders settled by
 * the {@code CalibrationHarness} against the ~5-blocks/team target.
 */
@Component
public class BlockResolver {

    private final SimConfig config;

    public BlockResolver(SimConfig config) {
        this.config = config;
    }

    /**
     * Roll the flat four-way recovery. Weights come from {@code SimConfig} and are
     * normalized by their sum (they need not sum to 1), mirroring the weighted-draw
     * pattern in {@link ReboundResolver}. Skill-independent by design.
     */
    public BlockRecovery resolveRecovery(RandomGenerator rng) {
        double recoveredDefense = config.blockRecoveredDefense();
        double recoveredOffense = config.blockRecoveredOffense();
        double oobDefense = config.blockOobDefense();
        double oobOffense = config.blockOobOffense();
        double total = recoveredDefense + recoveredOffense + oobDefense + oobOffense;

        double roll = rng.nextDouble() * total;
        if (roll < recoveredDefense) {
            return BlockRecovery.RECOVERED_DEFENSE;
        }
        roll -= recoveredDefense;
        if (roll < recoveredOffense) {
            return BlockRecovery.RECOVERED_OFFENSE;
        }
        roll -= recoveredOffense;
        if (roll < oobDefense) {
            return BlockRecovery.OOB_DEFENSE;
        }
        return BlockRecovery.OOB_OFFENSE;
    }
}
