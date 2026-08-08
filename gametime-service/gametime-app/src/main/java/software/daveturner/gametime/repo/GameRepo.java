package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

public interface GameRepo extends CrudRepository<GameEntity, String> {
}
