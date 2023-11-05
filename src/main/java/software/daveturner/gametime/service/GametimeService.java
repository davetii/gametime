package software.daveturner.gametime.service;

import software.daveturner.gametime.model.*;

import java.util.*;

public interface GametimeService {
    List<Team> getLeague();
    /*
    Optional <Conference> getConference(String conferenceId);
    Optional<Team> getTeam(String teamId);
    Optional<Player> getPlayer(String playerId);

     */
}
