package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.entity.PlayType;

import java.util.*;
import java.util.random.RandomGenerator;

@Component
public class PossessionEngine {

    /**
     * §3.14b (decisions.md #034): the {@code outcome} strings for the two flagrant
     * grades, on the existing {@link PlayType#FOUL} — a flagrant is a KIND of foul, so
     * no new {@code PlayType} (the #025 F / #026 E / #028 D / #032 reuse discipline),
     * and free text since #020 means no migration. They mirror the established {@code
     * SHOOTING_FOUL} / {@code REBOUNDING_FOUL_*} / {@code AND_ONE} / {@code
     * TECHNICAL_FOUL} vocabulary and collide with nothing in the §3.8/§3.9/§3.10
     * strings (the #027 D discipline).
     *
     * <p><b>They live HERE rather than on {@link GameData}, and the contrast with
     * {@link GameData#TECHNICAL_FOUL_OUTCOME} is deliberate.</b> That constant sits on
     * {@code GameData} because that class must <i>recognise</i> it — it is the one
     * outcome excluded from the penalty tally, and a literal on both sides of that
     * agreement is how the two would drift apart. <b>A flagrant COUNTS toward the
     * bonus</b> (#034 I), so {@code GameData} needs no entry for it and these belong
     * with the class that emits them.
     *
     * <p>The grade rides the outcome suffix rather than a separate field, so nothing
     * else carries it.
     */
    static final String FLAGRANT_FOUL_1_OUTCOME = "FLAGRANT_FOUL_1";
    static final String FLAGRANT_FOUL_2_OUTCOME = "FLAGRANT_FOUL_2";

    /**
     * §3.16 (decisions.md #039 B/E): the foul that awards no free throws outside the
     * bonus — {@link FoulResolver#isNonShootingFoul}'s outcome.
     *
     * <p><b>⚠ The event outcome is {@code COMMON_FOUL} while the config key is {@code
     * sim.non-shooting-foul-share}, and the divergence is INTENTIONAL, not drift</b>
     * (#039 E). This string sits in a play-by-play list of basketball terms
     * ({@code SHOOTING_FOUL}, {@code REBOUNDING_FOUL_*}, {@code TECHNICAL_FOUL},
     * {@code FLAGRANT_FOUL_*}) where the jargon reads correctly and
     * {@code NON_SHOOTING_FOUL} would read as a clumsy negation; the config key is
     * read by a tuner with no such surrounding vocabulary, so it defines itself
     * against {@code SHOOTING_FOUL} instead. Different audiences, named for different
     * readers.
     *
     * <p>Like the flagrant outcomes above it lives HERE rather than on {@link
     * GameData}: a common foul <b>counts</b> toward the penalty (#039 B), so {@code
     * GameData} needs no entry for it — only the technical, the one excluded outcome,
     * must be recognised on both sides of that agreement.
     */
    static final String COMMON_FOUL_OUTCOME = "COMMON_FOUL";

    private final ShotSelector shotSelector;
    private final ShotResolver shotResolver;
    private final TurnoverResolver turnoverResolver;
    private final FoulResolver foulResolver;
    private final BlockResolver blockResolver;
    private final MissedShotResolver missedShotResolver;
    private final SimConfig config;

    public PossessionEngine(ShotSelector shotSelector, ShotResolver shotResolver,
                            TurnoverResolver turnoverResolver, FoulResolver foulResolver,
                            BlockResolver blockResolver, MissedShotResolver missedShotResolver,
                            SimConfig config) {
        this.shotSelector = shotSelector;
        this.shotResolver = shotResolver;
        this.turnoverResolver = turnoverResolver;
        this.foulResolver = foulResolver;
        this.blockResolver = blockResolver;
        this.missedShotResolver = missedShotResolver;
        this.config = config;
    }

    public GameData simulate(TeamContext home, TeamContext away,
                             int possessionsPerPeriod, RandomGenerator rng) {
        GameData data = new GameData();
        data.setHomeTeamId(home.teamId());
        data.setAwayTeamId(away.teamId());
        int sequence = 1;
        int period = 1;

        // §3.4 (Decision A): pace scales the possession COUNT. Both teams share one
        // possession count (they alternate), so we blend the two coaches' pace
        // multipliers — a fast coach against a slow one lands in between.
        double paceFactor = (home.modifiers().paceMultiplier()
                + away.modifiers().paceMultiplier()) / 2.0;
        int pacedPossessions = Math.max(1,
                (int) Math.round(possessionsPerPeriod * paceFactor));

        while (true) {
            int possessionsThisPeriod = (period <= SimConfig.PERIODS)
                    ? pacedPossessions
                    : config.otPossessionsPerPeriod();

            for (int poss = 0; poss < possessionsThisPeriod * 2; poss++) {
                boolean homeOnOffense = (poss % 2 == 0);
                TeamContext offense = homeOnOffense ? home : away;
                TeamContext defense = homeOnOffense ? away : home;

                // §3.5 (Decisions C/F): the between-possession substitution step.
                // Both teams are on the floor for every possession (one offense,
                // one defense), so both rotations advance once per possession —
                // drain the on-floor five's energy, recover the benched, force off
                // fouled-out and (§3.14a) ejected players, run the §3.13 foul-trouble
                // sub, run fatigue subs. As of §3.13 it consumes RNG, and as of
                // §3.14a that is THREE unconditional draws per call (decisions.md
                // #031 revising #023 C; #032 B). Ordering (home then away, then
                // resolve) is fixed so the seed-pinned stream stays reproducible.
                //
                // §3.14a (#032 B/D): the step now also rolls each team's TECHNICAL
                // foul and returns the committer, because a technical is the one foul
                // with no contest to hang off — it belongs to the team, not to the
                // possession. The rotation cannot emit the event itself (it has no
                // GameData, team ids, period or sequence), so the plumbing lands
                // here. The possession is UNCHANGED either way: this fires BETWEEN
                // possessions, so there is nothing to fork — not even when the team
                // that commits it is the one about to go on offense.
                PlayerGameState homeTechnical = home.rotation().advancePossession(rng);
                PlayerGameState awayTechnical = away.rotation().advancePossession(rng);

                // Both teams can independently draw one in the same possession, so
                // both are resolved, home first (the fixed ordering above).
                if (homeTechnical != null) {
                    sequence = awardTechnicalFoul(data, homeTechnical, home, away,
                            offense.teamId(), defense.teamId(), period, sequence, rng);
                }
                if (awayTechnical != null) {
                    sequence = awardTechnicalFoul(data, awayTechnical, away, home,
                            offense.teamId(), defense.teamId(), period, sequence, rng);
                }

                sequence = resolvePossession(data, offense, defense,
                        period, sequence, rng);
            }

            if (period >= SimConfig.PERIODS && data.getHomeScore() != data.getAwayScore()) {
                break;
            }
            period++;
        }

        data.setPeriods(period);
        return data;
    }

