package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.entity.PlayType;

import java.util.*;
import java.util.random.RandomGenerator;

@Component
public class PossessionEngine {

    // Flagrants count toward the bonus, so they need no entry on GameData (#034).
    static final String FLAGRANT_FOUL_1_OUTCOME = "FLAGRANT_FOUL_1";
    static final String FLAGRANT_FOUL_2_OUTCOME = "FLAGRANT_FOUL_2";

    // ⚠ Rows simulated before §3.17 say COMMON_FOUL; there was no migration (#040 N).
    static final String NON_SHOOTING_FOUL_OUTCOME = "NON_SHOOTING_FOUL";

    private final ShotSelector shotSelector;
    private final ShotResolver shotResolver;
    private final TurnoverResolver turnoverResolver;
    private final FoulResolver foulResolver;
    private final BlockResolver blockResolver;
    private final MissedShotResolver missedShotResolver;
    // Block-recovery rebounder selection only; board contests live in MissedShotResolver (#043 E).
    private final ReboundResolver reboundResolver;
    private final SimConfig config;

    public PossessionEngine(ShotSelector shotSelector, ShotResolver shotResolver,
                            TurnoverResolver turnoverResolver, FoulResolver foulResolver,
                            BlockResolver blockResolver, MissedShotResolver missedShotResolver,
                            ReboundResolver reboundResolver,
                            SimConfig config) {
        this.shotSelector = shotSelector;
        this.shotResolver = shotResolver;
        this.turnoverResolver = turnoverResolver;
        this.foulResolver = foulResolver;
        this.blockResolver = blockResolver;
        this.missedShotResolver = missedShotResolver;
        this.reboundResolver = reboundResolver;
        this.config = config;
    }

    public GameData simulate(TeamContext home, TeamContext away,
                             int possessionsPerPeriod, RandomGenerator rng) {
        GameData data = new GameData();
        data.setHomeTeamId(home.teamId());
        data.setAwayTeamId(away.teamId());
        int sequence = 1;
        int period = 1;

        // Pace scales the possession count, blended across both coaches (§3.4 A).
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

                // Subs, fatigue and the technical roll, between possessions (#023, #031, #032).
                // ⚠ Home, away, then the possession: fixed so the seeded RNG stream reproduces.
                PlayerGameState homeTechnical = home.rotation().advancePossession(rng);
                PlayerGameState awayTechnical = away.rotation().advancePossession(rng);
                Possession possession = Possession.of(data, offense, defense,
                        period, sequence, rng);

                if (homeTechnical != null) {
                    awardTechnicalFoul(possession, homeTechnical, home, away);
                }
                if (awayTechnical != null) {
                    awardTechnicalFoul(possession, awayTechnical, away, home);
                }

                data.addPossession(offense.teamId());
                resolvePossession(possession, offense.modifiers(), defense.modifiers());
                sequence = possession.sequence();
            }

