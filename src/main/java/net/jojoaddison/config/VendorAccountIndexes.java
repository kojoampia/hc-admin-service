package net.jojoaddison.config;

import net.jojoaddison.domain.Vendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * The unique index on {@code vendor.account_id}, which is the join from hc-vendor's portal into this
 * directory — backlog item 31.
 *
 * <p>The third index this service creates, after {@link DirectoryLinkIndexes} and
 * {@link ServicePlanIndexes}, and for the reason stated there at length:
 * {@code spring.data.mongodb.auto-index-creation} is off, so an {@code @Indexed} on the document
 * would be a comment rather than a constraint, and turning it on globally would create indexes for
 * forty collections nobody has thought about.
 *
 * <h2>What it prevents</h2>
 *
 * <p>{@code account_id} does not merely label a vendor — since item 31 it <b>decides which vendor a
 * portal caller is</b>. Two rows sharing a login is therefore not a data-quality problem to tidy up
 * later; it is one supplier being handed another supplier's directory record, and (on hc-vendor's
 * side of the reconciliation) another supplier's contract note and spend.
 *
 * <p>hc-vendor knows this repository could not previously promise otherwise, and routed around it:
 * its {@code VendorScopeResolver} resolves a caller against its own {@code ux_vendor_account_id}
 * because <i>"hc-admin indexes no domain field and can hold two entries sharing a login"</i>. That
 * sentence stops being true here. {@code VendorResource} refuses a duplicate on all three write
 * handlers, which catches an administrator pasting a login into the console; this catches everything
 * else — a concurrent pair of writes that both pass that check, a seed, a migration, a shell.
 *
 * <p><b>Sparse as well as unique, and the sparseness is load-bearing.</b> A vendor with no portal
 * login is the ordinary directory entry rather than an incomplete one, and a plain unique index
 * treats every missing field as one shared null and refuses the second such vendor — which is most
 * of the directory.
 *
 * <h2>A duplicate already in the data is reported, not fatal — and the read is its backstop</h2>
 *
 * <p>Creating a unique index over a collection that already holds duplicates fails. Failing startup
 * on that would turn a data problem into an outage in a service whose first screen is a dashboard,
 * so it is logged at {@code error} with what to do and the application carries on, exactly as the
 * other two creators do.
 *
 * <p><b>That policy has a condition, and the condition is enforced rather than requested.</b>
 * "Report and continue" means this service can run without the index it believes it has, and an
 * invariant nothing enforces is a claim rather than a constraint. So
 * {@link net.jojoaddison.web.rest.VendorResource#getAllVendors} <b>refuses</b> — {@code 409}, on
 * the request — whenever resolving a login finds more than one row, instead of answering with the
 * first. The index is the prevention and the refusal at the read is the enforcement; neither is
 * sufficient alone, and a reader who has found only one of them should look for the other. It is
 * asserted in {@code VendorScopeIT}, which drops this index in order to reach the state this
 * paragraph permits.
 */
@Component
public class VendorAccountIndexes {

    /** Named rather than left to MongoDB's field-concatenation default, so a reader can find it. */
    public static final String ACCOUNT_ID_INDEX = "vendor_account_id";

    private static final Logger LOG = LoggerFactory.getLogger(VendorAccountIndexes.class);

    private final MongoTemplate mongoTemplate;

    public VendorAccountIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Idempotent for an identical specification, so this runs on every boot and does nothing on all but the first. */
    @EventListener(ApplicationReadyEvent.class)
    public void createAccountIdIndex() {
        try {
            mongoTemplate
                .indexOps(Vendor.class)
                .createIndex(new Index().on("account_id", Sort.Direction.ASC).unique().sparse().named(ACCOUNT_ID_INDEX));
            LOG.debug("vendor is indexed uniquely on account_id, sparsely");
        } catch (RuntimeException e) {
            LOG.error(
                "Could not create the unique index {} on vendor. Until it exists, two vendors can share one " +
                    "portal login, and a vendor resolving itself through GET /api/vendors?accountId.equals= is " +
                    "refused with 409 rather than being shown the wrong record. If this is a duplicate-key " +
                    "failure, find the duplicates by grouping on account_id, clear account_id from every row " +
                    "but the one that really holds the login, and restart.",
                ACCOUNT_ID_INDEX,
                e
            );
        }
    }
}
