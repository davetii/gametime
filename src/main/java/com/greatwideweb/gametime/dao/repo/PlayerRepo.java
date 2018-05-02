package com.greatwideweb.gametime.dao.repo;

import com.greatwideweb.gametime.dao.model.PlayerData;
import org.springframework.data.repository.CrudRepository;

public interface PlayerRepo extends CrudRepository<PlayerData, Long> {
}
