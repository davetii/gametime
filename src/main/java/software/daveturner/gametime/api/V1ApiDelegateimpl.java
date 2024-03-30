package software.daveturner.gametime.api;

import jakarta.persistence.*;
import org.springframework.cache.annotation.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import software.daveturner.gametime.model.*;
import software.daveturner.gametime.service.*;

import java.util.*;

@Service
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
        /*
        Optional<Player> p = Optional.ofNullable(service.getPlayer(playerId)
                .orElseThrow(() -> new EntityNotFoundException()));
        return ResponseEntity.ok(p.get());

         */
        Optional<Player> p = service.getPlayer(playerId);
        if (p.isPresent()) {
            return ResponseEntity.ok(p.get());
        }
        return ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<Team> fetchTeam(String teamId) {
        /*Optional<Team> t = Optional.ofNullable(service.getTeam(teamId)
                .orElseThrow(() -> new EntityNotFoundException()));
        return ResponseEntity.ok(t.get());
         */
        Optional<Team> t = service.getTeam(teamId);
        if (t.isPresent()) {
            return ResponseEntity.ok(t.get());
        }
        return ResponseEntity.notFound().build();
    }

}
