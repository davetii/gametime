package com.greatwideweb.gametime.dao.repo;

import com.greatwideweb.gametime.dao.model.TeamData;
import org.springframework.data.repository.CrudRepository;

public interface TeamRepo extends CrudRepository<TeamData, Long> {
}
