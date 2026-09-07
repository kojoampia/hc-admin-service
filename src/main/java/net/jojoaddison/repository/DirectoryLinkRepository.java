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

    /**
     * How many accounts from one stack this service knows about and holds no local record for.
     *
     * <p>For {@link DirectorySource#HC_PROFESSIONAL} that is every link, and permanently so: both
     * event types on that topic are {@code LINK_ONLY}, because {@code Professional} requires a
     * {@code role} and a {@code licenceNumber} and neither is on the wire. The dashboard reports this
     * <b>beside</b> the count of {@code Professional} documents rather than added into it — see
     * {@code DashboardMetricsDTO.professionalsAwaitingRecord} for why the tile's meaning is left
     * alone — and {@code GET /api/directory-links?source=…&unlinked=true} lists the same rows.
     *
     * <p>{@code 'local_id': null} matches a missing field as well as a null one, which is what an
     * unclaimed link actually looks like: {@code DirectoryProjectionService.setIfPresent} never writes
     * the field at all until there is a record to name. The two readers of this rule — this count and
     * the endpoint's {@code unlinked} filter — are asserted against each other in
     * {@code DashboardMetricsResourceIT}, because a tile and a list disagreeing about one number is a
     * defect this dashboard has already had twice.
     */
    @Query(value = "{ 'source': ?0, 'local_id': null }", count = true)
    long countBySourceWithNoLocalRecord(DirectorySource source);
}
