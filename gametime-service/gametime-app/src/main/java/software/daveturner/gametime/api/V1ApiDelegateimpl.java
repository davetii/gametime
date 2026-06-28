package software.daveturner.gametime.api;

import jakarta.persistence.*;
import org.springframework.cache.annotation.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import software.daveturner.gametime.exception.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.service.*;

import java.util.*;

@RestController
public class V1ApiDelegateimpl implements V1ApiDelegate {

    private final GametimeService service;

    public V1ApiDelegateimpl(GametimeService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Team>> fetchLeaguev1() {
        return ResponseEntity.ok(service.getLeague());
    }

    @Override
    public ResponseEntity<Player> readPlayer(String playerId) {
        Optional<Player> p = Optional.ofNullable(service.getPlayer(playerId)
                .orElseThrow(() -> new ResourceNotFoundException()));
        return ResponseEntity.ok(p.get());
    }

    @Override
    public ResponseEntity<Team> fetchTeam(String teamId) {
        Optional<Team> t = Optional.ofNullable(service.getTeam(teamId)
                .orElseThrow(() -> new ResourceNotFoundException()));
        return ResponseEntity.ok(t.get());
    }

    @Override
    public ResponseEntity<Player> createPlayer(Player player) {
        return ResponseEntity.ok(service.createPlayer(player));
    }

    @Override
    public ResponseEntity<Player> updatePlayer(Player player) {
        return ResponseEntity.ok(service.updatePlayer(player));
    }

    @Override
    public ResponseEntity<Void> addPlayerToTeam(String teamId, String playerId) {
        service.addPlayerToTeam(teamId, playerId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<List<PlayerTransaction>> fetchPlayerHistory(String playerId) {
        return ResponseEntity.ok(service.getPlayerHistory(playerId));
    }

    @Override
    public ResponseEntity<Team> setLineup(String teamId, LineupRequest lineupRequest) {
        return ResponseEntity.ok(service.setLineup(teamId, lineupRequest));
    }

    @Override
    public ResponseEntity<Void> removePlayerFromTeam(String teamId, String playerId) {
        service.removePlayerFromTeam(teamId, playerId);
        return ResponseEntity.ok().build();
    }

}
