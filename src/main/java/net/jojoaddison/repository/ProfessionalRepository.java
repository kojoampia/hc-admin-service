package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Professional entity.
 */
@Repository
public interface ProfessionalRepository extends MongoRepository<Professional, String> {
    @Query("{}")
    Page<Professional> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<Professional> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<Professional> findOneWithEagerRelationships(String id);

    /**
     * The console's directory listings, split on {@code isArchived}.
     *
     * <p>Raw queries rather than derived ones: {@code findByIsArchivedNot} would have to be parsed
     * back into a property, and {@code IsArchived} is exactly the shape Spring Data's parser can
     * read two ways. These say what they mean against the stored field name.
     *
     * <p>{@code $ne: true} and not {@code false}: a document written before this field existed does
     * not carry it, and {@code is_archived: false} matches none of them. In MongoDB {@code $ne}
     * does match a missing field, which is the semantics the directory needs.
     */
    @Query("{ 'is_archived': true }")
    Page<Professional> findArchived(Pageable pageable);

    @Query("{ 'is_archived': { '$ne': true } }")
    Page<Professional> findNotArchived(Pageable pageable);

    /**
     * The professional belonging to a profile.
     *
     * <p><b>This is the direction the link is actually stored in.</b> {@code Profile} also declares
     * a {@code professional} back-reference, and reading that is the obvious way to answer this
     * question — but nothing populates it. Every {@code Professional} carries {@code profile}; no
     * {@code Profile} carries {@code professional}. Following the back-reference compiles, passes a
     * test that sets both sides, and returns empty against every real document.
     *
     * <p>Derived rather than a raw {@code @Query}, and this is the one method here where that is
     * the safer choice: {@code profile} is a {@code @DBRef}, so the value to match is a reference
     * document rather than the id string inside it. A derived method takes the entity and lets the
     * mapping context write that reference exactly as the converter stored it. A hand-written
     * {@code { 'profile': ?0 }} would interpolate the id and match nothing — silently, as an empty
     * result rather than an error. Same hazard, same reasoning, as
     * {@code ShiftValuationService.payableShifts}.
     */
    Optional<Professional> findByProfile(Profile profile);
}
