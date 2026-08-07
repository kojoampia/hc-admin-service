package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Professional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Professional entity.
 */
@Repository
public interface ProfessionalRepository extends MongoRepository<Professional, String> {
    @Query("{}")
    Page<Professional> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<Professional> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<Professional> findOneWithEagerRelationships(String id);
}
