package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.entity.PlayType;

import java.util.*;
import java.util.random.RandomGenerator;

@Component
public class PossessionEngine {

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
                    : SimConfig.OT_POSSESSIONS_PER_PERIOD;

            for (int poss = 0; poss < possessionsThisPeriod * 2; poss++) {
                boolean homeOnOffense = (poss % 2 == 0);
                TeamContext offense = homeOnOffense ? home : away;
                TeamContext defense = homeOnOffense ? away : home;

                // §3.5 (Decisions C/F): the between-possession substitution step.
                // Both teams are on the floor for every possession (one offense,
                // one defense), so both rotations advance once per possession —
                // drain the on-floor five's energy, recover the benched, force off
                // fouled-out players, run fatigue subs. Deterministic, RNG-free,
                // emits no events. Ordering (home then away, then resolve) is fixed
                // so the seed-pinned stream stays reproducible.
                home.rotation().advancePossession();
                away.rotation().advancePossession();

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
        // to MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION. Each attempt runs the full
        // flow (turnover → foul → shot); a missed shot rolls a rebound (§3.3).
        int offensiveRebounds = 0;
        while (true) {
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
                return sequence + 1;
            }

            // 2. Foul check (drive/post only)
            if (foulResolver.isFoul(shotType, shooter, defender, defensivePressure, rng)) {
                defender.recordFoul();
                // §3.10 (#028 D): a shooting foul's committer is always the
                // DEFENDER, but the column is populated here too so the penalty
                // derivation reads ONE uniform field across all FOUL events.
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.FOUL, "SHOOTING_FOUL", defender.getPlayerId(),
                        null, defTeamId);
                sequence++;

                sequence = awardFreeThrows(data, shooter, offTeamId, offTeamId, defTeamId,
                        period, sequence, rng);
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
                boolean capReached =
                        offensiveRebounds >= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION;
                if (recovery.offenseRetains() && !capReached) {
                    offensiveRebounds++;
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
                return sequence + 1;
            }

            // Missed shot — emit the SHOT event, then resolve the miss (§3.3 + §3.8).
            data.addEvent(offTeamId, defTeamId, period, sequence,
                    PlayType.SHOT, outcome, shooter.getPlayerId());
            sequence++;

            boolean capReached =
                    offensiveRebounds >= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION;

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
                    offensiveRebounds++;
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
                offensiveRebounds++;
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
     * {@link SimConfig#BONUS_FOULS_PER_PERIOD}) itself sends the fouled team to the
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
        data.addEvent(offTeamId, defTeamId, period, sequence,
                PlayType.FOUL, foul.side().outcome(), foul.committer().getPlayerId(),
                null, committingTeamId);
        sequence++;

        // Emit-then-count: the event above is already in the log, so the Nth foul
        // puts its own committing team in the bonus.
        boolean inBonus = data.isInBonus(committingTeamId, period);

        if (inBonus) {
            // The FOULED team shoots. Its FTs score for that team.
            List<PlayerGameState> fouledFive = offenseCommitted ? defense : offense;
            String fouledTeamId = offenseCommitted ? defTeamId : offTeamId;
            PlayerGameState freeThrowShooter = pickFreeThrowShooter(fouledFive, rng);
            sequence = awardFreeThrows(data, freeThrowShooter, fouledTeamId,
                    offTeamId, defTeamId, period, sequence, rng);
            return new ReboundFoulResult(sequence, false); // possession over either way
        }

        // Under the bonus: no FTs. Only a DEFENSIVE foul leaves the offense the
        // ball, and only while the second-chance loop has room.
        boolean offenseRetains = !offenseCommitted && !capReached;
        return new ReboundFoulResult(sequence, offenseRetains);
    }

    /**
     * §3.10 (decisions.md #028 B): pick who shoots the bonus free throws from the
     * fouled five — weighted by {@code foulDrawing}, so the players who live at the
     * line are the ones fouled off the ball. (A shooting foul has no such choice:
     * the shooter shoots.) Mirrors the skill-weighted draw pattern used throughout.
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
     * The free-throw award block, shared by the §3.2 shooting foul and §3.10's
     * bonus free throws (decisions.md #028 B — "reuse the existing FT block
     * verbatim", so no new FT machinery exists and FT/points reconciliation is
     * automatic). {@code shootingTeamId} is the team the made FTs score for — the
     * offense on a shooting foul, but the FOULED team on a rebounding foul, which
     * may be the DEFENSE (an over-the-back sends the defending team to the line
     * while the offense's possession ends).
     *
     * <p>{@code offTeamId}/{@code defTeamId} stay the possession's orientation on
     * the emitted events (the event log always records who was on offense), which
     * is why the scoring team is passed separately.
     *
     * @return the next free sequence number
     */
    int awardFreeThrows(GameData data, PlayerGameState shooter, String shootingTeamId,
                        String offTeamId, String defTeamId, int period, int sequence,
                        RandomGenerator rng) {
        for (int ft = 0; ft < SimConfig.FREE_THROWS_PER_FOUL; ft++) {
            shooter.recordFreeThrowAttempt();
            boolean made = foulResolver.isFreeThrowMade(shooter, rng);
            if (made) {
                shooter.recordFreeThrowMade();
                data.addScore(shootingTeamId, 1);
            }
            data.addEvent(offTeamId, defTeamId, period, sequence,
                    PlayType.FREE_THROW, made ? "MADE" : "MISSED", shooter.getPlayerId());
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
