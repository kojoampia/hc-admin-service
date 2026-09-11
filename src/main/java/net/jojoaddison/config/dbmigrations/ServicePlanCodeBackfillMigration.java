package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jojoaddison.config.ApplicationProperties;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Stamps Abofonsa's published tier code onto the plans this console created before the catalogue was
 * reconciled — backlog item 56.
 *
 * <h2>What goes wrong without it, and why nothing would report it</h2>
 *
 * <p>{@code ServicePlanCatalogueSyncService} matches on {@link net.jojoaddison.domain.ServicePlan#getCode()}.
 * Production's rows have none: {@code service_plan} is seeded only under {@code dev}/{@code test}, so
 * production holds what an administrator typed — the three Bridge plans, priced, codeless. The first
 * sync therefore <b>creates rather than updates</b>: {@code findOneByCode("PEAR")} answers empty, a new
 * unpriced PEAR row is inserted, likewise PAWPAW and MELON, and the three legacy rows are left holding
 * every {@code Patient.plan} {@code @DBRef} they had. The board then shows six cards — three priced with
 * all the subscribers, three "Not priced" with none — and the sync reports
 * {@code created: 3, updated: 0, unpublishedCodes: []}, which reads as a clean run. <b>The legacy rows
 * appear in no field of that report and in no log line.</b>
 *
 * <h2>⚠ THE MATCHING RULE WAS WRITTEN WITHOUT SIGHT OF PRODUCTION AND MUST BE REVIEWED BEFORE IT RUNS</h2>
 *
 * <p>Nothing that produced this class can read the production collection. The three recognisers in
 * {@link #RECOGNISERS} come from two things that <em>are</em> readable here, and from nothing else:
 *
 * <ul>
 *   <li>{@code ServicePlan}'s own class javadoc, which records the rows it replaced by name and price —
 *       {@code Bridge Essential} / {@code Bridge Plus} / {@code Bridge Family} at GHS 320 / 680 / 1,240.
 *   <li>The retired {@code PlanTier} enum ({@code ESSENTIAL}, {@code PLUS}, {@code FAMILY}), which was
 *       {@code @NotNull @Field("tier")} until item 51 removed it from the class. <b>Removing a field
 *       from a {@code @Document} removes it from no stored document</b> — the same fact
 *       {@code ShiftTypeMigration} records about {@code duty_roster} — so every row written before
 *       2026-09-08 still carries it, and it is a machine-set value rather than a typed one.
 * </ul>
 *
 * <p><b>Which Bridge plan is which published tier is inferred and not confirmed.</b> The order-preserving
 * reading is the one encoded: 320 → 3,000, 680 → 5,000, 1,240 → 8,000, which agrees with the published
 * {@code displayOrder} 1/2/3 and with {@code Entry} / {@code Most chosen} / {@code Full cover}. Item 56
 * says in as many words that this mapping is by intent rather than by arithmetic and should be confirmed,
 * and no confirmation was available. <b>A wrong stamp silently re-points every patient on that plan</b>,
 * so read {@link #RECOGNISERS} against the real collection before enabling this.
 *
 * <h2>It is off by default, and that is what makes failing loudly safe</h2>
 *
 * <p>A Mongock change unit has no notion of a Spring profile and runs wherever the application runs —
 * which for an ungated unit means <em>the next deploy of this service, whatever that deploy was about</em>,
 * before anybody has reviewed the rule above. So the gate is a property,
 * {@code application.service-plan-code-backfill.enabled}, default {@code false}: this is inert until
 * somebody deliberately switches it on, exactly as {@code APPLICATION_ABOFONSACONTENT_ENABLED: "false"}
 * holds item 51's sync off until item 56 is answered.
 *
 * <p><b>{@code runAlways = true} is required by that gate and is not a nicety.</b> Mongock records a
 * change unit as executed the first time its {@code @Execution} returns — including a run that returned
 * early because the gate was off — and never repeats it. Without {@code runAlways} the switch would be
 * permanently dead on every database this service has ever started against, which is every database that
 * matters. It costs nothing: a disabled run returns before it reads anything at all, and an enabled one is
 * idempotent by construction — the second finds no codeless plan and stops on one query.
 *
 * <h2>Refuses rather than guesses, and refuses before writing anything</h2>
 *
 * <p>Every codeless plan must be recognised by <b>exactly one</b> recogniser. A plan matching none — a
 * fourth tier somebody created, a renamed row, a database this rule was not written for — and a plan
 * matching more than one both make the whole run throw, naming every offending row. Two codeless plans
 * resolving to the same code, or resolving to a code a plan already holds, do the same: either would
 * breach {@code ServicePlanIndexes}' sparse unique index and split one tier's subscribers across two
 * rows of the plan mix, both looking plausible.
 *
 * <p><b>The refusal is computed in full before the first write.</b> Stamping what matched and then
 * throwing on what did not is the half-backfilled collection that looks finished, which is this item's
 * own failure mode one level up. Nothing is written unless everything can be.
 *
 * <h2>A targeted {@code $set}, on raw documents</h2>
 *
 * <p>Written in {@link Document}s rather than through {@code ServicePlan} for two reasons, and the second
 * is the load-bearing one. Reading could not see {@code tier} at all — the mapped type no longer declares
 * it. And <b>writing through the mapped type would not be a stamp, it would be a replacement</b>: a
 * {@code save(entity)} rewrites the whole document from the fields the class declares now, dropping
 * {@code tier} and anything else unmapped. A {@code $set} of one field leaves {@code monthlyPrice}
 * untouched, which item 56 names as the thing that must survive — it is the one field hc-admin still owns,
 * the sync never writes it, and the dashboard's plan-mix revenue column is computed from it. Adopting
 * Abofonsa's prices would be a separate and deliberate decision, and is not folded in here.
 *
 * <p>This creates and needs no index. {@code ServicePlanIndexes} builds the unique sparse index on
 * {@code code} at {@code ApplicationReadyEvent}, which is after Mongock has run, so on a first start this
 * writes before that index exists — harmless, because the duplicate check above is done in this class
 * rather than delegated to the constraint.
 */
@ChangeUnit(id = "service-plan-code-backfill", order = "001", author = "hc-admin", runAlways = true)
public class ServicePlanCodeBackfillMigration {

    private static final Logger LOG = LoggerFactory.getLogger(ServicePlanCodeBackfillMigration.class);

    private static final String SERVICE_PLAN = "service_plan";
    private static final String CODE = "code";
    private static final String NAME = "name";
    private static final String TIER = "tier";

    /**
     * How a codeless plan is recognised, and what it becomes.
     *
     * <p>Two independent keys per row rather than one, because the pair can disagree in a real database
     * and each covers the other's hole. {@code tier} is the stronger: it was required and machine-set,
     * so every row written before item 51 has it — but a plan edited through the console <em>since</em>
     * item 51 deployed has had it rewritten away by the mapped save, and then only the name is left.
     * The name is the weaker: an administrator can retype it. Either alone is a rule with a silent gap;
     * requiring exactly one recogniser to match on either key is the narrowest thing that covers both.
     *
     * <p>Matching is case-insensitive and trimmed. It is deliberately not a prefix or contains test:
     * {@code Bridge Plus} is a prefix of nothing here, but a rule loose enough to be convenient is a
     * rule that quietly matches a fourth plan somebody adds later.
     *
     * @param legacyTier the retired {@code PlanTier} value still stored on pre-2026-09-08 documents
     * @param legacyName the plan name recorded in {@code ServicePlan}'s javadoc as what this console held
     * @param code Abofonsa's published tier code, which becomes the join key
     */
    private record Recogniser(String legacyTier, String legacyName, String code) {
        boolean matches(Document plan) {
            return equalsIgnoringCase(plan.getString(TIER), legacyTier) || equalsIgnoringCase(plan.getString(NAME), legacyName);
        }

        private static boolean equalsIgnoringCase(String stored, String expected) {
            return stored != null && stored.trim().equalsIgnoreCase(expected);
        }
    }

    /** ⚠ Inferred, not confirmed — see the mapping warning in this class's javadoc before running. */
    private static final List<Recogniser> RECOGNISERS = List.of(
        new Recogniser("ESSENTIAL", "Bridge Essential", "PEAR"),
        new Recogniser("PLUS", "Bridge Plus", "PAWPAW"),
        new Recogniser("FAMILY", "Bridge Family", "MELON")
    );

    private final MongoTemplate mongoTemplate;
    private final ApplicationProperties applicationProperties;

    public ServicePlanCodeBackfillMigration(MongoTemplate mongoTemplate, ApplicationProperties applicationProperties) {
        this.mongoTemplate = mongoTemplate;
        this.applicationProperties = applicationProperties;
    }

    @Execution
    public void migrate() {
        if (!applicationProperties.getServicePlanCodeBackfill().isEnabled()) {
            LOG.debug(
                "The service-plan code backfill is disabled and did nothing. Set " +
                    "application.service-plan-code-backfill.enabled (APPLICATION_SERVICEPLANCODEBACKFILL_ENABLED) once its " +
                    "matching rule has been reviewed against the plans this environment actually holds."
            );
            return;
        }

        // Missing, null and blank all count as codeless. A blank code would satisfy findOneByCode for
        // nothing and sits outside the sparse index, so it is the same state as an absent one.
        Query codeless = new Query(new Criteria().orOperator(Criteria.where(CODE).is(null), Criteria.where(CODE).regex("^\\s*$")));
        List<Document> plans = mongoTemplate.find(codeless, Document.class, SERVICE_PLAN);
        if (plans.isEmpty()) {
            LOG.debug("Every {} row already carries a code — nothing to backfill", SERVICE_PLAN);
            return;
        }

        Map<String, Document> byCode = resolveOrRefuse(plans);

        byCode.forEach((code, plan) ->
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(plan.get("_id"))), new Update().set(CODE, code), SERVICE_PLAN)
        );
        LOG.warn(
            "Stamped {} published tier code(s) onto plans that had none: {}. Every other field, monthlyPrice " +
                "included, is untouched — this was a targeted $set and not a rewrite. The catalogue sync will now " +
                "reconcile these rows rather than insert duplicates beside them.",
            byCode.size(),
            byCode.keySet()
        );
    }

    /**
     * Every codeless plan resolved to exactly one free code, or an exception naming everything wrong.
     *
     * <p>Four refusals, and all four are reported together rather than one per run: an operator turning
     * this on wants the whole picture of what this database holds, not the first row that broke.
     */
    private Map<String, Document> resolveOrRefuse(List<Document> plans) {
        List<String> taken = mongoTemplate
            .find(new Query(Criteria.where(CODE).ne(null)), Document.class, SERVICE_PLAN)
            .stream()
            .map(plan -> plan.getString(CODE))
            .filter(code -> code != null && !code.isBlank())
            .toList();

        List<String> refusals = new ArrayList<>();
        Map<String, Document> byCode = new LinkedHashMap<>();
        for (Document plan : plans) {
            List<Recogniser> matched = RECOGNISERS.stream()
                .filter(recogniser -> recogniser.matches(plan))
                .toList();
            if (matched.isEmpty()) {
                refusals.add(describe(plan) + " matches no recogniser");
                continue;
            }
            if (matched.size() > 1) {
                refusals.add(describe(plan) + " matches " + matched.stream().map(Recogniser::code).toList());
                continue;
            }
            String code = matched.getFirst().code();
            if (taken.contains(code)) {
                refusals.add(describe(plan) + " resolves to " + code + ", which another plan already holds");
                continue;
            }
            Document clash = byCode.put(code, plan);
            if (clash != null) {
                refusals.add(describe(plan) + " and " + describe(clash) + " both resolve to " + code);
            }
        }

        if (!refusals.isEmpty()) {
            // Nothing has been written at this point, and that ordering is the whole design: a partial
            // stamp would leave a collection that looks reconciled and is not.
            throw new IllegalStateException(
                "The service-plan code backfill refused to run and wrote nothing. Its matching rule was written " +
                    "from this repository rather than from a production database (backlog item 56) and does not fit " +
                    "what this one holds. Review the recognisers in ServicePlanCodeBackfillMigration against these rows " +
                    "before enabling it again: " +
                    refusals
            );
        }
        return byCode;
    }

    /** Id and name only. A plan name is not personal data, and no price or subscriber count is needed to act. */
    private static String describe(Document plan) {
        return "plan " + plan.get("_id") + " (\"" + plan.getString(NAME) + "\", tier " + tierOf(plan) + ")";
    }

    private static String tierOf(Document plan) {
        String tier = plan.getString(TIER);
        return tier == null ? "absent" : tier.toUpperCase(Locale.ROOT);
    }

    /**
     * Deliberately does nothing.
     *
     * <p>There is no partial state to undo — a refused run writes nothing, so the only run there is to
     * roll back is one that stamped every row it was asked to. And after a successful run the catalogue
     * sync may already have reconciled against those codes, so unsetting them would re-open exactly the
     * duplication this exists to prevent, on a schedule nobody is watching. Recovering from a wrong
     * mapping means deciding per plan which subscribers belong where, which is a data decision and not
     * something a rollback hook can take.
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("service-plan-code-backfill is not rolled back automatically — see the class comment");
    }
}
