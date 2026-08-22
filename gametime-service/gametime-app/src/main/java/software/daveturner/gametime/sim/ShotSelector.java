package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class ShotSelector {

    public PlayerGameState pickShooter(List<PlayerGameState> offensivePlayers, RandomGenerator rng) {
        double totalWeight = offensivePlayers.stream()
                .mapToDouble(PlayerGameState::offensiveWeight)
                .sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : offensivePlayers) {
            cumulative += p.offensiveWeight();
            if (roll < cumulative) return p;
        }
        return offensivePlayers.get(offensivePlayers.size() - 1);
    }

    public ShotType pickShotType(PlayerGameState shooter, RandomGenerator rng) {
        return pickShotType(shooter, 1.0, rng);
    }

    /**
     * §3.4 / §3.17: {@code shotMixLean} (the offensive coach's {@code offensiveScheme}
     * modifier) leans the draw along the <b>mid-range-versus-three</b> axis.
     * {@code 1.0} = the unleaned draw.
     *
     * <p><b>⚠ §3.17 (decisions.md #040 D) SPLIT THIS INTO TWO OPPOSED LEANS.</b> Until
     * now the lean scaled <b>PERIMETER and THREE together</b>, so a jump-shooting coach
     * raised mid-range and threes in lockstep — <b>the one shape the real game
     * forbids</b>, since the modern game trades the mid-range jumper <i>for</i> the
     * three. #036 D named that conflation as the blocker before anything was measured,
     * and it could not survive the pass that owns the 3PA gap.
     *
     * <p>It is now: <b>THREE × {@code shotMixLean}; PERIMETER × its RECIPROCAL; DRIVE
     * and POST unscaled.</b> A high-{@code offensiveScheme} coach shoots more threes
     * <i>and fewer mid-range jumpers</i>; a low one does the inverse. The axis is
     * mid-range-vs-three, <b>not</b> jumper-vs-interior.
     *
     * <p>⚠ <b>Aggregate-neutral by design.</b> {@code offensiveScheme} centres on 10
     * across the league, so the two directions roughly cancel over a full slate. This
     * split exists so that a <i>given</i> coach finally means something — it is not a
     * 3PA lever, and the league mix is {@code sim.shot-share-*}'s job (#040 C).
     *
     * <p>{@link CoachModifiers#shotMixLean()} itself is <b>untouched</b> — it stays the
     * {@code offensiveScheme} avg-10 multiplier (#022). The conflation lived here and so
     * does the split: <b>no new coach attribute</b> (#018's five are not reopened) and
     * <b>no new {@code SimConfig} value</b> — the reciprocal is free.
     *
     * <p>⚠ The reciprocal makes {@code offensiveScheme} a <b>stronger</b> lever than
     * before (two types moving in opposite directions), and nothing bounds how extreme
     * an attribute-20 coach's mix becomes (#040 D trade-off).
     */
    public ShotType pickShotType(PlayerGameState shooter, double shotMixLean,
                                 RandomGenerator rng) {
        ShotType[] types = ShotType.values();
        double totalWeight = 0;
        for (ShotType t : types) {
            totalWeight += leanedWeight(shooter, t, shotMixLean);
        }
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (ShotType t : types) {
            cumulative += leanedWeight(shooter, t, shotMixLean);
            if (roll < cumulative) return t;
        }
        return types[types.length - 1];
    }

    /**
     * §3.17 (#040 D): the coach's lean applied to one type's weight — THREE takes
     * {@code shotMixLean}, PERIMETER takes its reciprocal, DRIVE and POST are unscaled.
     * See {@link #pickShotType(PlayerGameState, double, RandomGenerator)} for why the
     * two jumper types now move in opposite directions.
     *
     * <p>The lean is guarded against a non-positive value: {@link CoachModifiers} cannot
     * produce one today, but the reciprocal is the first expression here that would turn
     * a zero into a division blow-up rather than a harmless zero weight.
     */
    private double leanedWeight(PlayerGameState shooter, ShotType type, double shotMixLean) {
        double w = shooter.shotTypeWeight(type);
        if (shotMixLean <= 0) {
            return w;
        }
        return switch (type) {
            case THREE -> w * shotMixLean;
            case PERIMETER -> w / shotMixLean;
            case DRIVE, POST -> w;
        };
    }

    public PlayerGameState pickDefender(List<PlayerGameState> defensivePlayers, RandomGenerator rng) {
        double totalWeight = defensivePlayers.stream()
                .mapToDouble(PlayerGameState::getIndividualDefense)
                .sum();
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState d : defensivePlayers) {
            cumulative += d.getIndividualDefense();
            if (roll < cumulative) return d;
        }
        return defensivePlayers.get(defensivePlayers.size() - 1);
    }
}
