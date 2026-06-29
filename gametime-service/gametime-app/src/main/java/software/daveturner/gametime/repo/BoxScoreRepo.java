package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

import java.util.*;

public interface BoxScoreRepo extends CrudRepository<BoxScoreEntity, String> {

    /** All per-player stat lines for a single game. */
    List<BoxScoreEntity> findByGameId(String gameId);
}
