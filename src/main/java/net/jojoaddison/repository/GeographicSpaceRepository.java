package net.jojoaddison.repository;

import net.jojoaddison.domain.GeographicSpace;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the GeographicSpace entity.
 */
@Repository
public interface GeographicSpaceRepository extends MongoRepository<GeographicSpace, String> {}
