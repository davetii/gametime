package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

import java.util.*;

public interface GameEventRepo extends CrudRepository<GameEventEntity, String> {

    /** Play-by-play for a game in event order (sequence is game-wide — #020). */
    List<GameEventEntity> findByGameIdOrderBySequenceAsc(String gameId);
}
