package net.jojoaddison.repository;

import net.jojoaddison.domain.DutyRoster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the DutyRoster entity.
 */
@Repository
public interface DutyRosterRepository extends MongoRepository<DutyRoster, String> {}
