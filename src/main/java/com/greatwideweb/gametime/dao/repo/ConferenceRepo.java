package com.greatwideweb.gametime.dao.repo;

import com.greatwideweb.gametime.dao.model.ConferenceData;
import org.springframework.data.repository.CrudRepository;

public interface ConferenceRepo extends CrudRepository<ConferenceData, Long> {
}
