package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.HCProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Profile entity.
 *
 * <p><b>{@code findByRoleTypeAndTeamIdInAndStatusTrue} was deleted on 2026-09-05, with its last
 * caller.</b> It was the old auto-scheduler's candidate query — role, covering team, still active —
 * and {@code RoundPlanningService} ranks {@link net.jojoaddison.domain.Professional} instead. The
 * rule moved because the console dataset seeds no {@code HCProfile} at all, so the query described a
 * search over an empty collection. Leaving it cost nothing at runtime and is exactly what a reader
 * writing the next planner would reach for, which is the mistake it would re-make.
 */
@Repository
public interface HCProfileRepository extends MongoRepository<HCProfile, String> {
    Optional<HCProfile> findByUserId(String userId);
    Optional<HCProfile> findByPersonId(String personId);
    Optional<HCProfile> findByContactId(String contactId);
    List<HCProfile> findByTeamId(String teamId);
    List<HCProfile> findByOrganisationId(String organisationId);
    List<HCProfile> findByRoles(String roles);
    List<HCProfile> findByStatus(Boolean status);
    List<HCProfile> findByCreatedBy(String createdBy);
    List<HCProfile> findByModifiedBy(String modifiedBy);
    List<HCProfile> findByCreatedDate(String createdDate);
    List<HCProfile> findByModifiedDate(String modifiedDate);
    List<HCProfile> findByOrganisationIdAndStatus(String organisationId, Boolean status);
    List<HCProfile> findByTeamIdAndStatus(String teamId, Boolean status);
    List<HCProfile> findByRolesAndStatus(String roles, Boolean status);
    List<HCProfile> findByOrganisationIdAndTeamId(String organisationId, String teamId);
}
