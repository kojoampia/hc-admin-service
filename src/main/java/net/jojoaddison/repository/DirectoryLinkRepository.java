package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the DirectoryLink entity.
 */
@Repository
public interface DirectoryLinkRepository extends MongoRepository<DirectoryLink, String> {
    /**
     * One subject, by its natural key.
     *
     * <p>Explicit {@code @Query} on the stored field names rather than a derived method, matching
     * {@link ProfileRepository#findByAccount}: the Java property is {@code externalKey} and the
     * document field is {@code external_key}, and a derived method works only because Spring maps it
     * back through the {@code @Field} annotation — the indirection that breaks silently when one
     * side is renamed.
     *
     * <p>Reads only. The write goes through {@code MongoTemplate.findAndModify} with an upsert, for
     * the reason {@link DirectoryLink} gives: there is no unique index in this service to lean on.
     */
    @Query("{ 'source': ?0, 'external_key': ?1 }")
    Optional<DirectoryLink> findSubject(DirectorySource source, String externalKey);

    /**
     * The sweep the reconciliation walks.
     *
     * <p>There was a {@code Page}-returning overload beside this one, serving
     * {@code GET /api/directory-links?source=…}. It went when that handler gained a second optional
     * filter ({@code localId.in}, backlog item 45) and moved to {@link net.jojoaddison.repository.support.NamedFilters}:
     * two independent optional filters need four derived methods, which is the growth that support
     * class exists to stop. This overload stays because the reconciliation wants every link of one
     * source unpaged, which is not what the endpoint asks for.
     */
    @Query("{ 'source': ?0 }")
    List<DirectoryLink> findBySource(DirectorySource source);
}
