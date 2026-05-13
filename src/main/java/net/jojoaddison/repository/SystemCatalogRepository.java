package net.jojoaddison.repository;

import net.jojoaddison.domain.SystemCatalog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the SystemCatalog entity.
 */
@Repository
public interface SystemCatalogRepository extends MongoRepository<SystemCatalog, String> {}
