package software.daveturner.gametime.api;

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

    @Override
    public ResponseEntity<List<Team>> fetchLeaguev1() {
        return ResponseEntity.ok(service.getLeague());
    }
}
