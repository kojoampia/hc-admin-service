package net.jojoaddison.repository;

import net.jojoaddison.domain.Hub;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Hub entity.
 */
@Repository
public interface HubRepository extends MongoRepository<Hub, String> {}
