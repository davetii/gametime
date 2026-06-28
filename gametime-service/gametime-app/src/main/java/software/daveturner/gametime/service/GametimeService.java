package software.daveturner.gametime.service;

import software.daveturner.gametime.model.*;

import java.util.*;

public interface GametimeService {
    List<Team> getLeague();
    Optional<Player> getPlayer(String playerId);
    Optional<Team> getTeam(String teamId);
    Player createPlayer(Player player);
    Player updatePlayer(Player player);
    void addPlayerToTeam(String teamId, String playerId);
    List<PlayerTransaction> getPlayerHistory(String playerId);
    Team setLineup(String teamId, LineupRequest request);
    void removePlayerFromTeam(String teamId, String playerId);
}
