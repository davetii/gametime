package software.daveturner.gametime.sim;

public class SimResult {

    private final String gameId;
    private final String homeTeamId;
    private final String awayTeamId;
    private final int homeScore;
    private final int awayScore;
    private final int periods;
    private final int totalEvents;

    public SimResult(String gameId, String homeTeamId, String awayTeamId,
                     int homeScore, int awayScore, int periods, int totalEvents) {
        this.gameId = gameId;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.periods = periods;
        this.totalEvents = totalEvents;
    }

    public String getGameId() { return gameId; }
    public String getHomeTeamId() { return homeTeamId; }
    public String getAwayTeamId() { return awayTeamId; }
    public int getHomeScore() { return homeScore; }
    public int getAwayScore() { return awayScore; }
    public int getPeriods() { return periods; }
    public int getTotalEvents() { return totalEvents; }
}
