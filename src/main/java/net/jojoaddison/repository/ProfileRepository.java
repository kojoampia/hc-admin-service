package net.jojoaddison.repository;

import java.util.Optional;
import net.jojoaddison.domain.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Profile entity.
 */
@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    /**
     * The profile belonging to a gateway account, addressed by its <b>login</b>.
     *
     * <p>{@code accountId} is the identity link between this service and the gateway's user record,
     * and it is the only way to answer "who is signed in, in detail" — this service runs
     * {@code skipUserManagement: true} and has no route to the gateway's user collection.
     *
     * <p><b>The login is what it holds — not the gateway user id.</b> The login is the JWT subject
     * and is therefore present on every token from all three stacks; the user id arrives only as
     * hc-admin's {@code uid} claim, which exists for the auditor and is minted by one gateway of the
     * three. Keying people on it would make this service resolvable for hc-admin's own accounts and
     * for nobody else. Both callers here read the login: {@link
     * net.jojoaddison.service.CurrentProfessionalService} from the token, {@link
     * net.jojoaddison.web.rest.ProfileResource#getProfileByAccount} from the path.
     *
     * <p>Explicit {@code @Query} on the stored field name rather than a derived method. The Java
     * property is {@code accountId} and the document field is {@code account_id}; a derived
     * {@code findByAccountId} works only because Spring maps it back through the {@code @Field}
     * annotation, and that indirection is exactly what breaks silently when someone renames one
     * side. Naming the stored field means a rename fails loudly.
     */
    @Query("{ 'account_id': ?0 }")
    Optional<Profile> findByAccount(String accountId);
}