    int resolvePossession(GameData data, TeamContext offenseCtx, TeamContext defenseCtx,
                          int period, int sequence, RandomGenerator rng) {
        // §3.5: read the CURRENT on-floor five (the substitution step may have
        // swapped players since last possession). This single seam is where the
        // dynamic rotation reaches every downstream pick.
        List<PlayerGameState> offense = offenseCtx.onFloor();
        List<PlayerGameState> defense = defenseCtx.onFloor();
        String offTeamId = offenseCtx.teamId();
        String defTeamId = defenseCtx.teamId();
        double shotMixLean = offenseCtx.modifiers().shotMixLean();
        double defensivePressure = defenseCtx.modifiers().defensivePressure();
        // Team-efficiency multiplier (Decision C): the offense's teamOffense vs.
        // the defense's teamDefense, averaged over each five and applied once per
        // shot. Computed per-possession so it reflects who's on the floor.
        double teamOffense = averageTeamOffense(offense);
        double oppTeamDefense = averageTeamDefense(defense);

        // The offense keeps the ball as long as it grabs offensive rebounds, up
        // to MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION. Each attempt runs the full
        // flow (turnover → foul → shot); a missed shot rolls a rebound (§3.3).
        int offensiveRetentions = 0;
        while (true) {
            // Is the second-chance loop full? Hoisted here at §3.14b (#034 B): it was
            // computed identically at the block-recovery branch and again at the
            // rebound phase, and the flagrant sites need it EARLIER than either (the
            // stopped-shot fork is above both). One definition serving all four
            // retention paths — a pure move, no behavior change: `offensiveRetentions`
            // is only ever incremented immediately before a `continue`, so its value
            // at the top of an iteration is what both original sites read.
            boolean capReached =
                    offensiveRetentions >= config.maxOffensiveRetentionsPerPossession();

            PlayerGameState shooter = shotSelector.pickShooter(offense, rng);
            ShotType shotType = shotSelector.pickShotType(shooter, shotMixLean, rng);
            PlayerGameState defender = shotSelector.pickDefender(defense, rng);

            // 1. Turnover check
            if (turnoverResolver.isTurnover(shooter, defense, defensivePressure, rng)) {
                // §3.9 (decisions.md #027 A/B): the gate above is UNCHANGED — a
                // turnover happens exactly as often as before. Only now does a
                // weighted draw pick WHICH of the nine causes it was (a pure
                // re-partition; the count never moves). STOLEN keeps the pickStealer
                // + recordSteal() path; every cause credits the ball-handler
                // (recordTurnover, primaryPlayerId = shooter) exactly as today.
                TurnoverCause cause = turnoverResolver.pickCause(
                        shooter, teamOffense, defensivePressure, rng);
                if (cause == TurnoverCause.STOLEN) {
                    PlayerGameState stealer = turnoverResolver.pickStealer(defense, rng);
                    stealer.recordSteal();
                }
                shooter.recordTurnover();
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.TURNOVER, cause.outcome(), shooter.getPlayerId());
                sequence++;

                // §3.16 (decisions.md #039 G, fixing #037): a CHARGE IS A PERSONAL
                // FOUL, and until now the engine charged it to nobody — not the
                // committer's six, not the team-foul tally, not the box-score fouls
                // column. §3.9 (#027) added the cause without noticing.
                //
                // ⚠ TWO EVENTS FOR ONE OCCURRENCE: the TURNOVER above and the FOUL
                // below both describe the same charge. Every reconciliation that
                // counts events must tolerate that, and the harness's
                // `Fouls / team / game` line steps up ~1.26 for this reason ALONE —
                // which must not be misread as §3.16's re-partition misfiring.
                //
                // ⚠ committingTeamId = offTeamId. This is the ONLY site outside the
                // rebounding foul where the committer is on OFFENSE, so it is the
                // only other path that puts the DEFENSIVE team in the bonus.
                // GameData.isInBonus reads committingTeamId and already supports it;
                // it counts toward the bonus and FOUL_OUT_LIMIT because it IS a
                // personal foul (no exclusion in GameData.countsTowardBonus — only
                // the technical is excluded, #032 E).
                //
                // ⚠ NOT flagrant-eligible (#039 G), stated rather than implied: the
                // flagrant roll lives in FoulResolver's foul block below and this
                // path never reaches it. The possession already ends (it is a
                // turnover), so #039 C's possession-fork question does not arise.
                if (cause == TurnoverCause.OFFENSIVE_FOUL) {
                    shooter.recordFoul();
                    // The outcome string is TurnoverCause's own — the FOUL event and
                    // the TURNOVER event describe one occurrence, so they must read
                    // the same word, and a literal here would be a second copy of it.
                    data.addEvent(offTeamId, defTeamId, period, sequence,
                            PlayType.FOUL, cause.outcome(), shooter.getPlayerId(),
                            null, offTeamId);
                    sequence++;
                }
                return sequence;
            }

            // 2. Foul check — a foul that STOPPED the shot (no basket), on ANY shot
            // type since §3.12 (#030 A1). The FT count graduates with the type:
            // a stopped THREE is 3 FTs, everything else 2 (#030 C).
            if (foulResolver.isFoul(shotType, shooter, defender, defensivePressure, rng)) {
                defender.recordFoul();

                // §3.14b (#034 A/B/C): the severity roll, layered ON TOP of the foul
                // above — which has already been charged and is unchanged either way.
                // This site is ALWAYS defensive (a shooting foul is on the defender),
                // so a flagrant here ALWAYS retains for the offense.
                //
                // ⚠ The 2 FTs REPLACE shotType.freeThrowsIfFouled(), they do NOT add to
                // it (#034 C): a flagrant stopped THREE awards 2, not 3 and not 5. The
                // ordinary award below is in the `else` path and never runs on a hit.
                if (foulResolver.isFlagrant(rng)) {
                    sequence = awardFlagrant(data, defender, shooter, offTeamId,
                            offTeamId, defTeamId, period, sequence, rng);
                    if (!capReached) {
                        offensiveRetentions++;
                        continue; // the offense keeps the ball (#034 B)
                    }
                    return sequence; // cap full: FTs awarded, possession ends (§3.10's shape)
                }

                // §3.16 (decisions.md #039 A/B/C/E): the COMPOSITION roll, layered
                // on the same already-charged foul the flagrant roll above rides.
                // `defender.recordFoul()` ran BEFORE all branching, which is what
                // holds the foul TOTAL by construction — this only decides what KIND
                // of foul it was, and therefore whether free throws follow.
                //
                // ⚠ ORDERING IS LOAD-BEARING (#039 F): flagrant is rolled FIRST and
                // returns above, so a flagrant common foul is simply a flagrant. The
                // two never compose and there is no "flagrant that awards nothing".
                //
                // ⚠ THE POSSESSION ENDS AND THE BALL DOES NOT COME BACK (#039 C) —
                // no `continue`, no `offensiveRetentions++`. This is DELIBERATELY
                // wrong as basketball: a real common foul is a side inbound and the
                // offense keeps the ball. It is not modelled that way because every
                // returning variant re-enters the loop at ShotSelector and yields a
                // live attempt worth ~0.76 FGA where the stopped shot charged NONE,
                // and FGA is 88.4 against 89.1 real — 0.7 of headroom. That caps a
                // retaining variant at a ~6% share, which moves FTA by less than one
                // attempt: the retention reading and this phase's goal are
                // arithmetically incompatible. FGA wins because it is sourced and
                // already correct. Revisit only if §3.17 buys FGA headroom.
                if (foulResolver.isNonShootingFoul(rng)) {
                    // Emit-then-count (#028 A1): the event goes in the log FIRST, so
                    // the Nth foul — this one — sends its own team to the line.
                    data.addEvent(offTeamId, defTeamId, period, sequence,
                            PlayType.FOUL, COMMON_FOUL_OUTCOME, defender.getPlayerId(),
                            null, defTeamId);
                    sequence++;

                    // In the penalty: 2 bonus FTs, exactly as the rebounding foul
                    // awards them. This is the ~18.5% case that makes the net FT
                    // removed per conversion 1.664 rather than 2.034 (#039 D) — and
                    // it is why the share is 0.43 and not the withdrawn ~0.35.
                    if (data.isInBonus(defTeamId, period, config)) {
                        sequence = awardFreeThrows(data, shooter, offTeamId,
                                offTeamId, defTeamId, period, sequence,
                                SimConfig.FREE_THROWS_PER_FOUL,
                                FreeThrowSource.BONUS, rng);
                    }
                    // Not in the penalty: NO free throws at all — the point of the
                    // phase. The §3.10 not-in-bonus rebounding-foul shape.
                    return sequence;
                }

                // §3.10 (#028 D): a shooting foul's committer is always the
                // DEFENDER, but the column is populated here too so the penalty
                // derivation reads ONE uniform field across all FOUL events.
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.FOUL, "SHOOTING_FOUL", defender.getPlayerId(),
                        null, defTeamId);
                sequence++;

                sequence = awardFreeThrows(data, shooter, offTeamId, offTeamId, defTeamId,
                        period, sequence, shotType.freeThrowsIfFouled(),
                        FreeThrowSource.SHOOTING, rng);
                return sequence;
            }

