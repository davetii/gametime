package software.daveturner.gametime.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * §3.5 (decisions.md #023): the mutable per-team rotation — the full squad plus
 * which five are currently on the floor — that makes the on-floor set dynamic
 * within a game. {@link TeamContext} holds one of these (a record may hold a
 * reference to a mutable object) and exposes {@link #onFloor()}, so the possession
 * loop and every shooter/defender/rebounder pick read the <i>current</i> five
 * without re-plumbing the engine.
 *
 * <p><b>Determinism — §3.13 (decisions.md #031) REVISED this.</b> #023 Decision C
 * made the substitution check deterministic and RNG-free on purpose. §3.13's
 * foul-trouble sub is a coaching <i>judgement</i> rather than a state trigger, so
 * {@link #advancePossession(RandomGenerator)} now takes a {@link RandomGenerator}
 * and consumes <b>exactly one draw per call</b>, taken unconditionally at a fixed
 * point so the stream never forks on rotation state. Everything else here is still
 * state-derived (energy, fouls, the coach's rotation factors), and the step still
 * emits no {@link GameData} events.
 *
 * <p><b>Invariant (Decision F):</b> {@link #onFloor()} is <b>always exactly 5</b>.
 * Substitutions swap one-for-one, and the last-resort path keeps the least-fouled
 * player on rather than dropping below five, so every pick site can assume five.
 */
public class RotationState {

    private final SimConfig config;
    private final CoachModifiers modifiers;

    /** Full squad, ordered starters-first then bench by rotationOrder (stable). */
    private final List<PlayerGameState> squad;
    /** The current on-floor five (a subset of {@link #squad}; always size 5). */
    private final List<PlayerGameState> onFloor;
    /** How far down the rotationOrder bench queue fatigue subs may draw. */
    private final int rotationDepth;

    /**
     * @param squad the full rotation, ordered starters-first then bench ascending
     *              by rotationOrder (built by {@code GameSimulator.buildRotation}).
     *              Must contain at least 5 players.
     */
    public RotationState(List<PlayerGameState> squad, CoachModifiers modifiers, SimConfig config) {
        if (squad.size() < 5) {
            throw new IllegalArgumentException(
                    "A rotation needs at least 5 players, got " + squad.size());
        }
        this.config = config;
        this.modifiers = modifiers;
        this.squad = new ArrayList<>(squad);
        this.onFloor = new ArrayList<>(squad.subList(0, 5));
        this.rotationDepth = config.rotationDepth(modifiers.rotationDepthFactor());
    }

    /** The current on-floor five (always exactly 5). */
    public List<PlayerGameState> onFloor() {
        return onFloor;
    }

    /** The full squad (for box-score population — every player who took the floor). */
    public List<PlayerGameState> squad() {
        return squad;
    }

    /** Players not currently on the floor. */
    private List<PlayerGameState> bench() {
        List<PlayerGameState> bench = new ArrayList<>();
        for (PlayerGameState p : squad) {
            if (!onFloor.contains(p)) {
                bench.add(p);
            }
        }
        return bench;
    }

    /**
     * The between-possession rotation step, run before the team plays a possession,
     * in three tiers of decreasing force (#031 F/H):
     * <ol start="0">
     *   <li>drain on-floor energy, recover the benched (§3.5 Decision B)</li>
     *   <li><b>hard/forced</b> — {@link #replaceFouledOut()} (§3.5 Decision F)</li>
     *   <li><b>soft/preference</b> — {@link #runFoulTroubleSub} (§3.13, #031 B)</li>
     *   <li><b>fatigue</b> — {@link #runFatigueSubs()} (§3.5 Decisions C/D)</li>
     * </ol>
     * §3.14's ejections extend tier 1, not a fourth path (#031 H).
     *
     * <p><b>This step CONSUMES RNG (§3.13), which REVISES #023 C.</b> That decision
     * made substitution deterministic and RNG-free on purpose ("subs are a coaching
     * decision, not chance"). #031 B reverses it, and the reversal is argued rather
     * than incidental: that reasoning fits <i>fatigue</i> subs, whose trigger is a
     * measurable state, far better than foul trouble, whose trigger is a
     * <i>judgement</i> — two coaches facing an identical 4-foul situation genuinely
     * make different calls. <b>An RNG draw here is not a bug against #023 C.</b>
     *
     * <p>The draw is taken <b>unconditionally, at a fixed point</b> — before any
     * candidate is examined — so the seed stream advances identically regardless of
     * rotation state. A conditional draw would fork the stream on which players
     * happen to be in foul trouble and make the shift unreproducible.
     */
    public void advancePossession(RandomGenerator rng) {
        for (PlayerGameState p : onFloor) {
            p.drainForPossession();
        }
        for (PlayerGameState p : bench()) {
            p.recoverForPossession();
        }
        double foulTroubleRoll = rng.nextDouble();
        replaceFouledOut();
        runFoulTroubleSub(foulTroubleRoll);
        runFatigueSubs();
    }

    /**
     * §3.13 (decisions.md #031 B/D/F): the <b>soft</b> foul-trouble sub — the
     * counter-pressure the engine was missing, and the only thing in it that reacts
     * to foul <i>trouble</i> rather than foul-<i>out</i>.
     *
     * <p>The most foul-troubled on-floor player is a bench <b>candidate</b>; whether
     * he sits is {@code base(fouls) × coach × value × roster} (see {@link
     * SimConfig#foulTroubleSitProbability}) against the caller's pre-drawn roll.
     *
     * <p><b>The star-protection INVERSION is deliberate (#031 B).</b> {@link
     * SimConfig#STARTER_SUB_THRESHOLD_BONUS} lets starters tolerate <i>more</i>
     * fatigue before being pulled; this rule pulls the better player <i>sooner</i>.
     * You ride your star when he's tired, you protect him when he's in foul trouble.
     * It reads like an inconsistency with the fatigue rule and is not — do not
     * "fix" it into agreement.
     *
     * <p><b>The rule yields (#031 F).</b> It draws only from the {@code
     * rotationDepth} window (a rotation decision, not the emergency full-bench reach
     * {@link #replaceFouledOut()} gets), and fires only if a genuinely eligible
     * replacement exists. If the bench cannot cover it the foul-troubled player
     * keeps playing: a soft preference never weakens the hard never-below-5
     * invariant, and never reaches for an ineligible (fouled-out) player.
     *
     * <p><b>STICKY SIT (#031 D).</b> A player benched here becomes an <b>ordinary
     * bench player</b> — no "benched for fouls" status, no exclusion to lift, and no
     * competing foul-trouble roll to bring him back. His return runs <b>only</b>
     * through {@link #runFatigueSubs()}'s freshness path, and the return timer is
     * energy recovery itself (~2× the drain, §3.5 B), which is free. Only the
     * <i>sit</i> is probabilistic; the <i>return</i> is earned. That asymmetry is
     * load-bearing: this runs ~100 times per team per game, and a rule that re-rolled
     * in both directions at that cadence would visibly flicker a 4-foul player on and
     * off the floor. For the same reason {@code substitutionAggressiveness} is used
     * once, here, and is NOT re-applied to the return.
     */
    private void runFoulTroubleSub(double roll) {
        PlayerGameState candidate = mostFoulTroubledCandidate();
        if (candidate == null) {
            return;
        }
        double sitProbability = config.foulTroubleSitProbability(
                candidate.foulTroubleLevel(),
                modifiers.subAggressivenessFactor(),
                candidate.valueComposite(),
                candidate.isStarter(),
                candidate.getRotationOrder());
        if (roll >= sitProbability) {
            return;
        }
        PlayerGameState replacement = freshestEligibleBench(benchWithinDepth());
        if (replacement == null) {
            return; // the bench can't cover it — the rule yields and he keeps playing.
        }
        // The replacement must be fresher by a MARGIN — the anti-oscillation guard
        // (#031 D), and the one piece of the sticky sit that was not free. A player
        // benched for foul trouble rests to full, returns through the freshness path,
        // and for a few possessions is only marginally less fresh than the bench;
        // with a bare `>` comparison the rule re-fires at once and he flickers on and
        // off across consecutive possessions — measured, not hypothetical. Requiring
        // a real rest advantage makes the sit stick in both directions using only
        // energy state that already exists: no "benched for fouls" flag, no timer.
        if (replacement.getCurrentEnergy()
                <= candidate.getCurrentEnergy() + SimConfig.FOUL_TROUBLE_FRESHNESS_MARGIN) {
            return;
        }
        onFloor.set(onFloor.indexOf(candidate), replacement);
    }

    /**
     * The on-floor player deepest in foul trouble, or null if nobody is carrying
     * enough fouls for the rule to have anything to say. Ties broken by higher value
     * then squad order, so the check is deterministic given the roll.
     */
    private PlayerGameState mostFoulTroubledCandidate() {
        PlayerGameState candidate = null;
        for (PlayerGameState p : onFloor) {
            if (p.isFouledOut()
                    || SimConfig.FOUL_TROUBLE_SIT_PROBABILITY[p.foulTroubleLevel()] <= 0.0) {
                continue;
            }
            if (candidate == null
                    || p.foulTroubleLevel() > candidate.foulTroubleLevel()
                    || (p.foulTroubleLevel() == candidate.foulTroubleLevel()
                        && p.valueComposite() > candidate.valueComposite())) {
                candidate = p;
            }
        }
        return candidate;
    }

    /**
     * Force off every on-floor player who has fouled out (Decision F), replacing
     * each from the <b>full</b> bench (not just the rotationDepth window) with the
     * freshest non-fouled-out player. If no eligible replacement exists, the
     * fouled-out player stays on — the never-below-5 last resort.
     */
    private void replaceFouledOut() {
        for (int i = 0; i < onFloor.size(); i++) {
            PlayerGameState p = onFloor.get(i);
            if (!p.isFouledOut()) {
                continue;
            }
            PlayerGameState replacement = freshestEligibleBench(bench());
            if (replacement != null) {
                onFloor.set(i, replacement);
            }
            // else: no eligible player anywhere — keep them on (floor stays at 5).
        }
    }

    /**
     * Fatigue subs (Decisions C/D): pull the most-tired on-floor player who is
     * below their (starter-adjusted, aggressiveness-scaled) energy threshold and
     * swap for the freshest eligible bench player within the rotationDepth window,
     * but only if that bench player is actually fresher. Starters tolerate more
     * fatigue (lower threshold) and, being fresher on return, re-enter first.
     * One swap per call keeps subs gradual and the ordering reproducible.
     */
    private void runFatigueSubs() {
        PlayerGameState tired = mostTiredCandidate();
        if (tired == null) {
            return;
        }
        PlayerGameState replacement = freshestEligibleBench(benchWithinDepth());
        if (replacement != null && replacement.getCurrentEnergy() > tired.getCurrentEnergy()) {
            int idx = onFloor.indexOf(tired);
            onFloor.set(idx, replacement);
        }
    }

    /**
     * The most-tired on-floor player below their sub threshold, or null if no one
     * qualifies. Ties broken by lowest energy then squad order (deterministic).
     */
    private PlayerGameState mostTiredCandidate() {
        PlayerGameState candidate = null;
        for (PlayerGameState p : onFloor) {
            double threshold = config.subEnergyThreshold(
                    modifiers.subAggressivenessFactor(), p.isStarter());
            if (p.getCurrentEnergy() < threshold) {
                if (candidate == null || p.getCurrentEnergy() < candidate.getCurrentEnergy()) {
                    candidate = p;
                }
            }
        }
        return candidate;
    }

    /** Bench players eligible to be on the floor: not fouled out. */
    private List<PlayerGameState> eligible(List<PlayerGameState> pool) {
        List<PlayerGameState> eligible = new ArrayList<>();
        for (PlayerGameState p : pool) {
            if (!p.isFouledOut()) {
                eligible.add(p);
            }
        }
        return eligible;
    }

    /**
     * The freshest (highest currentEnergy) eligible player in the pool, or null if
     * none. Ties broken by squad order (starters before bench, then rotationOrder)
     * so rested starters return first (Decision C).
     */
    private PlayerGameState freshestEligibleBench(List<PlayerGameState> pool) {
        return eligible(pool).stream()
                .max(Comparator.comparingDouble(PlayerGameState::getCurrentEnergy)
                        .thenComparing(p -> -squad.indexOf(p)))
                .orElse(null);
    }

    /**
     * The bench, limited to the coach's rotationDepth window (the first
     * {@code rotationDepth} bench players by squad order). Fatigue subs stay within
     * this window; forced foul-out subs use the full bench.
     */
    private List<PlayerGameState> benchWithinDepth() {
        List<PlayerGameState> bench = bench();
        int limit = Math.min(rotationDepth, bench.size());
        return new ArrayList<>(bench.subList(0, limit));
    }
}
