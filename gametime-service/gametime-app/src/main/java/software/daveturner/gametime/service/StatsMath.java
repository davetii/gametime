package software.daveturner.gametime.service;

import software.daveturner.gametime.entity.BoxScoreEntity;
import software.daveturner.gametime.entity.GameEntity;
import software.daveturner.gametime.model.ShootingPercentages;
import software.daveturner.gametime.model.StatAverages;
import software.daveturner.gametime.model.StatTotals;
import software.daveturner.gametime.model.TeamGameStats;
import software.daveturner.gametime.sim.SimConfig;

import java.util.Collection;
import java.util.List;

/** The stat formulas (decisions.md #045 H). Pure: no Spring, no repositories. */
public final class StatsMath {

    private StatsMath() {
    }

    /** Summed box-score columns over some set of rows, plus how many rows and starts. */
    public record Sums(long games, long started, long points,
                       long offensiveRebounds, long defensiveRebounds, long assists,
                       long steals, long blocks, long turnovers, long fouls,
                       long technicalFouls, long minutes,
                       long fieldGoalsAttempted, long fieldGoalsMade,
                       long threePointersAttempted, long threePointersMade,
                       long freeThrowsAttempted, long freeThrowsMade) {

        public long rebounds() {
            return offensiveRebounds + defensiveRebounds;
        }

        /** From a JPQL row laid out as {@link StatsServiceImpl#SUMS}, starting at {@code offset}. */
        static Sums fromRow(Object[] row, int offset) {
            long[] v = new long[18];
            for (int i = 0; i < v.length; i++) {
                Object o = row[offset + i];
                v[i] = o == null ? 0 : ((Number) o).longValue();
            }
            return new Sums(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                    v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17]);
        }

