package net.jojoaddison.repository;

import net.jojoaddison.domain.Facility;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Facility entity.
 */
@Repository
public interface FacilityRepository extends MongoRepository<Facility, String> {}
