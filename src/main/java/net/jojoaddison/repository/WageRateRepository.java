package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the WageRate entity.
 */
@Repository
public interface WageRateRepository extends MongoRepository<WageRate, String> {
    /**
     * Every rate for a role, across all shift types, newest effective date first.
     *
     * <p>The head of this list is <b>not</b> "the rate in force today" any more, and has not been
     * since {@code shiftType} became part of the key on 2026-09-04: it is the most recently dated row
     * for the role, whichever shift it prices. Callers wanting one cell's history want
     * {@link #findByRoleAndShiftTypeOrderByValidFromDesc}; this one is the whole role's audit trail.
     */
    List<WageRate> findByRoleOrderByValidFromDesc(ProfessionalRole role);

    /** One cell's history — a role and a shift type — newest effective date first. */
    List<WageRate> findByRoleAndShiftTypeOrderByValidFromDesc(ProfessionalRole role, ShiftType shiftType);

    /**
     * Rates for one {@code (role, shiftType)} cell that had taken effect by {@code on}, newest first
     * — so the head of the list is the rate that governs a shift of that type worked on that date.
     */
    List<WageRate> findByRoleAndShiftTypeAndValidFromLessThanEqualOrderByValidFromDesc(
        ProfessionalRole role,
        ShiftType shiftType,
        LocalDate on
    );

    /**
     * Every rate that had taken effect by {@code on}, across all roles and shift types. Loaded once
     * and grouped in memory when valuing a batch of shifts, rather than one query per shift.
     */
    List<WageRate> findByValidFromLessThanEqualOrderByValidFromDesc(LocalDate on);

    List<WageRate> findAllByOrderByRoleAscShiftTypeAscValidFromDesc();
}
