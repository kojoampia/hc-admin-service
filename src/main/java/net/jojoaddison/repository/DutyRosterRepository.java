package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the DutyRoster entity.
 */
@Repository
public interface DutyRosterRepository extends MongoRepository<DutyRoster, String> {
    List<DutyRoster> findByDateAndStatus(LocalDate date, ShiftStatus status);

    List<DutyRoster> findByPatientIdAndDateOrderByNameAsc(String patientId, LocalDate date);

    boolean existsByProfessionalIdAndDate(String professionalId, LocalDate date);

    int countByProfessionalIdAndDateBetween(String professionalId, LocalDate startDate, LocalDate endDate);
}
