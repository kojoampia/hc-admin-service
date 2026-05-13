package net.jojoaddison.repository;

import net.jojoaddison.domain.FacilityCatalog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the FacilityCatalog entity.
 */
@Repository
public interface FacilityCatalogRepository extends MongoRepository<FacilityCatalog, String> {}
