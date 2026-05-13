package net.jojoaddison.repository;

import net.jojoaddison.domain.PatientPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PatientPlan entity.
 */
@Repository
public interface PatientPlanRepository extends MongoRepository<PatientPlan, String> {}
