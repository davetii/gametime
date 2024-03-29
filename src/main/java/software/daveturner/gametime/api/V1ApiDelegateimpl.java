package software.daveturner.gametime.api;

import org.springframework.cache.annotation.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
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
    public ResponseEntity<Player> readPlayer(UUID playerId) {
        Optional<Player> p = service.getPlayer(playerId.toString());
        return ResponseEntity.ok(p.get());
    }
}
