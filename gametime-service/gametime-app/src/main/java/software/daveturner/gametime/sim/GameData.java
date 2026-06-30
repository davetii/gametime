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
        events.add(new EventRecord(offTeamId, defTeamId, period, sequence,
                playType, outcome, primaryPlayerId));
    }

    public void addScore(String teamId, int points) {
        if (teamId.equals(homeTeamId)) {
            homeScore += points;
        } else {
            awayScore += points;
        }
    }

    public List<EventRecord> getEvents() { return events; }
    public int getHomeScore() { return homeScore; }
    public int getAwayScore() { return awayScore; }
    public int getPeriods() { return periods; }
    public void setPeriods(int periods) { this.periods = periods; }
    public String getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(String homeTeamId) { this.homeTeamId = homeTeamId; }
    public String getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(String awayTeamId) { this.awayTeamId = awayTeamId; }

    public record EventRecord(String offTeamId, String defTeamId, int period,
                               int sequence, PlayType playType, String outcome,
                               String primaryPlayerId) {}
}
