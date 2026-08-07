package net.jojoaddison.repository;

import net.jojoaddison.domain.CareActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the CareActivity entity.
 */
@Repository
public interface CareActivityRepository extends MongoRepository<CareActivity, String> {}
