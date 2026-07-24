package software.daveturner.gametime.sim;

import software.daveturner.gametime.entity.PlayType;

import java.util.*;

public class GameData {

    private final List<EventRecord> events = new ArrayList<>();
    private int homeScore;
    private int awayScore;
    private int periods;
    private String homeTeamId;
    private String awayTeamId;

    public void addEvent(String offTeamId, String defTeamId, int period,
                         int sequence, PlayType playType, String outcome,
                         String primaryPlayerId) {
        addEvent(offTeamId, defTeamId, period, sequence, playType, outcome,
                primaryPlayerId, null);
    }

    public void addEvent(String offTeamId, String defTeamId, int period,
                         int sequence, PlayType playType, String outcome,
                         String primaryPlayerId, String assistPlayerId) {
        addEvent(offTeamId, defTeamId, period, sequence, playType, outcome,
                primaryPlayerId, assistPlayerId, null);
    }

    /**
     * §3.10 (decisions.md #028 D): the full overload carrying {@code
     * committingTeamId} — the team that COMMITTED the event (set on FOUL events
     * only; see {@link EventRecord}).
     */
    public void addEvent(String offTeamId, String defTeamId, int period,
                         int sequence, PlayType playType, String outcome,
                         String primaryPlayerId, String assistPlayerId,
                         String committingTeamId) {
        events.add(new EventRecord(offTeamId, defTeamId, period, sequence,
                playType, outcome, primaryPlayerId, assistPlayerId, committingTeamId));
    }

    public void addScore(String teamId, int points) {
        if (teamId.equals(homeTeamId)) {
            homeScore += points;
        } else {
            awayScore += points;
        }
    }

    public List<EventRecord> getEvents() { return events; }

    /**
     * §3.10 (decisions.md #028 A1 — the crux): is {@code teamId} in the BONUS
     * (penalty) for period {@code period}? Computed on demand from the FOUL event
     * log — {@code count(FOUL events with committingTeamId == teamId in this
     * period) >= SimConfig.BONUS_FOULS_PER_PERIOD} — with <b>no stored teamFouls
     * counter and no reset logic</b>. The events already hold the fact (#020), so
     * a counter could only ever disagree with them; this is exactly the derived-
     * predicate discipline #023 F applied to foul-OUTS, carried to the team level.
     *
     * <p><b>Emit-then-count</b> (#028 A1): callers {@code addEvent} the current
     * FOUL <i>first</i>, then ask — so the Nth foul (the one that reaches the
     * threshold) itself awards the bonus free throws. "In the bonus" means the
     * count HAS reached the limit, not exceeded it.
     *
     * <p>Both foul kinds count toward one unified tally (#028 A1): {@code
     * SHOOTING_FOUL} and the two-sided {@code REBOUNDING_FOUL_*} alike, grouped by
     * the {@code committingTeamId} field they all carry.
     */
    public boolean isInBonus(String teamId, int period) {
        return periodFoulCount(teamId, period) >= SimConfig.BONUS_FOULS_PER_PERIOD;
    }

    /**
     * §3.10: how many fouls {@code teamId} has committed in {@code period}, read
     * from the FOUL event log (the substrate behind {@link #isInBonus}). Exposed
     * for the calibration harness's team-fouls line and for tests.
     */
    public int periodFoulCount(String teamId, int period) {
        int count = 0;
        for (EventRecord e : events) {
            if (e.playType() == PlayType.FOUL
                    && e.period() == period
                    && teamId.equals(e.committingTeamId())) {
                count++;
            }
        }
        return count;
    }
    public int getHomeScore() { return homeScore; }
    public int getAwayScore() { return awayScore; }
    public int getPeriods() { return periods; }
    public void setPeriods(int periods) { this.periods = periods; }
    public String getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(String homeTeamId) { this.homeTeamId = homeTeamId; }
    public String getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(String awayTeamId) { this.awayTeamId = awayTeamId; }

    /**
     * One simulated event. {@code committingTeamId} (§3.10, #028 D) is the team
     * that COMMITTED the event — populated on FOUL events only (uniformly for
     * {@code SHOOTING_FOUL} = the defender's team and for the two-sided
     * {@code REBOUNDING_FOUL_*}), null elsewhere. It exists because a rebounding
     * foul can be committed by the offense, so the committer is not recoverable
     * from the offense/defense orientation the way a shooting foul's is.
     */
    public record EventRecord(String offTeamId, String defTeamId, int period,
                               int sequence, PlayType playType, String outcome,
                               String primaryPlayerId, String assistPlayerId,
                               String committingTeamId) {}
}
