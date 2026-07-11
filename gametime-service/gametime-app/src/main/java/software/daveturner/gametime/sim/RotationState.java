package software.daveturner.gametime.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * §3.5 (decisions.md #023): the mutable per-team rotation — the full squad plus
 * which five are currently on the floor — that makes the on-floor set dynamic
 * within a game. {@link TeamContext} holds one of these (a record may hold a
 * reference to a mutable object) and exposes {@link #onFloor()}, so the possession
 * loop and every shooter/defender/rebounder pick read the <i>current</i> five
 * without re-plumbing the engine.
 *
 * <p><b>Determinism (Decision C):</b> {@link #advancePossession()} decides subs
 * purely from state — energy, fouls, and the coach's rotation factors — and
 * consumes <b>no RNG</b>. It emits no {@link GameData} events. The §3.4 seed-pinned
 * stream is untouched by it; the only reason §3.4 assertions move is that the five
 * on the floor now change during a game.
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
     * The between-possession rotation step (Decisions C/F), run before the team
     * plays a possession: drain on-floor energy, recover the benched, replace any
     * fouled-out player (forced), then run fatigue subs. Deterministic, RNG-free.
     */
    public void advancePossession() {
        for (PlayerGameState p : onFloor) {
            p.drainForPossession();
        }
        for (PlayerGameState p : bench()) {
            p.recoverForPossession();
        }
        replaceFouledOut();
        runFatigueSubs();
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
