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
     * §3.14a's ejections extend tier 1, not a fourth path (#031 H) — an ejected
     * player is filtered by {@link #eligible} exactly as a fouled-out one is, and
     * {@link #replaceFouledOut()} forces him off.
     *
     * <p><b>§3.14a (decisions.md #032 B) added a second responsibility: the technical
     * foul roll.</b> This is the engine's only "things that happen to a TEAM, not to
     * a possession" seam, and a technical is the one foul with no contest to hang off
     * — it is not caused by the shot, the matchup, or the rebound. Rolling it inside
     * the possession flow would make it inherit a {@code pickDefender} draw that has
     * nothing to do with it, manufacturing a causal link the real event does not
     * have. On a hit this returns the committer; the caller ({@link
     * PossessionEngine#simulate}) owns emitting the event and awarding the free
     * throw, because those need {@link GameData}, the team ids, the period and the
     * sequence — none of which belong in a rotation.
     *
     * <p><b>The possession is UNCHANGED (#032 D).</b> The roll fires BETWEEN
     * possessions, so there is nothing to fork — no retention, no switch, not even
     * when the offense commits it. §3.14a adds no branch to the possession path at
     * all, which is exactly what made it the cheap half of the §3.14 split.
     *
     * @return the on-floor player who committed a technical this call, or {@code
     *         null} (overwhelmingly the common case — the per-check probability is
     *         ~0.0035).
     *
     * <p><b>This step CONSUMES RNG (§3.13), which REVISES #023 C.</b> That decision
     * made substitution deterministic and RNG-free on purpose ("subs are a coaching
     * decision, not chance"). #031 B reverses it, and the reversal is argued rather
     * than incidental: that reasoning fits <i>fatigue</i> subs, whose trigger is a
     * measurable state, far better than foul trouble, whose trigger is a
     * <i>judgement</i> — two coaches facing an identical 4-foul situation genuinely
     * make different calls. <b>An RNG draw here is not a bug against #023 C.</b>
     *
     * <p><b>BOTH draws are taken unconditionally, at a fixed point</b> — before any
     * candidate is examined — so the seed stream advances identically regardless of
     * rotation state. A conditional draw would fork the stream on which players
     * happen to be in foul trouble (or on whether a technical fired) and make the
     * shift unreproducible. §3.14a takes <b>both</b> of its draws beside §3.13's for
     * exactly that reason — the technical roll AND the committer draw, the latter
     * even though it is used only on a hit (~0.35% of calls). Drawing it
     * unconditionally costs one {@code nextDouble()} and buys a flat, state-
     * independent draw count. Note this changes the count per call from <b>one to
     * three</b>, which re-baselines any seed-pinned stream expectation.
     */
    public PlayerGameState advancePossession(RandomGenerator rng) {
        for (PlayerGameState p : onFloor) {
            p.drainForPossession();
        }
        for (PlayerGameState p : bench()) {
            p.recoverForPossession();
        }
        double foulTroubleRoll = rng.nextDouble();
        double technicalRoll = rng.nextDouble();
        double committerRoll = rng.nextDouble();
        PlayerGameState technicalCommitter =
                resolveTechnicalFoul(technicalRoll, committerRoll);
        replaceFouledOut();
        runFoulTroubleSub(foulTroubleRoll);
        runFatigueSubs();
        return technicalCommitter;
    }

    /**
     * §3.14a (decisions.md #032 B/C): did this team commit a technical this check,
     * and if so, who wore it?
     *
     * <p>The RATE is a flat constant with <b>no causal input whatsoever</b> — no
     * player skill, no coach attribute, no game situation (#032 B). Only WHO commits
     * it is skill-weighted, by a {@code foulProne}-weighted draw over the <b>on-floor
     * five</b>, mirroring the weighted-selection shape used throughout ({@link
     * ShotSelector#pickDefender}, {@link FoulResolver#pickCommitter}).
     *
     * <p><b>The bench is excluded, and the reason is measurement bias rather than
     * realism (#032 C).</b> In reality a technical can be called on a benched player
     * or a coach. But the bench pool is ~10 against the floor's 5, so ~2/3 of
     * technicals would land on players who are not playing and whose ejections have
     * <b>no engine consequence</b> — a benched ejected player is simply never
     * selected again. The rate budget would be spent mostly where nothing observable
     * happens. The accepted fidelity loss, stated plainly: <b>bench and coach
     * technicals are not modelled.</b>
     *
     * <p><b>Expect the committer draw to look near-uniform, and do not "improve"
     * it.</b> {@code foulProne} is nearly flat (sd 1.22 against a ~9.66 mean, #031
     * A), so a high-{@code foulProne} player is only ~2× likelier than a low one, not
     * 10×. That weak signal is <b>affordable precisely because #032 B made the rate
     * causally inert</b>: the weighting moves no aggregate, no calibrated number, and
     * nothing observable at the 33–71 events a harness run produces. The raw
     * {@code aggression}/{@code composure}/{@code ego} <i>attributes</i> were
     * deliberately NOT threaded in — the engine consumes skills, and {@code
     * foulProne} already IS that composite by construction (player.md:122:
     * "Aggression and reckless energy raise it; composure and awareness lower it").
     *
     * <p><b>No clamp on the committer draw</b> — it is a weighted selection, not a
     * probability. Every on-floor player with non-zero {@code foulProne} keeps a
     * non-zero chance; no player is exempt by construction (#032 H).
     */
    private PlayerGameState resolveTechnicalFoul(double roll, double committerRoll) {
        if (roll >= config.technicalFoulProbability()) {
            return null;
        }
        PlayerGameState committer = pickTechnicalCommitter(committerRoll);
        if (committer == null) {
            return null;
        }
        committer.recordTechnicalFoul();
        return committer;
    }

    /**
     * §3.14a (#032 C): the {@code foulProne}-weighted draw over the on-floor five.
     * Takes the caller's pre-drawn roll in [0, 1) (the fixed-point discipline above)
     * and scales it across the weight total. Returns null only in the degenerate case
     * where every on-floor player has non-positive {@code foulProne} — unreachable
     * with real skills, which are ≥ 1.
     */
    private PlayerGameState pickTechnicalCommitter(double committerRoll) {
        double totalWeight = 0;
        for (PlayerGameState p : onFloor) {
            totalWeight += Math.max(0.0, p.getFoulProne());
        }
        if (totalWeight <= 0.0) {
            return null;
        }
        double roll = committerRoll * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : onFloor) {
            cumulative += Math.max(0.0, p.getFoulProne());
            if (roll < cumulative) return p;
        }
        return onFloor.get(onFloor.size() - 1);
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
                <= candidate.getCurrentEnergy() + config.foulTroubleFreshnessMargin()) {
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
            // §3.14a: a disqualified player is not a SOFT-sub candidate. In practice
            // the hard tier has already forced him off, so this only bites in the
            // never-below-5 case — where the soft rule would find no replacement
            // either. Asked through the shared predicate so the two tiers cannot
            // drift apart on what "disqualified" means.
            if (isDisqualified(p)
                    || config.foulTroubleSitProbabilities()[p.foulTroubleLevel()] <= 0.0) {
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
     * The <b>hard/forced</b> tier: force off every on-floor player who has fouled out
     * (§3.5 Decision F), been ejected on two technicals (§3.14a, #032 F) or — since
     * §3.14b (#034 F) — been ejected on a flagrant-2, replacing each from the
     * <b>full</b> bench (not just the rotationDepth window) with the freshest eligible
     * player. If no eligible replacement exists, the disqualified player stays on —
     * the never-below-5 last resort (#023 F), which ejections do not weaken.
     *
     * <p>The method keeps its name: an ejection is the same forced removal under a
     * further cause, and #031 H ruled out a separate removal path for exactly that
     * reason. All three causes are derived predicates over monotonic counters, so
     * nothing here needs to know which one fired.
     */
    private void replaceFouledOut() {
        for (int i = 0; i < onFloor.size(); i++) {
            PlayerGameState p = onFloor.get(i);
            if (!isDisqualified(p)) {
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

    /**
     * Players eligible to be on the floor: not fouled out, not ejected on technicals
     * (§3.14a, #032 F) and — since §3.14b (#034 F) — not ejected on a flagrant-2.
     *
     * <p><b>This is the single filter every candidate pool passes through</b>, which
     * is precisely why each new ejection cause extends it rather than adding a removal
     * path: disqualification is disqualification, whatever produced it, and all three
     * predicates are derived over monotonic counters (#023 F).
     */
    private List<PlayerGameState> eligible(List<PlayerGameState> pool) {
        List<PlayerGameState> eligible = new ArrayList<>();
        for (PlayerGameState p : pool) {
            if (!isDisqualified(p)) {
                eligible.add(p);
            }
        }
        return eligible;
    }

    /**
     * §3.14a (#032 F) / §3.14b (#034 F): the hard/forced tier's disqualification
     * predicate — fouled out (§3.5 F), ejected on two technicals (§3.14a), or ejected
     * on a flagrant-2 (§3.14b). One question with <b>three</b> causes, asked
     * identically by {@link #eligible} and {@link #replaceFouledOut()}.
     *
     * <p><b>Extending this predicate is the WHOLE of §3.14b's rotation change — one
     * line, and NOT a fourth removal path</b> (#031 H, now held for the third pass
     * running). All three causes are derived predicates over monotonic counters
     * (#023 F), so nothing downstream needs to know which one fired.
     *
     * <p><b>§3.14b is the pass where this tier is finally EXERCISED.</b> §3.14a's
     * ejections measure ~0.014 per team-game — essentially never. Flagrant ejections
     * land around <b>0.024</b> (~0.16 flagrants × the 15% severity share), roughly
     * double that, so the forced substitution below now actually runs from time to
     * time rather than being dead-but-correct code.
     */
    private boolean isDisqualified(PlayerGameState p) {
        return p.isFouledOut() || p.isEjected() || p.isEjectedForFlagrant();
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
