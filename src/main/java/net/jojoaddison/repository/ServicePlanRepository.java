package net.jojoaddison.repository;

import java.util.Optional;
import net.jojoaddison.domain.ServicePlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ServicePlan entity.
 */
@Repository
public interface ServicePlanRepository extends MongoRepository<ServicePlan, String> {
    /**
     * The plan Abofonsa publishes under this code, if this service holds one.
     *
     * <p>{@code code} is the join key between this catalogue and the published one — see
     * {@link net.jojoaddison.service.ServicePlanCatalogueSyncService}. {@code findOne} rather than
     * {@code findAll}: {@code config/ServicePlanIndexes} makes the field uniquely indexed, so more
     * than one match is a data fault rather than a shape this code should quietly tolerate.
     */
    Optional<ServicePlan> findOneByCode(String code);
}
