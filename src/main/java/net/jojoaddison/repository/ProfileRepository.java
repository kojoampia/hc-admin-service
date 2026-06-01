package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.RoleType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Profile entity.
 */
@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUserId(String userId);
    Optional<Profile> findByPersonId(String personId);
    Optional<Profile> findByContactId(String contactId);
    List<Profile> findByTeamId(String teamId);
    List<Profile> findByOrganisationId(String organisationId);
    List<Profile> findByRoles(String roles);
    List<Profile> findByStatus(Boolean status);
    List<Profile> findByCreatedBy(String createdBy);
    List<Profile> findByModifiedBy(String modifiedBy);
    List<Profile> findByCreatedDate(String createdDate);
    List<Profile> findByModifiedDate(String modifiedDate);
    List<Profile> findByOrganisationIdAndStatus(String organisationId, Boolean status);
    List<Profile> findByTeamIdAndStatus(String teamId, Boolean status);
    List<Profile> findByRolesAndStatus(String roles, Boolean status);
    List<Profile> findByOrganisationIdAndTeamId(String organisationId, String teamId);
    List<Profile> findByRoleTypeAndTeamIdInAndStatusTrue(RoleType roleType, List<String> teamIds);
}
