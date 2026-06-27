package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

import java.util.*;

public interface PlayerTeamRepo extends CrudRepository<PlayerTeamEntity, String> {
    List<PlayerTeamEntity> findByTeamId(String teamId);
}
