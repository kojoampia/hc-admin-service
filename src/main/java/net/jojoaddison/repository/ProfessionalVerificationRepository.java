package net.jojoaddison.repository;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ProfessionalVerification entity.
 */
@Repository
public interface ProfessionalVerificationRepository extends MongoRepository<ProfessionalVerification, String> {
    /**
     * One professional's history, newest first — the order the record panel reads it in, and the
     * order that makes the head of the list the current state.
     */
    List<ProfessionalVerification> findByProfessionalIdOrderByRecordedAtDesc(String professionalId);

    /**
     * How many verifications reached {@code status} in a window.
     *
     * <p>This is what makes the dashboard's "+N verified this week" answerable at all. A current-state
     * field cannot answer it: it knows only the latest decision, and overwrites the evidence for
     * every earlier one.
     */
    long countByStatusAndRecordedAtGreaterThanEqual(VerificationStatus status, Instant since);

    @Query("{}")
    Page<ProfessionalVerification> findAllWithEagerRelationships(Pageable pageable);
}
