package net.jojoaddison.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.service.dto.VendorSummaryDTO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * The vendor directory's four tiles, computed over the whole collection.
 *
 * <p>See {@link VendorSummaryDTO} for why two of the four cannot be computed by the client at all.
 */
@Service
public class VendorSummaryService {

    /**
     * Archived vendors are out of every figure.
     *
     * <p>{@code $ne: true} rather than {@code is(false)} for the same reason the list endpoints use
     * it: a document written before {@code is_archived} existed does not carry the field, and
     * {@code is_archived: false} matches none of them — so the whole collection would total zero.
     *
     * <p>A method rather than a constant, because {@link Criteria} is <strong>mutable</strong>:
     * {@code .and(…)} appends to the receiver and hands it back. A shared static would accumulate a
     * {@code status} clause on its first use and throw {@code "you can't add a second 'status'
     * criteria"} on its second — so the first tile would be right, the next would 500, and it would
     * only ever show up once two of them were asked for.
     */
    private static Criteria notArchived() {
        return Criteria.where("is_archived").ne(true);
    }

    /**
     * What the "Review or pending" tile counts.
     *
     * <p>Two statuses in one tile because the demo draws one. They are distinct states — a vendor
     * pending approval has never traded, one under review has — but both mean "not currently a
     * clean active contract", which is the question the tile asks.
     */
    private static final List<AccountStatus> NEEDS_REVIEW = List.of(AccountStatus.UNDER_REVIEW, AccountStatus.PENDING);

    private final MongoTemplate mongoTemplate;

    public VendorSummaryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public VendorSummaryDTO summary() {
        return new VendorSummaryDTO(
            spendToDate(),
            categoryCount(),
            countByStatus(List.of(AccountStatus.ACTIVE)),
            countByStatus(NEEDS_REVIEW)
        );
    }

    /**
     * Summed in Java rather than by a {@code $group} pipeline.
     *
     * <p>{@code spend_to_date} is a {@link BigDecimal}, which MongoDB stores as a Decimal128 only if
     * it was written that way; the seed and anything written through the REST surface may hold a
     * double or even a string. {@code $sum} silently contributes zero for every value whose BSON
     * type is not numeric, so a pipeline here would return a total that is quietly short rather
     * than one that fails. Reading the documents and adding the mapped {@code BigDecimal}s uses the
     * same conversion the rest of the application does, and the vendor collection is a directory of
     * suppliers — hundreds at the outside, not a fact table.
     */
    private BigDecimal spendToDate() {
        return mongoTemplate
            .find(new Query(notArchived()), Vendor.class)
            .stream()
            .map(Vendor::getSpendToDate)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Distinct supply categories in use.
     *
     * <p>Blanks are excluded: a vendor with no category recorded is not a category of its own, and
     * counting it would make the tile creep up as records are added incompletely.
     */
    private long categoryCount() {
        return mongoTemplate
            .findDistinct(new Query(notArchived()), "category", Vendor.class, String.class)
            .stream()
            .filter(category -> category != null && !category.isBlank())
            .count();
    }

    private long countByStatus(List<AccountStatus> statuses) {
        return mongoTemplate.count(new Query(notArchived().and("status").in(statuses)), Vendor.class);
    }
}
