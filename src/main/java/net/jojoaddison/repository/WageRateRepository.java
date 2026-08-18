package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the WageRate entity.
 */
@Repository
public interface WageRateRepository extends MongoRepository<WageRate, String> {
    /**
     * Every rate for a role, newest effective date first. The first element is the rate in force
     * today; the rest are history.
     */
    List<WageRate> findByRoleOrderByValidFromDesc(ProfessionalRole role);

    /**
     * Rates for a role that had taken effect by {@code on}, newest first — so the head of the list
     * is the rate that governs a shift worked on that date.
     */
    List<WageRate> findByRoleAndValidFromLessThanEqualOrderByValidFromDesc(ProfessionalRole role, LocalDate on);

    /**
     * Every rate that had taken effect by {@code on}, across all roles. Loaded once and grouped in
     * memory when valuing a batch of shifts, rather than one query per shift.
     */
    List<WageRate> findByValidFromLessThanEqualOrderByValidFromDesc(LocalDate on);

    List<WageRate> findAllByOrderByRoleAscValidFromDesc();
}