        public static Sums of(Collection<BoxScoreEntity> rows) {
            long[] v = new long[18];
            for (BoxScoreEntity b : rows) {
                v[0]++;
                v[1] += Boolean.TRUE.equals(b.getStarted()) ? 1 : 0;
                v[2] += n(b.getPoints());
                v[3] += n(b.getOffensiveRebounds());
                v[4] += n(b.getDefensiveRebounds());
                v[5] += n(b.getAssists());
                v[6] += n(b.getSteals());
                v[7] += n(b.getBlocks());
                v[8] += n(b.getTurnovers());
                v[9] += n(b.getFouls());
                v[10] += n(b.getTechnicalFouls());
                v[11] += n(b.getMinutes());
                v[12] += n(b.getFieldGoalsAttempted());
                v[13] += n(b.getFieldGoalsMade());
                v[14] += n(b.getThreePointersAttempted());
                v[15] += n(b.getThreePointersMade());
                v[16] += n(b.getFreeThrowsAttempted());
                v[17] += n(b.getFreeThrowsMade());
            }
            return new Sums(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                    v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17]);
        }

        private static int n(Integer i) {
            return i == null ? 0 : i;
        }
    }

    /** One team's line for one game, from that game's box-score rows. */
    public static TeamGameStats teamGameStats(GameEntity game, List<BoxScoreEntity> rows, boolean home) {
        String teamId = home ? game.getHomeTeamId() : game.getAwayTeamId();
        Integer possessions = home ? game.getHomePossessions() : game.getAwayPossessions();
        Sums own = Sums.of(rows.stream().filter(b -> teamId.equals(b.getTeamId())).toList());
        Sums opponents = Sums.of(rows.stream().filter(b -> !teamId.equals(b.getTeamId())).toList());
        Long poss = possessions == null ? null : possessions.longValue();
        long minutes = game.getPeriods() == null ? 0 : gameMinutes(1, game.getPeriods());
        TeamGameStats s = new TeamGameStats();
        s.setTeamId(teamId);
        s.setTotals(totals(own));
        s.setPossessions(possessions);
        s.setPace(pace(poss, minutes));
        s.setOffensiveRating(rating(own.points(), poss));
        s.setDefensiveRating(rating(opponents.points(), poss));
        s.setNetRating(net(s.getOffensiveRating(), s.getDefensiveRating()));
        return s;
    }

    public static StatTotals totals(Sums s) {
        StatTotals t = new StatTotals();
        t.setPoints((int) s.points());
        t.setRebounds((int) s.rebounds());
        t.setOffensiveRebounds((int) s.offensiveRebounds());
        t.setDefensiveRebounds((int) s.defensiveRebounds());
        t.setAssists((int) s.assists());
        t.setSteals((int) s.steals());
        t.setBlocks((int) s.blocks());
        t.setTurnovers((int) s.turnovers());
        t.setFouls((int) s.fouls());
        t.setTechnicalFouls((int) s.technicalFouls());
        t.setMinutes((int) s.minutes());
        t.setFieldGoalsAttempted((int) s.fieldGoalsAttempted());
        t.setFieldGoalsMade((int) s.fieldGoalsMade());
        t.setThreePointersAttempted((int) s.threePointersAttempted());
        t.setThreePointersMade((int) s.threePointersMade());
        t.setFreeThrowsAttempted((int) s.freeThrowsAttempted());
        t.setFreeThrowsMade((int) s.freeThrowsMade());
        return t;
    }

    /** Per-game averages over {@code games}; null when there are no games. */
    public static StatAverages perGame(Sums s, long games) {
        if (games == 0) {
            return null;
        }
        double g = games;
        StatAverages a = new StatAverages();
        a.setPoints(s.points() / g);
        a.setRebounds(s.rebounds() / g);
        a.setOffensiveRebounds(s.offensiveRebounds() / g);
        a.setDefensiveRebounds(s.defensiveRebounds() / g);
        a.setAssists(s.assists() / g);
        a.setSteals(s.steals() / g);
        a.setBlocks(s.blocks() / g);
        a.setTurnovers(s.turnovers() / g);
        a.setFouls(s.fouls() / g);
        a.setTechnicalFouls(s.technicalFouls() / g);
        a.setMinutes(s.minutes() / g);
        a.setFieldGoalsAttempted(s.fieldGoalsAttempted() / g);
        a.setFieldGoalsMade(s.fieldGoalsMade() / g);
        a.setThreePointersAttempted(s.threePointersAttempted() / g);
        a.setThreePointersMade(s.threePointersMade() / g);
        a.setFreeThrowsAttempted(s.freeThrowsAttempted() / g);
        a.setFreeThrowsMade(s.freeThrowsMade() / g);
        return a;
    }

    public static ShootingPercentages shooting(Sums s) {
        ShootingPercentages p = new ShootingPercentages();
        p.setFieldGoalPct(fieldGoalPct(s));
        p.setThreePointPct(threePointPct(s));
        p.setFreeThrowPct(freeThrowPct(s));
        p.setTrueShootingPct(trueShootingPct(s));
        p.setEffectiveFieldGoalPct(effectiveFieldGoalPct(s));
        return p;
    }

    public static Double fieldGoalPct(Sums s) {
        return ratio(s.fieldGoalsMade(), s.fieldGoalsAttempted());
    }

    public static Double threePointPct(Sums s) {
        return ratio(s.threePointersMade(), s.threePointersAttempted());
    }

    public static Double freeThrowPct(Sums s) {
        return ratio(s.freeThrowsMade(), s.freeThrowsAttempted());
    }

    public static Double trueShootingPct(Sums s) {
        double attempts = s.fieldGoalsAttempted() + 0.44 * s.freeThrowsAttempted();
        return attempts == 0 ? null : s.points() / (2 * attempts);
    }

    public static Double effectiveFieldGoalPct(Sums s) {
        return s.fieldGoalsAttempted() == 0 ? null
                : (s.fieldGoalsMade() + 0.5 * s.threePointersMade()) / s.fieldGoalsAttempted();
    }

    /** Regulation plus overtime minutes for {@code games} games totalling {@code periods} periods. */
    public static long gameMinutes(long games, long periods) {
        long regulation = games * SimConfig.PERIODS;
        return regulation * SimConfig.MINUTES_PER_PERIOD
                + Math.max(0, periods - regulation) * SimConfig.OT_MINUTES;
    }

    /** Possessions per 48 minutes. */
    public static Double pace(Long possessions, long gameMinutes) {
        if (possessions == null || gameMinutes == 0) {
            return null;
        }
        return possessions * (double) (SimConfig.PERIODS * SimConfig.MINUTES_PER_PERIOD) / gameMinutes;
    }

    /** Points per 100 possessions. */
    public static Double rating(long points, Long possessions) {
        return possessions == null || possessions == 0 ? null : points * 100.0 / possessions;
    }

    public static Double net(Double offensive, Double defensive) {
        return offensive == null || defensive == null ? null : offensive - defensive;
    }

    private static Double ratio(long made, long attempted) {
        return attempted == 0 ? null : (double) made / attempted;
    }
}
