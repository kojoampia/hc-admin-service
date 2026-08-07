package net.jojoaddison.repository;

import net.jojoaddison.domain.ServicePlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ServicePlan entity.
 */
@Repository
public interface ServicePlanRepository extends MongoRepository<ServicePlan, String> {}
