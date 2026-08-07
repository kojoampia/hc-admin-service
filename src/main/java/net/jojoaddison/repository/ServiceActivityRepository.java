package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.ServiceActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ServiceActivity entity.
 */
@Repository
public interface ServiceActivityRepository extends MongoRepository<ServiceActivity, String> {
    @Query("{}")
    Page<ServiceActivity> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<ServiceActivity> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<ServiceActivity> findOneWithEagerRelationships(String id);
}
