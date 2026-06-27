package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

import java.util.*;

public interface PlayerTeamHistRepo extends CrudRepository<PlayerTeamHistEntity, String> {
    List<PlayerTeamHistEntity> findByPlayerIdOrderByTransactionDateDesc(String playerId);
}
