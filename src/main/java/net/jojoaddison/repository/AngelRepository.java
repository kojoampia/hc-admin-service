package net.jojoaddison.repository;

import net.jojoaddison.domain.Angel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Angel entity.
 */
@Repository
public interface AngelRepository extends MongoRepository<Angel, String> {
    /** The angels not attached to a patient — what the relationship picker asks for. */
    Page<Angel> findByPatientIsNull(Pageable pageable);
}
