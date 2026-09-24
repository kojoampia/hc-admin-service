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
     * The profile belonging to a gateway account, addressed by whatever {@code account_id} holds
     * for that row — an equality match, with no translation between value spaces.
     *
     * <p>{@code accountId} is the identity link between this service and a gateway's user record,
     * and it is the only way to answer "who is signed in, in detail" — this service runs
     * {@code skipUserManagement: true} and has no route to any gateway's user collection.
     *
     * <p><b>The decided value is the account's {@code User.id}</b> (item 123; see
     * {@link net.jojoaddison.domain.Profile}'s class javadoc for which seeded rows have been
     * translated and which still hold hc-professional logins, and why). An earlier version of this
     * comment argued the login was the only workable key because the {@code uid} claim was
     * "minted by one gateway of the three" — that was true when written and is not now:
     * hc-professional's gateway has stamped {@code uid} since 2026-09-07, which is what made the
     * estate rule implementable. Both callers pass the value the rows they serve hold: {@link
     * net.jojoaddison.service.CurrentProfessionalService} the token's <em>login</em> (its rows are
     * the untranslated clinician nine), {@link
     * net.jojoaddison.web.rest.ProfileResource#getProfileByAccount} whatever the console sends
     * from the account it read.
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
