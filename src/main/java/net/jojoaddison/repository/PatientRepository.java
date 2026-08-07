package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Patient entity.
 */
@Repository
public interface PatientRepository extends MongoRepository<Patient, String> {
    @Query("{}")
    Page<Patient> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<Patient> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<Patient> findOneWithEagerRelationships(String id);
}
