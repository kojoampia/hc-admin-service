package net.jojoaddison.repository;

import net.jojoaddison.domain.PlatformService;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PlatformService entity.
 */
@Repository
public interface PlatformServiceRepository extends MongoRepository<PlatformService, String> {}
