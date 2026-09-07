package software.daveturner.gametime.sim;

import software.daveturner.gametime.entity.PlayType;

import java.util.*;

public class GameData {

    /**
     * §3.14a (decisions.md #032 D/E): the {@code outcome} string for a technical foul, on
     * the existing {@link PlayType#FOUL} — a technical is a KIND of foul, so no new
     * {@code PlayType} (the #025 F / #026 E / #028 D reuse discipline).
     *
     * <p>⚠ It lives HERE rather than on {@link PossessionEngine} (which emits it) because
     * this class is the one that must RECOGNISE it: it is the single outcome excluded
     * from the penalty tally below, and a literal on both sides of that agreement is
     * exactly how the two would drift apart.
     */
    public static final String TECHNICAL_FOUL_OUTCOME = "TECHNICAL_FOUL";

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
     * §3.10 (decisions.md #028 D): the overload carrying {@code committingTeamId} —
     * the team that COMMITTED the event (set on FOUL events only; see
     * {@link EventRecord}).
     */
    public void addEvent(String offTeamId, String defTeamId, int period,
                         int sequence, PlayType playType, String outcome,
                         String primaryPlayerId, String assistPlayerId,
                         String committingTeamId) {
        addEvent(offTeamId, defTeamId, period, sequence, playType, outcome,
                primaryPlayerId, assistPlayerId, committingTeamId, null);
    }

    /**
     * §3.18 (decisions.md #041 A): the full overload, carrying {@code
     * opponentPlayerId} — the COUNTERPARTY, the player on the other side of the
     * play from {@code primaryPlayerId} and therefore ALWAYS on the opposite team.
     * A teammate never goes here; an assister rides {@code assistPlayerId} (#041 D).
     * See {@link EventRecord} for the three sites that populate it.
     */
    public void addEvent(String offTeamId, String defTeamId, int period,
                         int sequence, PlayType playType, String outcome,
                         String primaryPlayerId, String assistPlayerId,
                         String committingTeamId, String opponentPlayerId) {
        events.add(new EventRecord(offTeamId, defTeamId, period, sequence,
                playType, outcome, primaryPlayerId, assistPlayerId, committingTeamId,
                opponentPlayerId));
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
     * §3.10 (decisions.md #028 A1 — the crux): is {@code teamId} in the BONUS (penalty)
     * for period {@code period}? Derived on demand from the FOUL event log, with <b>no
     * stored teamFouls counter and no reset logic</b> — the events already hold the fact
     * (#020), so a counter could only ever disagree with them (#023 F's derived-predicate
     * discipline, at the team level).
     *
     * <p><b>⚠ EMIT-THEN-COUNT</b> (#028 A1): callers {@code addEvent} the current FOUL
     * <i>first</i>, then ask — so the Nth foul, the one reaching the threshold, itself
     * awards the bonus free throws. "In the bonus" means the count HAS reached the limit,
     * not exceeded it.
     *
     * <p>All <b>personal</b> foul kinds count toward one unified tally (#028 A1),
     * grouped by the {@code committingTeamId} they carry. The one exclusion is a {@link
     * #TECHNICAL_FOUL_OUTCOME}, which does not put a team in the bonus (#032 E).
     *
     * <p><b>⚠ EVERY NEW FOUL TYPE MUST CONSCIOUSLY DECIDE WHETHER IT COUNTS</b>, and say
     * so in {@link #countsTowardBonus}. A flagrant, for instance, DOES count — it is a
     * personal foul and also feeds the six-foul limit.
     */
    public boolean isInBonus(String teamId, int period, SimConfig config) {
        return periodFoulCount(teamId, period) >= config.bonusFoulsPerPeriod();
    }

    /**
     * §3.10: how many fouls {@code teamId} has committed in {@code period}, read
     * from the FOUL event log (the substrate behind {@link #isInBonus}). Exposed
     * for the calibration harness's team-fouls line and for tests.
     *
     * <p>§3.14a (#032 E): counts PERSONAL fouls only — technicals are excluded, see
     * {@link #isInBonus}.
     */
    public int periodFoulCount(String teamId, int period) {
        int count = 0;
        for (EventRecord e : events) {
            if (e.playType() == PlayType.FOUL
                    && e.period() == period
                    && teamId.equals(e.committingTeamId())
                    && countsTowardBonus(e)) {
                count++;
            }
        }
        return count;
    }

    /**
     * §3.14a (#032 E): does this {@code FOUL} event count toward its team's period
     * penalty tally? Everything does except a technical.
     */
    private static boolean countsTowardBonus(EventRecord e) {
        return !TECHNICAL_FOUL_OUTCOME.equals(e.outcome());
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
     *
     * <p>{@code opponentPlayerId} (§3.18, #041 A) is the COUNTERPARTY — the player
     * on the other side of the play from {@code primaryPlayerId}, and therefore
     * ALWAYS on the opposite team. Populated on {@code TURNOVER}/{@code STOLEN}
     * (the stealer), {@code SHOT}/{@code BLOCKED_*} (the blocker) and {@code
     * FOUL}/{@code SHOOTING_FOUL} (the fouled shooter); null elsewhere — including,
     * deliberately, on {@code OFFENSIVE_FOUL}, whose drawer is not modelled. A
     * teammate never goes here: an assister rides {@code assistPlayerId} (#041 D).
     */
    public record EventRecord(String offTeamId, String defTeamId, int period,
                               int sequence, PlayType playType, String outcome,
                               String primaryPlayerId, String assistPlayerId,
                               String committingTeamId, String opponentPlayerId) {}
}
