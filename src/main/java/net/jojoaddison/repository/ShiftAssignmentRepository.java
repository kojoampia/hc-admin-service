package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.ShiftAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ShiftAssignment entity.
 */
@Repository
public interface ShiftAssignmentRepository extends MongoRepository<ShiftAssignment, String> {
    @Query("{}")
    Page<ShiftAssignment> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<ShiftAssignment> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<ShiftAssignment> findOneWithEagerRelationships(String id);
}
