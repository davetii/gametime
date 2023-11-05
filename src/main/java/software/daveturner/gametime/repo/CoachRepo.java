package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

public interface CoachRepo extends CrudRepository<CoachEntity, Long> {
}
