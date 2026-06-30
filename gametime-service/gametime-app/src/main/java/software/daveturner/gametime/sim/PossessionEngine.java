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
    private final ReboundResolver reboundResolver;
    private final SimConfig config;

    public PossessionEngine(ShotSelector shotSelector, ShotResolver shotResolver,
                            TurnoverResolver turnoverResolver, FoulResolver foulResolver,
                            ReboundResolver reboundResolver, SimConfig config) {
        this.shotSelector = shotSelector;
        this.shotResolver = shotResolver;
        this.turnoverResolver = turnoverResolver;
        this.foulResolver = foulResolver;
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
        List<PlayerGameState> offense = offenseCtx.players();
        List<PlayerGameState> defense = defenseCtx.players();
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
                boolean stolen = turnoverResolver.isStolen(rng);
                String outcome;
                if (stolen) {
                    PlayerGameState stealer = turnoverResolver.pickStealer(defense, rng);
                    stealer.recordSteal();
                    outcome = "STOLEN";
                } else {
                    outcome = "LOST_BALL";
                }
                shooter.recordTurnover();
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.TURNOVER, outcome, shooter.getPlayerId());
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

            // Missed shot — emit the SHOT event, then resolve the rebound (§3.3).
            data.addEvent(offTeamId, defTeamId, period, sequence,
                    PlayType.SHOT, outcome, shooter.getPlayerId());
            sequence++;

            // 4. Rebound
            PlayerGameState offRebounder = reboundResolver.pickOffensiveRebounder(offense, rng);
            PlayerGameState defRebounder = reboundResolver.pickDefensiveRebounder(defense, rng);
            boolean capReached = offensiveRebounds >= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION;
            boolean offensiveRebound = !capReached
                    && reboundResolver.isOffensiveRebound(offRebounder, defRebounder, rng);

            if (offensiveRebound) {
                offRebounder.recordOffensiveRebound();
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.REBOUND, "OFFENSIVE", offRebounder.getPlayerId());
                sequence++;
                offensiveRebounds++;
                // Offense retains the ball — loop for a second-chance possession.
            } else {
                defRebounder.recordDefensiveRebound();
                data.addEvent(offTeamId, defTeamId, period, sequence,
                        PlayType.REBOUND, "DEFENSIVE", defRebounder.getPlayerId());
                return sequence + 1;
            }
        }
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
