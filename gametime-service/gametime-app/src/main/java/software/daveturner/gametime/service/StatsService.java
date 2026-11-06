package software.daveturner.gametime.service;

import software.daveturner.gametime.model.*;

import java.util.List;

/** Aggregates over box_score ⋈ game (decisions.md #045 C); never reads game_event. */
public interface StatsService {

    PlayerStats getPlayerStats(String playerId);

    List<GameLogEntry> getPlayerGameLog(String playerId);

    TeamStats getTeamStats(String teamId);

    LeaderBoard getLeaders(LeaderCategory category, Integer limit);

    GameHighs getGameHighs(GameHighCategory category, Integer limit);
}
