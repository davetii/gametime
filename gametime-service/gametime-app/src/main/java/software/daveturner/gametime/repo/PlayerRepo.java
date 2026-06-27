package software.daveturner.gametime.repo;

import org.springframework.data.repository.*;
import software.daveturner.gametime.entity.*;

import java.util.*;

public interface PlayerRepo extends CrudRepository<PlayerEntity, String> {
    List<PlayerEntity> findByIdIn(Collection<String> ids);
}
