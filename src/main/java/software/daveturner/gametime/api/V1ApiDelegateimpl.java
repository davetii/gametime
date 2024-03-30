package software.daveturner.gametime.api;

import jakarta.persistence.*;
import org.springframework.cache.annotation.*;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable
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

}
