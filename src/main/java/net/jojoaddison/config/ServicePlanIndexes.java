package net.jojoaddison.config;

import net.jojoaddison.domain.ServicePlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * The unique index on {@code service_plan.code}, which is the join to Abofonsa's published catalogue.
 *
 * <p>The second index this service creates, after {@link DirectoryLinkIndexes}, and for the same
 * reason stated there at length: {@code spring.data.mongodb.auto-index-creation} is off, so a
 * {@code @Indexed} on the document would be a comment rather than a constraint, and turning it on
 * globally would create indexes for forty collections nobody has thought about.
 *
 * <p><b>Sparse as well as unique, and the sparseness is the point.</b> A plan created by an
 * administrator before this catalogue was reconciled has no {@code code} at all — that is a real and
 * permitted state — and a plain unique index treats every missing field as one shared null and
 * rejects the second such plan. Sparse excludes them, so uniqueness constrains exactly the plans
 * that claim a published code.
 *
 * <p>What it buys is the same "at most one" that {@code directory_link} needed:
 * {@link net.jojoaddison.service.ServicePlanCatalogueSyncService} looks a plan up by code and inserts
 * when it finds none, and two runs racing — two instances, or an administrator pressing
 * {@code POST /api/service-plans/sync} while the scheduler is mid-pass — can both find nothing and
 * both insert. Two documents for one published tier would then split the patients holding it across
 * two rows of the dashboard's plan mix, with both rows looking plausible.
 *
 * <p>A duplicate already in the data is reported and not fatal, exactly as for {@code directory_link}:
 * failing startup over it would turn a data problem into an outage in a service whose first screen is
 * a dashboard.
 */
@Component
public class ServicePlanIndexes {

    /** Named rather than left to MongoDB's field-concatenation default, so a reader can find it. */
    static final String CODE_INDEX = "service_plan_code";

    private static final Logger LOG = LoggerFactory.getLogger(ServicePlanIndexes.class);

    private final MongoTemplate mongoTemplate;

    public ServicePlanIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Idempotent for an identical specification, so this runs on every boot and does nothing on all but the first. */
    @EventListener(ApplicationReadyEvent.class)
    public void createCodeIndex() {
        try {
            mongoTemplate
                .indexOps(ServicePlan.class)
                .createIndex(new Index().on("code", Sort.Direction.ASC).unique().sparse().named(CODE_INDEX));
            LOG.debug("service_plan is indexed uniquely on code, sparsely");
        } catch (RuntimeException e) {
            LOG.error(
                "Could not create the unique index {} on service_plan. Until it exists, two catalogue syncs racing can " +
                "both insert the same published tier, and the patients holding it split across two rows of the plan mix. " +
                "If this is a duplicate-key failure, find the duplicates by grouping on code, move every Patient.plan " +
                "reference onto the one you are keeping, delete the other, and restart.",
                CODE_INDEX,
                e
            );
        }
    }
}
