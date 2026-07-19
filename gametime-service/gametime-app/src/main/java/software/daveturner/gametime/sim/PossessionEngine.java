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
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.FOUL, "SHOOTING_FOUL", defender.getPlayerId());
                sequence++;

                for (int ft = 0; ft < SimConfig.FREE_THROWS_PER_FOUL; ft++) {
                    shooter.recordFreeThrowAttempt();
                    boolean made = foulResolver.isFreeThrowMade(shooter, rng);
                    if (made) {
                        shooter.recordFreeThrowMade();
                        data.addScore(offTeamId, 1);
                    }
                    data.addEvent(offTeamId, defTeamId, period, sequence,
                            PlayType.FREE_THROW, made ? "MADE" : "MISSED", shooter.getPlayerId());
                    sequence++;
                }
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

            // 4. Missed-shot outcome (§3.8, decisions.md #026): a single four-way
            // draw owned by MissedShotResolver (which wraps ReboundResolver) — an
            // offensive/defensive rebound OR the ball out of bounds (offense/defense).
            // The cap is passed IN so the resolver never returns an offense-retained
            // outcome once the second-chance loop is full; the loop still owns the
            // continue vs. return fork below (same shape as the §3.7 block recovery).
            boolean capReached =
                    offensiveRebounds >= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION;
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
