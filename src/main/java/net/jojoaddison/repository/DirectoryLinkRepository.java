package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** The console's list, and the sweep the reconciliation walks. */
    @Query("{ 'source': ?0 }")
    Page<DirectoryLink> findBySource(DirectorySource source, Pageable pageable);

    @Query("{ 'source': ?0 }")
    List<DirectoryLink> findBySource(DirectorySource source);
}
