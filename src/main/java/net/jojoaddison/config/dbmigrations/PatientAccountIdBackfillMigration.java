package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Populates {@code patient.account_id} on rows written before the field existed, from
 * {@code directory_link.account_id} — backlog item 115.
 *
 * <h2>Why existing rows need one at all</h2>
 *
 * <p>{@code Patient.accountId} is {@code @NotNull} and {@code ValidatingMongoEventListener} enforces
 * it on every mapped save — so a stored row without one is not merely incomplete, it is a row the
 * application can read but never again write: an administrator's {@code PUT} refuses at the DTO-less
 * {@code @Valid} boundary, and {@code DirectoryProjectionService.merge} deliberately skips rather
 * than dead-letters. Every row written before item 115 shipped is in that state.
 *
 * <h2>The one lawful source, and why it is not hc-patient's api</h2>
 *
 * <p>{@code directory_link.account_id} is the subject's gateway {@code User.id} as published by
 * hc-patient's own producers (their item 72 refactor) and recorded here by item 130 — the identity's
 * owner saying who the account is, already on this side of the boundary. <b>Calling hc-patient from
 * a change unit was ruled out in the item itself</b>: a migration runs with no request and therefore
 * no token to relay — {@code PatientServiceClient} reads {@code SecurityUtils.getCurrentRequestJwt()}
 * and answers {@code unavailable()} on a null token, so every lookup would fail silently and
 * identically — and it would make hc-patient a hard <em>boot</em> dependency of this service, the
 * exact shape whose {@code consul.config.fail-fast} cousin crash-looped the first patient deploy.
 *
 * <h2>A row it cannot resolve is REPORTED, not defaulted, and not fatal</h2>
 *
 * <p>House policy, set twice before this class existed: {@code DirectoryLinkIndexes} and
 * {@code ServicePlanIndexes} both log an ERROR naming the remedy and continue, because failing
 * startup over a data problem turns it into an outage in a service whose first screen is a
 * dashboard. Two populations are expected to be unresolvable and both are named in the ERROR:
 * patients created through the console before 2026-08-28 (the button removed that day made records
 * with no account behind them, so they have no link at all), and patients learned from frames
 * published before hc-patient's refactor (their link exists but never carried an
 * {@code account_id}). <b>Never default the value</b> — a fabricated account id is a join key that
 * silently matches nothing, item 26's defect with a new name; a fixture that models a state is fine,
 * a migration inventing an id at runtime is not.
 *
 * <h2>{@code runAlways = true}, because report-and-continue leaves work standing</h2>
 *
 * <p>A one-shot unit would log the unresolved rows once, on one deploy, and never look again — while
 * the very mechanism that resolves them keeps running: any later frame about the subject teaches the
 * link its {@code account_id} ({@code DirectoryProjectionService.recordEvent}), after which the next
 * start resolves rows this one could not. Re-running also re-reports what still stands, which is
 * what keeps a standing data problem visible instead of buried in one deploy's log. It is idempotent
 * by selection — only rows missing the value are read — so a start with nothing missing costs one
 * query and says nothing. Unlike {@code ServicePlanCodeBackfillMigration} there is no gate flag:
 * that unit's matching rule was inferred and dangerous, this one's join ({@code directory_link
 * .local_id = patient._id}, same source {@code DirectoryProjectionService.createAndClaim} claims by)
 * is the estate's own, and a wrong outcome here is an absent stamp, never a wrong one.
 *
 * <h2>A targeted {@code $set}, on raw documents</h2>
 *
 * <p>For both of its neighbours' reasons at once. Writing through the mapped type is impossible by
 * construction — a {@code save} of a row that still lacks the field is exactly the validation
 * failure described above — and would anyway be a replacement rather than a stamp, rewriting fields
 * an administrator owns. A {@code $set} of one field touches nothing else.
 */
