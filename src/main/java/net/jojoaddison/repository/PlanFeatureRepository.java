package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.PlanFeature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PlanFeature entity.
 */
@Repository
public interface PlanFeatureRepository extends MongoRepository<PlanFeature, String> {
    @Query("{}")
    Page<PlanFeature> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<PlanFeature> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<PlanFeature> findOneWithEagerRelationships(String id);
}