            if (period >= SimConfig.PERIODS && data.getHomeScore() != data.getAwayScore()) {
                break;
            }
            period++;
        }

        data.setPeriods(period);
        return data;
    }

    void resolvePossession(Possession p, CoachModifiers offenseModifiers,
                           CoachModifiers defenseModifiers) {
        Matchup matchup = new Matchup(offenseModifiers.shotMixLean(),
                defenseModifiers.defensivePressure(),
                averageTeamOffense(p.offense()), averageTeamDefense(p.defense()));

        // Second-chance loop (#034 B). The cap also shapes the board draws inside the resolvers.
        int offensiveRetentions = 0;
        PlayerGameState rebounder = null; // the putback candidate (#044 C)
        while (true) {
            boolean capReached =
                    offensiveRetentions >= config.maxOffensiveRetentionsPerPossession();
            AttemptResult result = resolveAttempt(p, matchup, rebounder, capReached);
            if (!result.offenseRetains() || capReached) {
                return;
            }
            offensiveRetentions++;
            rebounder = result.rebounder();
        }
    }

    /** Per-possession inputs to every attempt. */
    record Matchup(double shotMixLean, double defensivePressure,
                   double teamOffense, double oppTeamDefense) {}

    /**
     * How one attempt ended: the possession is over, or the offense keeps the ball,
     * naming the player who secured it when a board identified one (#044 C).
     */
    record AttemptResult(boolean offenseRetains, PlayerGameState rebounder) {
        static final AttemptResult END = new AttemptResult(false, null);

        static AttemptResult retain(PlayerGameState rebounder) {
            return new AttemptResult(true, rebounder);
        }
    }

    private AttemptResult resolveAttempt(Possession p, Matchup matchup,
                                         PlayerGameState rebounder, boolean capReached) {
        RandomGenerator rng = p.rng();
        PlayerGameState shooter = shotSelector.pickShooter(p.offense(), rebounder, rng);
        ShotType shotType = shotSelector.pickShotType(shooter, matchup.shotMixLean(), rng);
        PlayerGameState defender = shotSelector.pickDefender(p.defense(), rng);

        if (turnoverResolver.isTurnover(shooter, p.defense(), matchup.defensivePressure(), rng)) {
            resolveTurnover(p, matchup, shooter);
            return AttemptResult.END;
        }
        if (foulResolver.isFoul(shotType, shooter, defender, matchup.defensivePressure(), rng)) {
            return resolveStoppedShotFoul(p, shooter, foulCommitter(p, defender), shotType, capReached);
        }

        shooter.recordFieldGoalAttempt();
        if (shotType == ShotType.THREE) {
            shooter.recordThreePointAttempt();
        }
        if (shotResolver.isBlocked(shotType, shooter, defender, rng)) {
            return resolveBlock(p, shooter, defender, shotType);
        }

        double chemistryMultiplier = config.chemistryMakeMultiplier(
                shooter.getAcumen(), matchup.teamOffense(), matchup.oppTeamDefense());
        boolean made = shotResolver.isMade(shotType, shooter, defender,
                chemistryMultiplier, rng);
        if (made) {
            return resolveMadeShot(p, matchup, shooter, defender, shotType,
                    shooter == rebounder, capReached);
        }
        return resolveMissedShot(p, matchup, shooter, shotType, capReached);
    }

    // #027
    private void resolveTurnover(Possession p, Matchup matchup, PlayerGameState shooter) {
        TurnoverCause cause = turnoverResolver.pickCause(
                shooter, matchup.teamOffense(), matchup.defensivePressure(), p.rng());
        // ⚠ Hoist the declaration, never the pickStealer call: it moves the RNG stream (#041).
        String stealerId = null;
        if (cause == TurnoverCause.STOLEN) {
            PlayerGameState stealer = turnoverResolver.pickStealer(p.defense(), p.rng());
            stealer.recordSteal();
            stealerId = stealer.getPlayerId();
        }
        shooter.recordTurnover();
        p.emit(PlayType.TURNOVER, cause.outcome(), shooter.getPlayerId(),
                null, null, stealerId);

        // A charge is also a personal foul: a second event for the same play (#039 G).
        if (cause == TurnoverCause.OFFENSIVE_FOUL) {
            shooter.recordFoul();
            p.emit(PlayType.FOUL, cause.outcome(), shooter.getPlayerId(),
                    null, p.offTeamId());
        }
    }

    // A foul that stopped the shot — no FGA is charged (game-events.md).
    private AttemptResult resolveStoppedShotFoul(Possession p, PlayerGameState shooter,
                                                 PlayerGameState defender, ShotType shotType,
                                                 boolean capReached) {
        defender.recordFoul();

        // Flagrant: 2 FTs replace the shot's FTs and the offense keeps the ball (#034 B/C).
        if (foulResolver.isFlagrant(p.rng())) {
            awardFlagrant(p, defender, shooter, shooter.getPlayerId(), p.offTeamId());
            return AttemptResult.retain(null); // no board ran (#044 C)
        }

        // Non-shooting foul: bonus FTs or nothing, and the possession ends (#039 C).
        if (foulResolver.isNonShootingFoul(p.rng())) {
            p.emit(PlayType.FOUL, NON_SHOOTING_FOUL_OUTCOME, defender.getPlayerId(),
                    null, p.defTeamId(), shooter.getPlayerId());
            if (p.data().isInBonus(p.defTeamId(), p.period(), config)) {
                return awardLiveFreeThrows(p, shooter, p.offTeamId(),
                        SimConfig.FREE_THROWS_PER_FOUL, FreeThrowSource.BONUS, capReached);
            }
            return AttemptResult.END;
        }

        // Shooting foul: the last FT is live and can be rebounded (#043 C).
        p.emit(PlayType.FOUL, "SHOOTING_FOUL", defender.getPlayerId(),
                null, p.defTeamId(), shooter.getPlayerId());
        return awardLiveFreeThrows(p, shooter, p.offTeamId(),
                shotType.freeThrowsIfFouled(), FreeThrowSource.SHOOTING, capReached);
    }

    // Rolled before make/miss; recovery is a flat side roll, not the board contest (#025, #043 E).
    private AttemptResult resolveBlock(Possession p, PlayerGameState shooter,
                                       PlayerGameState defender, ShotType shotType) {
        defender.recordBlock();
        p.emit(PlayType.SHOT, buildBlockOutcome(shotType), shooter.getPlayerId(),
                null, null, defender.getPlayerId());

        BlockRecovery recovery = blockResolver.resolveRecovery(p.rng());
        PlayerGameState recoverer = emitBlockRecoveryEvent(p, recovery);
        return recovery.offenseRetains()
                ? AttemptResult.retain(recoverer) // null on OOB_OFFENSE
                : AttemptResult.END;
    }

    private AttemptResult resolveMadeShot(Possession p, Matchup matchup,
                                          PlayerGameState shooter, PlayerGameState defender,
                                          ShotType shotType, boolean putback,
                                          boolean capReached) {
        int pts = shotType.getPoints();
        shooter.recordFieldGoalMade(pts);
        if (shotType == ShotType.THREE) {
            shooter.recordThreePointMade();
        }
        p.data().addScore(p.offTeamId(), pts);
        // A putback is assisted at half the ordinary chance (#044 E).
        PlayerGameState assister = resolveAssist(p.offense(), shooter, putback, p.rng());
        String assistPlayerId = null;
        if (assister != null) {
            assister.recordAssist();
            assistPlayerId = assister.getPlayerId();
        }
        p.emit(PlayType.SHOT, buildShotOutcome(true, shotType), shooter.getPlayerId(),
                assistPlayerId);

        // And-1 (#029, #030)
        if (!foulResolver.isAndOne(shotType, shooter, defender,
                matchup.defensivePressure(), p.rng())) {
            return AttemptResult.END;
        }
        PlayerGameState committer = foulCommitter(p, defender);
        // Flagrant and-1: the basket stands, 2 FTs, and the offense keeps the ball (#034 B/C).
        if (foulResolver.isFlagrant(p.rng())) {
            committer.recordFoul();
            awardFlagrant(p, committer, shooter, shooter.getPlayerId(), p.offTeamId());
            return AttemptResult.retain(null); // no board ran (#044 C)
        }
        // The and-1 FT is live and can be rebounded (#043 C).
        return awardAndOne(p, shooter, committer, capReached);
    }

    // A defender in foul trouble may hand a foul already rolled to a teammate (#047).
    PlayerGameState foulCommitter(Possession p, PlayerGameState defender) {
        double keep = defender.foulKeepProbability();
        if (keep >= 1.0 || p.rng().nextDouble() < keep) {
            return defender;
        }
        List<PlayerGameState> teammates = new ArrayList<>(p.defense());
        teammates.remove(defender);
        return teammates.isEmpty() ? defender : foulResolver.pickCommitter(teammates, p.rng());
    }

    private AttemptResult resolveMissedShot(Possession p, Matchup matchup,
                                            PlayerGameState shooter, ShotType shotType,
                                            boolean capReached) {
        p.emit(PlayType.SHOT, buildShotOutcome(false, shotType), shooter.getPlayerId());

        // Rebounding foul, carved off the top of the rebound phase (#028)
        ReboundFoul reboundFoul = foulResolver.resolveReboundFoul(
                p.offense(), p.defense(), matchup.defensivePressure(), p.rng());
        if (reboundFoul != null) {
            return resolveReboundFoul(p, reboundFoul, capReached);
        }

        // Rebound or out of bounds; the cap forces an ending outcome (#026)
        MissedShotResolver.Result miss =
                missedShotResolver.resolve(p.offense(), p.defense(), capReached, p.rng());
        emitMissedShotEvent(p, miss);
        return miss.outcome().offenseRetains()
                ? AttemptResult.retain(miss.rebounder()) // null on OOB_OFFENSE
                : AttemptResult.END;
    }

    /** The only foul either side can commit, so it can flip the possession (#028, #034). */
    AttemptResult resolveReboundFoul(Possession p, ReboundFoul foul, boolean capReached) {
        List<PlayerGameState> offense = p.offense();
        List<PlayerGameState> defense = p.defense();
        String offTeamId = p.offTeamId();
        String defTeamId = p.defTeamId();
        boolean offenseCommitted = foul.side() == ReboundFoul.Side.OFFENSE;
        String committingTeamId = offenseCommitted ? offTeamId : defTeamId;

        foul.committer().recordFoul();

        // Flagrant: 2 FTs by rule, the bonus is not consulted (#034 C).
        if (foulResolver.isFlagrant(p.rng())) {
            List<PlayerGameState> fouledFive = offenseCommitted ? defense : offense;
            String fouledTeamId = offenseCommitted ? defTeamId : offTeamId;
            PlayerGameState freeThrowShooter = pickFreeThrowShooter(fouledFive, p.rng());
            // ⚠ Null counterparty: the FT shooter is a draw, not the player who was pushed.
            awardFlagrant(p, foul.committer(), freeThrowShooter, null, fouledTeamId);
            return new AttemptResult(!offenseCommitted, null);
        }

        // ⚠ Null counterparty by contract (game-events.md).
        p.emit(PlayType.FOUL, foul.side().outcome(), foul.committer().getPlayerId(),
                null, committingTeamId);

        // Emit-then-count: this foul can put its own team in the bonus (#028 A1).
        boolean inBonus = p.data().isInBonus(committingTeamId, p.period(), config);

        if (inBonus) {
            List<PlayerGameState> fouledFive = offenseCommitted ? defense : offense;
            String fouledTeamId = offenseCommitted ? defTeamId : offTeamId;
            PlayerGameState freeThrowShooter = pickFreeThrowShooter(fouledFive, p.rng());
            // The fouled team may be the defense; the board still counts for the offense (#043 C).
            return awardLiveFreeThrows(p, freeThrowShooter, fouledTeamId,
                    SimConfig.FREE_THROWS_PER_FOUL, FreeThrowSource.BONUS, capReached);
        }

        // Under the bonus: only a defensive foul leaves the offense the ball.
        return new AttemptResult(!offenseCommitted, null);
    }

    /**
     * A flagrant foul and its 2 free throws, which REPLACE the underlying foul's award (#034 C/E).
     * ⚠ The caller already charged the committer's personal foul; charging it here doubles it.
     *
     * @param fouledPlayerId the counterparty, or null where nobody in particular was fouled;
     *                       deliberately not derived from {@code freeThrowShooter} (game-events.md)
     * @param shootingTeamId the team the free throws score for; at the rebounding site, maybe
     *                       the defense
     */
    void awardFlagrant(Possession p, PlayerGameState committer,
                       PlayerGameState freeThrowShooter, String fouledPlayerId,
                       String shootingTeamId) {
        boolean flagrantTwo = foulResolver.isFlagrantTwo(p.rng());
        if (flagrantTwo) {
            committer.recordFlagrantTwo();
        }
        p.emit(PlayType.FOUL,
                flagrantTwo ? FLAGRANT_FOUL_2_OUTCOME : FLAGRANT_FOUL_1_OUTCOME,
                committer.getPlayerId(), null, committer.getTeamId(), fouledPlayerId);

        // ⚠ A literal count, never derived from the shot type (#034 C).
        awardFreeThrows(p, freeThrowShooter, shootingTeamId,
                SimConfig.FLAGRANT_FREE_THROWS, FreeThrowSource.FLAGRANT);
    }

    /** The and-1 foul and its single live free throw; the basket already counted (#029, #043 C). */
    AttemptResult awardAndOne(Possession p, PlayerGameState shooter,
                                PlayerGameState defender, boolean capReached) {
        defender.recordFoul();
        p.emit(PlayType.FOUL, "AND_ONE", defender.getPlayerId(), null, p.defTeamId(),
                shooter.getPlayerId());

        // ⚠ One free throw for every shot type, a three included (#030 C).
        return awardLiveFreeThrows(p, shooter, p.offTeamId(), SimConfig.AND_ONE_FREE_THROWS,
                FreeThrowSource.AND_ONE, capReached);
    }

    /** A technical, between possessions: one free throw, possession unchanged (#032 D/G). */
    void awardTechnicalFoul(Possession p, PlayerGameState committer,
                            TeamContext committingTeam, TeamContext shootingTeam) {
        // ⚠ Null counterparty: nobody was fouled.
        p.emit(PlayType.FOUL, GameData.TECHNICAL_FOUL_OUTCOME, committer.getPlayerId(),
                null, committingTeam.teamId());

        PlayerGameState shooter = pickTechnicalFreeThrowShooter(shootingTeam.onFloor());
        awardFreeThrows(p, shooter, shootingTeam.teamId(),
                SimConfig.TECHNICAL_FREE_THROWS, FreeThrowSource.TECHNICAL);
    }

    /**
     * The best free-throw shooter on the floor, deterministically (#032 G). ⚠ Never merge with
     * {@link #pickFreeThrowShooter}: nobody is fouled on a technical, so the team chooses.
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

    /** Who in the fouled five shoots the bonus free throws, weighted by foulDrawing (#028 B). */
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
     * The shared free-throw trip, used by every source (#028 B, #029 D).
     * ⚠ No possession logic belongs here; a live miss's rebound is in
     * {@link #awardLiveFreeThrows} (#043 H).
     */
    void awardFreeThrows(Possession p, PlayerGameState shooter, String shootingTeamId,
                         int count, FreeThrowSource source) {
        for (int ft = 0; ft < count; ft++) {
            shooter.recordFreeThrowAttempt();
            boolean made = foulResolver.isFreeThrowMade(shooter, p.rng());
            if (made) {
                shooter.recordFreeThrowMade();
                p.data().addScore(shootingTeamId, 1);
            }
            p.emit(PlayType.FREE_THROW, source.outcome(made), shooter.getPlayerId());
        }
    }

    /**
     * The REBOUND event for a blocked shot's loose ball (#043 E). The side is already decided by
     * the flat roll and is not re-contested; the two OOB outcomes credit nobody.
     *
     * @return the offensive recoverer (the putback candidate), or null
     */
    PlayerGameState emitBlockRecoveryEvent(Possession p, BlockRecovery recovery) {
        PlayerGameState rebounder = switch (recovery) {
            case RECOVERED_OFFENSE -> reboundResolver.pickOffensiveRebounder(p.offense(), p.rng());
            case RECOVERED_DEFENSE -> reboundResolver.pickDefensiveRebounder(p.defense(), p.rng());
            case OOB_OFFENSE, OOB_DEFENSE -> null; // nobody secured it
        };
        String rebounderId = null;
        if (rebounder != null) {
            if (recovery == BlockRecovery.RECOVERED_OFFENSE) {
                rebounder.recordOffensiveRebound();
            } else {
                rebounder.recordDefensiveRebound();
            }
            rebounderId = rebounder.getPlayerId();
        }
        String outcome = switch (recovery) {
            case RECOVERED_OFFENSE -> "OFFENSIVE";
            case RECOVERED_DEFENSE -> "DEFENSIVE";
            case OOB_OFFENSE -> "OUT_OF_BOUNDS_OFFENSE";
            case OOB_DEFENSE -> "OUT_OF_BOUNDS_DEFENSE";
        };
        p.emit(PlayType.REBOUND, outcome, rebounderId);
        return recovery == BlockRecovery.RECOVERED_OFFENSE ? rebounder : null;
    }

    /**
     * A free-throw trip whose LAST attempt is live: a miss is rebounded at a reduced offensive
     * base (#043 C/D/H). Shooting, bonus and and-1 trips only; never flagrants or technicals.
     */
    AttemptResult awardLiveFreeThrows(Possession p, PlayerGameState shooter,
                                        String shootingTeamId, int count,
                                        FreeThrowSource source, boolean capReached) {
        // ⚠ Only the last attempt is live; the earlier ones are dead balls.
        if (count > 1) {
            awardFreeThrows(p, shooter, shootingTeamId, count - 1, source);
        }
        int madeBefore = shooter.getFreeThrowsMade();
        awardFreeThrows(p, shooter, shootingTeamId, 1, source);
        if (shooter.getFreeThrowsMade() > madeBefore) {
            return AttemptResult.END; // made: dead ball, no board
        }

        MissedShotResolver.Result miss = missedShotResolver.resolve(p.offense(), p.defense(),
                capReached, config.baseOffensiveRebound() * SimConfig.FREE_THROW_REBOUND_LEAN,
                p.rng());
        emitMissedShotEvent(p, miss);
        PlayerGameState rebounder =
                miss.outcome() == MissedShotOutcome.OFFENSIVE_REBOUND ? miss.rebounder() : null;
        return new AttemptResult(miss.outcome().offenseRetains(), rebounder);
    }

    /** The missed-shot REBOUND event; the two OOB outcomes credit nobody (#026 E). */
    void emitMissedShotEvent(Possession p, MissedShotResolver.Result miss) {
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
        p.emit(PlayType.REBOUND, missedShotOutcomeString(outcome), rebounderId);
    }

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

    String buildBlockOutcome(ShotType shotType) {
        return switch (shotType) {
            case DRIVE -> "BLOCKED_2PT_DRIVE";
            case PERIMETER -> "BLOCKED_2PT_PERIMETER";
            case POST -> "BLOCKED_2PT_POST";
            case THREE -> "BLOCKED_3PT";
        };
    }

    /**
     * Whether a make is assisted and by whom, from the other four's passing (§3.4 B1). A putback
     * is assisted at half the chance (#044 E); ⚠ the draw count is unchanged (#044 I).
     */
    PlayerGameState resolveAssist(List<PlayerGameState> offense, PlayerGameState shooter,
                                  boolean putback, RandomGenerator rng) {
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
        double assistChance = config.assistProbability(avgPassing);
        if (putback) {
            assistChance *= SimConfig.OFFENSIVE_REBOUNDER_ASSIST_LEAN;
        }
        if (rng.nextDouble() >= assistChance) {
            return null;
        }
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
