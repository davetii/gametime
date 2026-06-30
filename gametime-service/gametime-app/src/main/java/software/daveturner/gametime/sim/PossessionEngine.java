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
    private final SimConfig config;

    public PossessionEngine(ShotSelector shotSelector, ShotResolver shotResolver,
                            TurnoverResolver turnoverResolver, FoulResolver foulResolver,
                            SimConfig config) {
        this.shotSelector = shotSelector;
        this.shotResolver = shotResolver;
        this.turnoverResolver = turnoverResolver;
        this.foulResolver = foulResolver;
        this.config = config;
    }

    public GameData simulate(List<PlayerGameState> homePlayers,
                             List<PlayerGameState> awayPlayers,
                             String homeTeamId, String awayTeamId,
                             int possessionsPerPeriod, RandomGenerator rng) {
        GameData data = new GameData();
        data.setHomeTeamId(homeTeamId);
        data.setAwayTeamId(awayTeamId);
        int sequence = 1;
        int period = 1;

        while (true) {
            int possessionsThisPeriod = (period <= SimConfig.PERIODS)
                    ? possessionsPerPeriod
                    : SimConfig.OT_POSSESSIONS_PER_PERIOD;

            for (int poss = 0; poss < possessionsThisPeriod * 2; poss++) {
                boolean homeOnOffense = (poss % 2 == 0);
                List<PlayerGameState> offense = homeOnOffense ? homePlayers : awayPlayers;
                List<PlayerGameState> defense = homeOnOffense ? awayPlayers : homePlayers;
                String offTeamId = homeOnOffense ? homeTeamId : awayTeamId;
                String defTeamId = homeOnOffense ? awayTeamId : homeTeamId;

                sequence = resolvePossession(data, offense, defense,
                        offTeamId, defTeamId, period, sequence, rng);
            }

            if (period >= SimConfig.PERIODS && data.getHomeScore() != data.getAwayScore()) {
                break;
            }
            period++;
        }

        data.setPeriods(period);
        return data;
    }

    int resolvePossession(GameData data,
                          List<PlayerGameState> offense, List<PlayerGameState> defense,
                          String offTeamId, String defTeamId,
                          int period, int sequence, RandomGenerator rng) {

        PlayerGameState shooter = shotSelector.pickShooter(offense, rng);
        ShotType shotType = shotSelector.pickShotType(shooter, rng);
        PlayerGameState defender = shotSelector.pickDefender(defense, rng);

        // 1. Turnover check
        if (turnoverResolver.isTurnover(shooter, defense, rng)) {
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
        if (foulResolver.isFoul(shotType, shooter, defender, rng)) {
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

        boolean made = shotResolver.isMade(shotType, shooter, defender, rng);
        String outcome = buildShotOutcome(made, shotType);

        if (made) {
            int pts = shotType.getPoints();
            shooter.recordFieldGoalMade(pts);
            if (shotType == ShotType.THREE) {
                shooter.recordThreePointMade();
            }
            data.addScore(offTeamId, pts);
        }

        data.addEvent(offTeamId, defTeamId, period, sequence,
                PlayType.SHOT, outcome, shooter.getPlayerId());
        return sequence + 1;
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
}
