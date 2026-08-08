package net.jojoaddison.repository;

import net.jojoaddison.domain.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Vendor entity.
 */
@Repository
public interface VendorRepository extends MongoRepository<Vendor, String> {
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
    Page<Vendor> findArchived(Pageable pageable);

    @Query("{ 'is_archived': { '$ne': true } }")
    Page<Vendor> findNotArchived(Pageable pageable);
}
