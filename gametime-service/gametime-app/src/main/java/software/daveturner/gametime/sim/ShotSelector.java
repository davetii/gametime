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
     * for THIS draw only. See {@link SimConfig#offensiveRebounderShotWeight()} for why
     * the mechanic is a multiplicative weight.
     *
     * <p><b>A WEIGHT, NOT A BRANCH.</b> Still one weighted draw over the five, still
     * exactly ONE {@code nextDouble()}; only one player's weight differs. ⚠ In particular
     * <b>the shot TYPE is not forced to the rim</b> — {@link #pickShotType} already bends
     * by the shooter's own skills (#040 C) and a rebounder leans interior on his own
     * (measured 54.8% vs a teammate's 45.6%). Forcing the type would bolt a second
     * mechanism onto a job the first already does.
     *
     * <p><b>⚠ {@code rebounder} is a PARTICIPANT, not a MODE — which is why it is a
     * parameter here where #043 H rejected one.</b> That flag would have switched off the
     * main thing its method does; this one <i>feeds</i> the same operation. {@code null}
     * means "no player was identified", the state on the four retention paths where
     * nobody secured the ball (OOB-offense, both flagrant retentions, the rebounding
     * foul's by-rule retain). ⚠ Do not split out a {@code pickPutbackShooter}: it would
     * duplicate this loop verbatim but for one multiplication, and every caller would
     * still test {@code rebounder == null} to choose between the two.
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
     * <p><b>TWO OPPOSED LEANS</b> (#040 D): <b>THREE × {@code shotMixLean}; PERIMETER ×
     * its RECIPROCAL; DRIVE and POST unscaled.</b> A high-{@code offensiveScheme} coach
     * shoots more threes <i>and fewer mid-range jumpers</i>. ⚠ The axis is
     * mid-range-vs-three, <b>NOT</b> jumper-vs-interior — scaling PERIMETER and THREE
     * together is the one shape the real game forbids, since the modern game trades the
     * mid-range jumper <i>for</i> the three.
     *
     * <p>⚠ <b>Aggregate-neutral by design, so this is NOT a 3PA lever.</b> {@code
     * offensiveScheme} centres on 10 across the league, so the two directions roughly
     * cancel over a full slate; the split exists so a <i>given</i> coach means something.
     * The league mix is {@code sim.shot-share-*}'s job (#040 C).
     *
     * <p>⚠ The reciprocal makes {@code offensiveScheme} a <b>stronger</b> lever than a
     * single-direction one, and nothing bounds how extreme an attribute-20 coach's mix
     * becomes (#040 D trade-off).
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
