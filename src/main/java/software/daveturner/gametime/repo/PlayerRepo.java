package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

public interface PlayerRepo extends CrudRepository<PlayerEntity, String> {
}
