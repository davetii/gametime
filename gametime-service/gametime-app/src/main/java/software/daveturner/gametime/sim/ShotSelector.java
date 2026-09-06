package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class ShotSelector {

    private final SimConfig config;

    public ShotSelector(SimConfig config) {
        this.config = config;
    }

    /**
     * The ordinary draw, with no identified offensive rebounder — every caller outside
     * the second-chance loop's re-entry. Delegates with {@code null}, which means "no
     * such participant" and is bit-identical to the pre-§3.22 draw.
     */
    public PlayerGameState pickShooter(List<PlayerGameState> offensivePlayers, RandomGenerator rng) {
        return pickShooter(offensivePlayers, null, rng);
    }

    /**
     * §3.22 (decisions.md #044 A/H): the weighted shooter draw, with the player who took
     * the last offensive board weighted {@code × sim.offensive-rebounder-shot-weight}
     * for THIS draw only. Before §3.22 an offensive rebound re-entered {@link
     * PossessionEngine}'s loop here at a draw that did not know who had just got the
     * ball, so a putback could not happen.
     *
     * <p><b>A WEIGHT, NOT A BRANCH.</b> Nothing is switched off and no outcome is
     * forced: it is still one weighted draw over the five, still exactly ONE {@code
     * nextDouble()}, and only one player's weight differs. In particular <b>the shot
     * TYPE is not forced to the rim</b> — {@link #pickShotType} already bends by the
     * shooter's own skills (#040 C), and a rebounder (usually a big) leans interior on
     * his own, measured at 54.8% on the next shot against a teammate's 45.6%. Forcing
     * the type would bolt a second mechanism onto a job the first already does.
     *
     * <p>The multiplier composes with the draw rather than replacing it, so the
     * rebounder's <i>relative</i> standing survives: a low-weight rebounder doubled is
     * still below a high-weight teammate. {@code 1.0} turns the mechanic off with no
     * special case.
     *
     * <p><b>⚠ {@code rebounder} is a PARTICIPANT, not a MODE — and that is why it is a
     * parameter here where #043 H rejected one.</b> That flag would have switched off
     * the main thing its method does for two callers; this one <i>feeds</i> the same
     * operation. A {@code null} rebounder means "no player was identified" — exactly
     * what a {@code null} rebounder on the result records means — and it is the state
     * on the four retention paths where nobody secured the ball (OOB-offense, both
     * flagrant retentions, the rebounding foul's by-rule retain). A second
     * {@code pickPutbackShooter} was rejected: it would duplicate this loop verbatim but
     * for one multiplication, and every caller would still test {@code rebounder == null}
     * to choose between the two.
     *
     * @param rebounder the player who took the offensive board that returned the ball,
     *                  or {@code null} when no rebounder was identified
     */
    public PlayerGameState pickShooter(List<PlayerGameState> offensivePlayers,
                                       PlayerGameState rebounder, RandomGenerator rng) {
        double totalWeight = 0;
        for (PlayerGameState p : offensivePlayers) {
            totalWeight += shooterWeight(p, rebounder);
        }
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : offensivePlayers) {
            cumulative += shooterWeight(p, rebounder);
            if (roll < cumulative) return p;
        }
        return offensivePlayers.get(offensivePlayers.size() - 1);
    }

    /**
     * §3.22 (#044 A): one player's weight in the shooter draw — his {@code
     * offensiveWeight()}, multiplied by {@code sim.offensive-rebounder-shot-weight} for
     * the one player who just took the offensive board. A {@code null} rebounder leaves
     * every weight untouched.
     */
    private double shooterWeight(PlayerGameState p, PlayerGameState rebounder) {
        double w = p.offensiveWeight();
        return (p == rebounder) ? w * config.offensiveRebounderShotWeight() : w;
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