@ChangeUnit(id = "patient-account-id-backfill", order = "003", author = "hc-admin", runAlways = true)
public class PatientAccountIdBackfillMigration {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAccountIdBackfillMigration.class);

    private static final String PATIENT = "patient";
    private static final String DIRECTORY_LINK = "directory_link";
    private static final String ACCOUNT_ID = "account_id";
    private static final String LOCAL_ID = "local_id";
    private static final String SUBJECT_KIND = "subject_kind";
    private static final String PATIENT_KIND = "PATIENT";

    /** How many unresolved row ids one ERROR line names before summarising — a bound, not a filter. */
    static final int REPORTED_IDS_CAP = 20;

    private final MongoTemplate mongoTemplate;

    public PatientAccountIdBackfillMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void migrate() {
        // Missing, null and blank all count as absent: a blank account id claims nothing, joins
        // nothing, and would satisfy @NotNull while breaking everything the field is for.
        Query missing = new Query(
            new Criteria().orOperator(Criteria.where(ACCOUNT_ID).is(null), Criteria.where(ACCOUNT_ID).regex("^\\s*$"))
        );
        List<Document> patients = mongoTemplate.find(missing, Document.class, PATIENT);
        if (patients.isEmpty()) {
            LOG.debug("Every {} row already carries an {} — nothing to backfill", PATIENT, ACCOUNT_ID);
            return;
        }

        // One read of the links into a map, never a query per row — the same budget argument the
        // CSV export settled (item 62): this side of the join is in-process Mongo, and the whole
        // collection costs less than a per-row fan-out on any database large enough to matter.
        Map<String, String> accountIdByLocalId = new HashMap<>();
        mongoTemplate
            .find(
                // Source-scoped AND kind-scoped: only an HC_PATIENT link of kind PATIENT names a
                // patient row. A professional's is excluded by source; a care angel's is NOT — a
                // nomination is published on the patient stream, so its link is HC_PATIENT too and
                // only its subject_kind tells it apart. This clause used to be source alone under a
                // comment promising both, which held only because no angel link has ever carried a
                // local_id; the promise is now the query rather than a property of today's data.
                //
                // A null subject_kind reads as PATIENT, for DirectoryProjectionService
                // .keepsAPatientRecord's reason: links written before 2026-09-05 carry no kind and
                // every one of them was created by the path that made any patient-stream subject a
                // patient. Defaulting the other way would strand exactly the oldest rows this
                // backfill exists for.
                //
                // ⚠ Deliberately NOT erased_at-scoped, which is where this stops copying
                // keepsAPatientRecord and the difference is the point. That guard stops the
                // reconciliation REBUILDING a record for a subject whose far side is gone. This
                // unit rebuilds nothing: the row already exists, and DirectoryLink.erasedAt's own
                // javadoc says the Patient is deliberately kept as "an administrator's record with
                // an operational history of its own". Excluding it would leave a live record
                // permanently unsaveable and ERROR-reported on every start — which is the state
                // this unit exists to end, applied to the one population that cannot escape it.
                new Query(
                    new Criteria().andOperator(
                        Criteria.where("source").is("HC_PATIENT"),
                        Criteria.where(LOCAL_ID).ne(null),
                        Criteria.where(ACCOUNT_ID).ne(null),
                        new Criteria().orOperator(Criteria.where(SUBJECT_KIND).is(null), Criteria.where(SUBJECT_KIND).is(PATIENT_KIND))
                    )
                ),
                Document.class,
                DIRECTORY_LINK
            )
            .forEach(link -> {
                String accountId = link.getString(ACCOUNT_ID);
                if (accountId != null && !accountId.isBlank()) {
                    accountIdByLocalId.put(String.valueOf(link.get(LOCAL_ID)), accountId);
                }
            });

        int resolved = 0;
        List<String> unresolved = new ArrayList<>();
        for (Document patient : patients) {
            String id = String.valueOf(patient.get("_id"));
            String accountId = accountIdByLocalId.get(id);
            if (accountId == null) {
                unresolved.add(id);
                continue;
            }
            mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(patient.get("_id"))),
                new Update().set(ACCOUNT_ID, accountId),
                PATIENT
            );
            resolved++;
        }

        if (resolved > 0) {
            LOG.warn(
                "Backfilled account_id onto {} patient row(s) from directory_link — a targeted $set; every other " + "field is untouched",
                resolved
            );
        }
        if (!unresolved.isEmpty()) {
            // Record ids only — a patient id is this service's own opaque identifier, the same value
            // the console puts in a URL; no name, address or correlation key rides with it.
            List<String> named = unresolved.subList(0, Math.min(unresolved.size(), REPORTED_IDS_CAP));
            LOG.error(
                "{} patient row(s) have no account_id and no directory_link that supplies one: {}{}. They remain " +
                    "readable but cannot be saved until the field is set — an administrator's edit will be refused and " +
                    "sibling-stream updates are skipped. Remedy: the value is the person's hc-patient gateway account " +
                    "User.id and must arrive from that side — a later patient-events frame about the subject teaches " +
                    "directory_link.account_id and the next start resolves the row — or be set deliberately by an " +
                    "operator who has looked the account up. NEVER invent one: a fabricated account id is a join key " +
                    "that silently matches nothing (item 26's defect with a new name). This backfill runs on every " +
                    "start and will keep reporting these rows until they are resolved.",
                unresolved.size(),
                named,
                unresolved.size() > named.size() ? " and " + (unresolved.size() - named.size()) + " more" : ""
            );
        }
    }

    /**
     * Deliberately does nothing.
     *
     * <p>Every value the forward run wrote came from {@code directory_link.account_id} — real data
     * with an owner, not something this unit computed — so unsetting it would manufacture exactly
     * the invalid-by-domain rows the forward run exists to retire, on rows an administrator may have
     * edited since (every such edit carried the value through {@code @Valid}). A wrong stamp would
     * mean the <em>link</em> was wrong, which is a consumer defect to fix at its source, not a state
     * a rollback hook can decide about.
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("patient-account-id-backfill is not rolled back automatically — see the class comment");
    }
}