            // 3. Shot
            shooter.recordFieldGoalAttempt();
            if (shotType == ShotType.THREE) {
                shooter.recordThreePointAttempt();
            }

            // §3.7 (decisions.md #025 A1/B2/F): the block fork is carved off the top
            // of the shot outcome — rolled BEFORE the make/miss contest. A block is a
            // field-goal outcome exactly as a steal is a turnover outcome: a SHOT
            // event with a BLOCKED_* outcome naming the shooter (the victim), the
            // FGA already charged above (a missed FGA, no FGM — it counts against
            // FG%), no assist (F4), and a separate blocker.recordBlock() credit (F2).
            if (shotResolver.isBlocked(shotType, shooter, defender, rng)) {
                defender.recordBlock();
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.SHOT, buildBlockOutcome(shotType), shooter.getPlayerId());
                sequence++;

                // Flat four-way loose-ball recovery (Decision D). Offense-recovered
                // (RECOVERED_OFFENSE/OOB_OFFENSE) re-enters the second-chance loop at
                // ShotSelector (Decision E), reusing the offensive-rebound machinery
                // and respecting the same cap — it SKIPS ReboundResolver (recovery is
                // already decided). Defense-recovered ends the possession.
                BlockRecovery recovery = blockResolver.resolveRecovery(rng);
                if (recovery.offenseRetains() && !capReached) {
                    offensiveRetentions++;
                    continue;
                }
                return sequence;
            }

            double chemistryMultiplier = config.chemistryMakeMultiplier(
                    shooter.getAcumen(), teamOffense, oppTeamDefense);
            boolean made = shotResolver.isMade(shotType, shooter, defender,
                    chemistryMultiplier, rng);
            String outcome = buildShotOutcome(made, shotType);

            if (made) {
                int pts = shotType.getPoints();
                shooter.recordFieldGoalMade(pts);
                if (shotType == ShotType.THREE) {
                    shooter.recordThreePointMade();
                }
                data.addScore(offTeamId, pts);
                // §3.4 (Decision B1): a made FG may be assisted. Roll using the
                // supporting cast's passing, then pick the assister by a weighted
                // passing draw over the other four (shooter excluded).
                PlayerGameState assister = resolveAssist(offense, shooter, rng);
                String assistPlayerId = null;
                if (assister != null) {
                    assister.recordAssist();
                    assistPlayerId = assister.getPlayerId();
                }
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.SHOT, outcome, shooter.getPlayerId(), assistPlayerId);
                sequence++;

                // §3.11 (decisions.md #029 A1/A3/B): the and-1 — a SECOND,
                // post-make foul roll carved BESIDE the assist above. The pre-shot
                // foul branch (and BASE_NO_BASKET_FOUL) is untouched: that one still
                // means "the contact stopped the shot", and this one is the
                // independent "the shot went in anyway" slice, so both rates stay
                // separately tunable (the §3.7 block / §3.10 rebound-foul carve, a
                // third time).
                //
                // §3.12 (#030 A1): the shot-type gate is GONE — EVERY made shot
                // rolls the and-1, including a perimeter and a three. The
                // graduation moved into the rate itself (the shared
                // SimConfig.foulMultiplier table, #030 B), which is the whole point
                // of deleting the binary: one mechanism, not a gate plus a rate.
                // A made three + foul is still exactly ONE FT (#030 C) — see
                // awardAndOne.
                if (foulResolver.isAndOne(shotType, shooter, defender,
                        defensivePressure, rng)) {
                    // §3.14b (#034 A/B/C): the same severity roll, layered on the and-1
                    // foul. Rolled HERE rather than inside awardAndOne because this is
                    // where `offensiveRetentions` and the `continue` are — awardAndOne
                    // returns only a sequence, and its javadoc correctly says it never
                    // forks a possession. Widening it to return a retention flag would
                    // put a possession fork inside a method documented not to have one.
                    //
                    // ⚠ THIS IS THE CASE THAT MOST VISIBLY BREAKS #030 B: the basket
                    // COUNTS (already recorded above — do NOT re-score it), 2 free
                    // throws are awarded, AND the offense keeps the ball. Three scoring
                    // channels on one possession, which no path in this engine has ever
                    // produced. It is the rule. #029 B's "the and-1 never forks the
                    // possession" is superseded FOR THE FLAGRANT CASE ONLY — the
                    // ordinary and-1 below is completely unchanged. Do not "fix" this.
                    //
                    // ⚠ 2 FTs, NOT AND_ONE_FREE_THROWS (1) — replaces, doesn't add.
                    if (foulResolver.isFlagrant(rng)) {
                        defender.recordFoul();
                        sequence = awardFlagrant(data, defender, shooter, offTeamId,
                                offTeamId, defTeamId, period, sequence, rng);
                        if (!capReached) {
                            offensiveRetentions++;
                            continue; // the offense keeps the ball (#034 B)
                        }
                        return sequence; // cap full: FTs awarded, possession ends
                    }
                    sequence = awardAndOne(data, shooter, defender, offTeamId, defTeamId,
                            period, sequence, rng);
                }
                // The ordinary and-1 never forks the possession — the make already
                // ended it (#029 B); the FT is simply tacked on before the ball changes
                // hands. (§3.14b's flagrant and-1 above is the one exception, and it
                // returns/continues before reaching here.)
                return sequence;
            }

            // Missed shot — emit the SHOT event, then resolve the miss (§3.3 + §3.8).
            data.addEvent(offTeamId, defTeamId, period, sequence,
                    PlayType.SHOT, outcome, shooter.getPlayerId());
            sequence++;

            // 4a. Rebounding foul (§3.10, decisions.md #028 A2/C) — carved off the
            // TOP of the rebound phase, exactly as §3.7 carves the block off the top
            // of the shot outcome. On a hit the whistle stopped play, so the board
            // contest below NEVER runs. Two-sided: the side draw inside the resolver
            // decides whether the defense pushed on a box-out or the offense went
            // over the back, which forks the possession both ways (Decision B).
            ReboundFoul reboundFoul = foulResolver.resolveReboundFoul(
                    offense, defense, defensivePressure, rng);
            if (reboundFoul != null) {
                ReboundFoulResult result = resolveReboundFoul(data, reboundFoul,
                        offense, defense, offTeamId, defTeamId, period, sequence,
                        capReached, rng);
                sequence = result.sequence();
                if (result.offenseRetains()) {
                    offensiveRetentions++;
                    continue; // second-chance possession (defense fouled, no bonus)
                }
                return sequence;
            }

            // 4b. Missed-shot outcome (§3.8, decisions.md #026): a single four-way
            // draw owned by MissedShotResolver (which wraps ReboundResolver) — an
            // offensive/defensive rebound OR the ball out of bounds (offense/defense).
            // The cap is passed IN so the resolver never returns an offense-retained
            // outcome once the second-chance loop is full; the loop still owns the
            // continue vs. return fork below (same shape as the §3.7 block recovery).
            MissedShotResolver.Result miss =
                    missedShotResolver.resolve(offense, defense, capReached, rng);
            emitMissedShotEvent(data, miss, offTeamId, defTeamId, period, sequence);
            sequence++;

            if (miss.outcome().offenseRetains()) {
                offensiveRetentions++;
                continue; // second-chance possession (offensive rebound OR OOB-offense)
            }
            return sequence; // possession over (defensive rebound OR OOB-defense)
        }
    }

    /**
     * §3.10: what the rebounding foul did to the possession — the new {@code
     * sequence} and whether the OFFENSE keeps the ball (a second-chance
     * {@code continue}) or the possession is over (a {@code return}).
     */
    record ReboundFoulResult(int sequence, boolean offenseRetains) {}

    /**
     * §3.10 (decisions.md #028 A1/A2/B/D): emit the rebounding foul, then fork the
     * possession by WHO fouled and whether that team is in the bonus.
     *
     * <p><b>Emit-then-count</b> (#028 A1, the crux): the {@code FOUL} event —
     * carrying {@code committingTeamId} — is added to the log FIRST, and only then
     * is {@link GameData#isInBonus} asked. So the Nth foul (the one that reaches
     * {@link SimConfig#bonusFoulsPerPeriod()}) itself sends the fouled team to the
     * line. There is no stored team-foul counter anywhere; the predicate reads the
     * events (#020).
     *
     * <p><b>The fork</b> (#028 B), driven by the side the resolver drew:
     * <ul>
     *   <li><b>Defense committed</b> (box-out push) → the OFFENSE is fouled.
     *       In the bonus: the offense shoots bonus FTs and the possession ends.
     *       Not in the bonus: the offense RETAINS for a second chance (an
     *       offense-retention path like the offensive rebound / §3.7 recovery /
     *       §3.8 OOB-offense) — unless the second-chance cap is already reached,
     *       in which case the possession simply ends.</li>
     *   <li><b>Offense committed</b> (over-the-back) → the DEFENSE is fouled and
     *       the possession ALWAYS ends for the offense (an offensive foul is a
     *       turnover-like loss of the ball). In the bonus the defense shoots its
     *       bonus FTs first.</li>
     * </ul>
     *
     * <p>Bonus free throws reuse {@link #awardFreeThrows} verbatim — the same block
     * the shooting foul uses — so FT/points reconciliation is automatic (#028 B).
     *
     * <p><b>§3.14b (#034 A/C): the flagrant fork is taken FIRST and short-circuits all
     * of the above.</b> On a flagrant the bonus is never consulted (two free throws by
     * rule, in the penalty or not) and the {@code REBOUNDING_FOUL_*} event is replaced
     * by a {@code FLAGRANT_FOUL_*} one — the award <b>replaces</b>, it does not add.
     * The possession fork is unchanged in shape: the defense committing leaves the
     * offense the ball (cap permitting), the offense committing flips it. This is the
     * <b>only</b> site of the three that can be committed by the offense, so it is the
     * only source of the flipping case.
     */
    ReboundFoulResult resolveReboundFoul(GameData data, ReboundFoul foul,
                                         List<PlayerGameState> offense,
                                         List<PlayerGameState> defense,
                                         String offTeamId, String defTeamId,
                                         int period, int sequence,
                                         boolean capReached, RandomGenerator rng) {
        boolean offenseCommitted = foul.side() == ReboundFoul.Side.OFFENSE;
        String committingTeamId = offenseCommitted ? offTeamId : defTeamId;

        // The committing player wears the foul exactly as a shooting-foul defender
        // does — it feeds the per-player foul-out predicate (#023 F).
        foul.committer().recordFoul();

        // §3.14b (#034 A/C/D): the severity roll, layered on the foul just charged.
        // THIS IS THE ONLY TWO-SIDED SITE — the only one that can be committed by the
        // OFFENSE (#028 A2's over-the-back, ~22% as measured), which makes it the sole
        // source of the possession-FLIPPING case. `committingTeamId` is load-bearing
        // here rather than merely uniform (#028 D): the fork depends on which side
        // committed. The FOULED player shoots either way, drawn from the fouled five by
        // the existing foulDrawing-weighted pickFreeThrowShooter (#028 B).
        //
        // ⚠ The bonus is NOT consulted (2 FTs by rule, in the penalty or not), and the
        // ordinary bonus branch below never runs on a hit — replaces, doesn't add.
        if (foulResolver.isFlagrant(rng)) {
            List<PlayerGameState> fouledFive = offenseCommitted ? defense : offense;
            String fouledTeamId = offenseCommitted ? defTeamId : offTeamId;
            PlayerGameState freeThrowShooter = pickFreeThrowShooter(fouledFive, rng);
            sequence = awardFlagrant(data, foul.committer(), freeThrowShooter,
                    fouledTeamId, offTeamId, defTeamId, period, sequence, rng);
            // Defense committed → the offense retains (while the loop has room).
            // Offense committed → the possession flips, exactly as an ordinary
            // offensive foul ends it.
            return new ReboundFoulResult(sequence, !offenseCommitted && !capReached);
        }

        data.addEvent(offTeamId, defTeamId, period, sequence,
                PlayType.FOUL, foul.side().outcome(), foul.committer().getPlayerId(),
                null, committingTeamId);
        sequence++;

        // Emit-then-count: the event above is already in the log, so the Nth foul
        // puts its own committing team in the bonus.
        boolean inBonus = data.isInBonus(committingTeamId, period, config);

        if (inBonus) {
            // The FOULED team shoots. Its FTs score for that team.
            List<PlayerGameState> fouledFive = offenseCommitted ? defense : offense;
            String fouledTeamId = offenseCommitted ? defTeamId : offTeamId;
            PlayerGameState freeThrowShooter = pickFreeThrowShooter(fouledFive, rng);
            sequence = awardFreeThrows(data, freeThrowShooter, fouledTeamId,
                    offTeamId, defTeamId, period, sequence,
                    SimConfig.FREE_THROWS_PER_FOUL, FreeThrowSource.BONUS, rng);
            return new ReboundFoulResult(sequence, false); // possession over either way
        }

        // Under the bonus: no FTs. Only a DEFENSIVE foul leaves the offense the
        // ball, and only while the second-chance loop has room.
        boolean offenseRetains = !offenseCommitted && !capReached;
        return new ReboundFoulResult(sequence, offenseRetains);
    }

    /**
     * §3.14b (decisions.md #034 C/D/E): emit a FLAGRANT foul — the severity sub-roll,
     * the {@code FOUL} event carrying the grade, and the flat <b>two</b> free throws.
     * Shared verbatim by all three foul sites.
     *
     * <p><b>⚠ THE TWO FREE THROWS REPLACE THE UNDERLYING FOUL'S AWARD — THEY DO NOT
     * ADD TO IT (#034 C).</b> A flagrant stopped THREE is <b>2</b> FTs, not 3 and not
     * 5; a flagrant and-1 is <b>2</b>, not 1 + 2. Every caller must therefore skip its
     * ordinary award entirely rather than calling this in addition to it. The count is
     * passed as a <b>literal</b> and deliberately not derived: #030 C derives it from
     * {@link ShotType} and #029 B fixes it per situation, so a builder has two live
     * precedents for deriving it — and deriving it is precisely how the count would
     * silently graduate. A stopped flagrant three consequently awards <i>fewer</i> free
     * throws than the ordinary foul it upgraded: correct by rule, counter-intuitive,
     * and not to be "fixed".
     *
     * <p><b>The severity sub-roll is taken here</b> (#034 E) so all three sites share
     * one definition. On a flagrant-2 the committer also takes {@link
     * PlayerGameState#recordFlagrantTwo()}, which ejects him immediately through the
     * derived {@link PlayerGameState#isEjectedForFlagrant()} — the third cause behind
     * {@code RotationState}'s single {@code isDisqualified(...)} filter, not a fourth
     * removal path (#031 H).
     *
     * <p><b>The committer's personal foul is charged by the CALLER, not here</b>, and
     * that is deliberate: two of the three sites have already charged it by the time
     * they roll the flagrant (the stopped-shot defender, the rebounding-foul committer),
     * so charging it here would double it. A flagrant IS a personal foul (#034 I) — it
     * goes through the ordinary {@code recordFoul()} and feeds the six-foul limit,
     * {@code foulTroubleLevel()} and the period bonus tally, with <b>none</b> of
     * §3.14a's counter split (#032 E). {@code flagrantTwos} counts ejection causes, not
     * fouls.
     *
     * <p><b>The bonus is NOT consulted</b> — two free throws by rule, in the penalty or
     * not (the #029 B shape). The flagrant nevertheless counts toward the period tally
     * for the NEXT foul, automatically, because {@link GameData#isInBonus} excludes
     * only {@link GameData#TECHNICAL_FOUL_OUTCOME}.
     *
     * <p><b>This method does NOT fork the possession</b> — it returns only a sequence.
     * The retention fork lives at each call site, where {@code offensiveRetentions} and
     * the loop's {@code continue} are (#034 B).
     *
     * @param committer      the player who committed it — already charged {@code
     *                       recordFoul()} by the caller
     * @param freeThrowShooter the player who WAS FOULED, who shoots (#034 D) — never
     *                       {@link #pickTechnicalFreeThrowShooter}, whose premise is
     *                       that nobody was fouled (#032 G)
     * @param shootingTeamId the team the free throws score for — the fouled player's
     *                       team, which at the rebounding site may be the DEFENSE
     * @return the next free sequence number
     */
    int awardFlagrant(GameData data, PlayerGameState committer,
                      PlayerGameState freeThrowShooter, String shootingTeamId,
                      String offTeamId, String defTeamId, int period, int sequence,
                      RandomGenerator rng) {
        boolean flagrantTwo = foulResolver.isFlagrantTwo(rng);
        if (flagrantTwo) {
            committer.recordFlagrantTwo();
        }
        data.addEvent(offTeamId, defTeamId, period, sequence, PlayType.FOUL,
                flagrantTwo ? FLAGRANT_FOUL_2_OUTCOME : FLAGRANT_FOUL_1_OUTCOME,
                committer.getPlayerId(), null, committer.getTeamId());
        sequence++;

        return awardFreeThrows(data, freeThrowShooter, shootingTeamId,
                offTeamId, defTeamId, period, sequence,
                SimConfig.FLAGRANT_FREE_THROWS, FreeThrowSource.FLAGRANT, rng);
    }

    /**
     * §3.11 (decisions.md #029 A1/B/D): emit the and-1 — the {@code FOUL} event and
     * the single free throw that ride a basket that already counted.
     *
     * <p>The made field goal is <b>not</b> re-rolled or re-scored: the {@code if
     * (made)} block above has already recorded the points, the FGM, and (possibly)
     * the assist, and the and-1 only ADDS one attempt from the line (#029 B). That
     * is also why nothing here forks the possession — a made basket already ended
     * it.
     *
     * <p>The foul is one-sided (a shooting foul is always on the DEFENDER), so
     * {@code committingTeamId} is simply {@code defTeamId} — #028 D's column reused
     * with no new plumbing. Like every other foul it charges the defender (feeding
     * the foul-out predicate, #023 F) and counts toward that team's period tally
     * (§3.10 A1). It does <b>not</b> consult the bonus: an and-1 is always exactly
     * one free throw by rule, in the penalty or not (#029 B).
     *
     * <p><b>§3.12 (#030 C): the count stays {@code AND_ONE_FREE_THROWS = 1} for
     * EVERY shot type — a made three plus a foul is 3 points and ONE free throw,
     * not three.</b> §3.12 made the STOPPED-shot count graduate by shot type
     * ({@link ShotType#freeThrowsIfFouled}); this count deliberately does not
     * follow it. Making them graduate in parallel is a one-character mistake the
     * {@code count} parameter makes easy, and it would silently inflate scoring on
     * top of §3.12's real lift — hence the explicit test guarding it.
     *
     * @return the next free sequence number
     */
    int awardAndOne(GameData data, PlayerGameState shooter, PlayerGameState defender,
                    String offTeamId, String defTeamId, int period, int sequence,
                    RandomGenerator rng) {
        defender.recordFoul();
        data.addEvent(offTeamId, defTeamId, period, sequence,
                PlayType.FOUL, "AND_ONE", defender.getPlayerId(), null, defTeamId);
        sequence++;

        return awardFreeThrows(data, shooter, offTeamId, offTeamId, defTeamId,
                period, sequence, SimConfig.AND_ONE_FREE_THROWS,
                FreeThrowSource.AND_ONE, rng);
    }

    /**
     * §3.14a (decisions.md #032 D/E/G): emit the technical foul and its single free
     * throw. Called between possessions, from {@link #simulate}, once per team that
     * drew one on this rotation check.
     *
     * <p><b>The possession is untouched.</b> Unlike every other foul path in this
     * class there is no fork here — no retention, no switch, no second-chance
     * {@code continue}. A technical is assessed on a team, play resumes from the
     * point of interruption, and because the roll fires between possessions there is
     * literally nothing to fork (#032 D). {@code offTeamId}/{@code defTeamId} are the
     * upcoming possession's orientation, carried only so the event log keeps its
     * uniform shape.
     *
     * <p><b>{@code committingTeamId} is populated</b> like every other {@code FOUL}
     * event (#028 D), so the column stays uniform — even though {@link
     * GameData#isInBonus} deliberately EXCLUDES this outcome from the penalty tally
     * (#032 E).
     *
     * <p>The committer already wore the technical inside the rotation step (that is
     * where the counter lives, feeding the derived {@link
     * PlayerGameState#isEjected()}); this method does not re-charge it.
     *
     * @param committer      the player who committed it (drawn on the floor by
     *                       {@code foulProne}, #032 C)
     * @param committingTeam the team he plays for
     * @param shootingTeam   the OTHER team, which shoots the free throw
     * @return the next free sequence number
     */
    int awardTechnicalFoul(GameData data, PlayerGameState committer,
                           TeamContext committingTeam, TeamContext shootingTeam,
                           String offTeamId, String defTeamId,
                           int period, int sequence, RandomGenerator rng) {
        data.addEvent(offTeamId, defTeamId, period, sequence,
                PlayType.FOUL, GameData.TECHNICAL_FOUL_OUTCOME, committer.getPlayerId(),
                null, committingTeam.teamId());
        sequence++;

        PlayerGameState shooter = pickTechnicalFreeThrowShooter(shootingTeam.onFloor());
        return awardFreeThrows(data, shooter, shootingTeam.teamId(),
                offTeamId, defTeamId, period, sequence,
                SimConfig.TECHNICAL_FREE_THROWS, FreeThrowSource.TECHNICAL, rng);
    }

    /**
     * §3.14a (decisions.md #032 G): who shoots a technical free throw — the
     * <b>highest {@code freeThrows} skill among the on-floor five, deterministically</b>.
     *
     * <p><b>This is a SECOND rule beside {@link #pickFreeThrowShooter}, which stays
     * untouched, and the two must never be merged.</b> That one weights by {@code
     * foulDrawing} because it models who <i>gets fouled</i> off the ball (#028 B).
     * <b>Nobody is fouled on a technical</b> — the offended team simply <i>chooses</i>
     * its best shooter. Merging them would get one of the two rules wrong: bonus FTs
     * would go to the best shooter (wrong — the player who was fouled shoots), or
     * technical FTs would be weighted by {@code foulDrawing} (wrong — nobody was
     * fouled).
     *
     * <p><b>Two consequences that look like bugs and are not.</b> First, this
     * consumes <b>no RNG</b> — a small determinism win, and why it takes no {@link
     * RandomGenerator}. Second, and far more visible: <b>the same player shoots
     * essentially every technical free throw for his team all game</b>, changing only
     * when the lineup changes. That reads oddly in a box score and looks like a stuck
     * selection. It is exactly what the real rule produces — teams do send their best
     * shooter every single time — and it is intended. <b>Do not "fix" it into a
     * weighted draw.</b>
     *
     * <p>Ties break on list order (the on-floor five's stable ordering), keeping the
     * pick reproducible.
     */
    PlayerGameState pickTechnicalFreeThrowShooter(List<PlayerGameState> players) {
        PlayerGameState best = players.get(0);
        for (PlayerGameState p : players) {
            if (p.getFreeThrows() > best.getFreeThrows()) {
                best = p;
            }
        }
        return best;
    }

    /**
     * §3.10 (decisions.md #028 B): pick who shoots the bonus free throws from the
     * fouled five — weighted by {@code foulDrawing}, so the players who live at the
     * line are the ones fouled off the ball. (A shooting foul has no such choice:
     * the shooter shoots.) Mirrors the skill-weighted draw pattern used throughout.
     *
     * <p><b>§3.14a deliberately did NOT touch this</b> and added {@link
     * #pickTechnicalFreeThrowShooter} beside it instead (#032 G) — two different real
     * rules, two functions.
     */
    PlayerGameState pickFreeThrowShooter(List<PlayerGameState> players, RandomGenerator rng) {
        double totalWeight = 0;
        for (PlayerGameState p : players) {
            totalWeight += p.getFoulDrawing();
        }
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (PlayerGameState p : players) {
            cumulative += p.getFoulDrawing();
            if (roll < cumulative) return p;
        }
        return players.get(players.size() - 1);
    }

    /**
     * The free-throw award block, shared by all THREE free-throw situations — the
     * §3.2 shooting foul, §3.10's bonus trip, and §3.11's and-1 (decisions.md #028 B
     * — "reuse the existing FT block verbatim", so no new FT machinery exists and
     * FT/points reconciliation is automatic). {@code shootingTeamId} is the team the
     * made FTs score for — the offense on a shooting foul or an and-1, but the
     * FOULED team on a rebounding foul, which may be the DEFENSE (an over-the-back
     * sends the defending team to the line while the offense's possession ends).
     *
     * <p>{@code offTeamId}/{@code defTeamId} stay the possession's orientation on
     * the emitted events (the event log always records who was on offense), which
     * is why the scoring team is passed separately.
     *
     * <p>§3.11 (#029 B/D) made the two things that differ per situation into
     * parameters rather than constants:
     * <ul>
     *   <li>{@code count} — how many attempts. A shooting foul and a bonus trip pass
     *       {@link SimConfig#FREE_THROWS_PER_FOUL} (2, unchanged); an and-1 passes
     *       {@link SimConfig#AND_ONE_FREE_THROWS} (1, always — an and-1 never
     *       consults the bonus, that is the rule). This is also the seam §3.12 will
     *       reuse to award 3 on a fouled three.</li>
     *   <li>{@code source} — stamped onto every emitted event's {@code outcome}, so
     *       a free throw says what sent the shooter to the line without a backward
     *       join to the preceding {@code FOUL} (#029 D, retiring #028 D's accepted
     *       ambiguity now that there are three sources).</li>
     * </ul>
     *
     * @return the next free sequence number
     */
    int awardFreeThrows(GameData data, PlayerGameState shooter, String shootingTeamId,
                        String offTeamId, String defTeamId, int period, int sequence,
                        int count, FreeThrowSource source, RandomGenerator rng) {
        for (int ft = 0; ft < count; ft++) {
            shooter.recordFreeThrowAttempt();
            boolean made = foulResolver.isFreeThrowMade(shooter, rng);
            if (made) {
                shooter.recordFreeThrowMade();
                data.addScore(shootingTeamId, 1);
            }
            data.addEvent(offTeamId, defTeamId, period, sequence,
                    PlayType.FREE_THROW, source.outcome(made), shooter.getPlayerId());
            sequence++;
        }
        return sequence;
    }

    /**
     * §3.8 (decisions.md #026 E): emit the missed-shot outcome event and credit the
     * rebounder ONLY on the two rebound outcomes. The two OOB outcomes reuse the
     * {@link PlayType#REBOUND} play type with OUT_OF_BOUNDS_* outcomes but credit NO
     * rebounder — so they are excluded from the rebound reconciliation invariant
     * (which exact-matches OFFENSIVE / DEFENSIVE), and record no box-score rebound.
     */
    void emitMissedShotEvent(GameData data, MissedShotResolver.Result miss,
                             String offTeamId, String defTeamId, int period, int sequence) {
        MissedShotOutcome outcome = miss.outcome();
        String rebounderId = null;
        if (outcome.creditsRebounder()) {
            PlayerGameState rebounder = miss.rebounder();
            if (outcome == MissedShotOutcome.OFFENSIVE_REBOUND) {
                rebounder.recordOffensiveRebound();
            } else {
                rebounder.recordDefensiveRebound();
            }
            rebounderId = rebounder.getPlayerId();
        }
        data.addEvent(offTeamId, defTeamId, period, sequence,
                PlayType.REBOUND, missedShotOutcomeString(outcome), rebounderId);
    }

    /**
     * §3.8 (step 1): the {@code outcome} string for each missed-shot outcome. The
     * two rebound outcomes keep the established OFFENSIVE / DEFENSIVE strings; the
     * two OOB outcomes use OUT_OF_BOUNDS_OFFENSE / OUT_OF_BOUNDS_DEFENSE (a distinct
     * outcome on the existing REBOUND PlayType — no schema change, no new PlayType,
     * mirroring #025 F's reuse discipline). Sail-out and tipped-OOB share one
     * outcome each (offense/defense): nothing consumes the distinction and the
     * last-touch team is already implied by the suffix (#026 E — no fabricated detail).
     */
    String missedShotOutcomeString(MissedShotOutcome outcome) {
        return switch (outcome) {
            case OFFENSIVE_REBOUND -> "OFFENSIVE";
            case DEFENSIVE_REBOUND -> "DEFENSIVE";
            case OOB_OFFENSE -> "OUT_OF_BOUNDS_OFFENSE";
            case OOB_DEFENSE -> "OUT_OF_BOUNDS_DEFENSE";
        };
    }

    String buildShotOutcome(boolean made, ShotType shotType) {
        String prefix = made ? "MADE" : "MISSED";
        return switch (shotType) {
            case DRIVE -> prefix + "_2PT_DRIVE";
            case PERIMETER -> prefix + "_2PT_PERIMETER";
            case POST -> prefix + "_2PT_POST";
            case THREE -> prefix + "_3PT";
        };
    }

    /**
     * §3.7 (decisions.md #025 F1): the BLOCKED_* outcome string, carrying the shot
     * type in the same shape as {@link #buildShotOutcome}'s MADE / MISSED strings
     * (so the play-by-play filter is {@code SHOT WHERE outcome LIKE 'BLOCKED%'}).
     */
    String buildBlockOutcome(ShotType shotType) {
        return switch (shotType) {
            case DRIVE -> "BLOCKED_2PT_DRIVE";
            case PERIMETER -> "BLOCKED_2PT_PERIMETER";
            case POST -> "BLOCKED_2PT_POST";
            case THREE -> "BLOCKED_3PT";
        };
    }

    /**
     * §3.4 (Decision B1): decide whether a made FG is assisted and by whom. Rolls
     * the assist probability from the supporting cast's (the four non-shooter
     * offensive players') average passing; if assisted, picks the assister by a
     * weighted passing draw over those four (mirrors {@link ShotSelector#pickShooter}).
     * Returns {@code null} when unassisted. Not every make is assisted.
     */
    PlayerGameState resolveAssist(List<PlayerGameState> offense, PlayerGameState shooter,
                                  RandomGenerator rng) {
        List<PlayerGameState> supportingCast = new ArrayList<>();
        double passingSum = 0;
        for (PlayerGameState p : offense) {
            if (p != shooter) {
                supportingCast.add(p);
                passingSum += p.getPassing();
            }
        }
        if (supportingCast.isEmpty()) {
            return null;
        }
        double avgPassing = passingSum / supportingCast.size();
        if (rng.nextDouble() >= config.assistProbability(avgPassing)) {
            return null;
        }
        // Weighted passing draw over the supporting cast.
        double roll = rng.nextDouble() * passingSum;
        double cumulative = 0;
        for (PlayerGameState p : supportingCast) {
            cumulative += p.getPassing();
            if (roll < cumulative) return p;
        }
        return supportingCast.get(supportingCast.size() - 1);
    }

    private double averageTeamOffense(List<PlayerGameState> players) {
        return players.stream()
                .mapToDouble(PlayerGameState::getTeamOffense)
                .average()
                .orElse(SimConfig.SCALE_AVG);
    }

    private double averageTeamDefense(List<PlayerGameState> players) {
        return players.stream()
                .mapToDouble(PlayerGameState::getTeamDefense)
                .average()
                .orElse(SimConfig.SCALE_AVG);
    }
}
