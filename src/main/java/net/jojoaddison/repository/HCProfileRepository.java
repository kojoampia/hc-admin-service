package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.HCProfile;
import net.jojoaddison.domain.enumeration.RoleType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Profile entity.
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
    List<HCProfile> findByRoleTypeAndTeamIdInAndStatusTrue(RoleType roleType, List<String> teamIds);
}
