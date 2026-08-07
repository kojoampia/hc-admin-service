package net.jojoaddison.repository;

import net.jojoaddison.domain.RosterWeek;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the RosterWeek entity.
 */
@Repository
public interface RosterWeekRepository extends MongoRepository<RosterWeek, String> {}
