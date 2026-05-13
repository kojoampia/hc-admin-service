package net.jojoaddison.repository;

import net.jojoaddison.domain.PricingPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PricingPlan entity.
 */
@Repository
public interface PricingPlanRepository extends MongoRepository<PricingPlan, String> {}
