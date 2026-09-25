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
 * <h2>When this runs: as an {@code ApplicationRunner}, NOT during context refresh</h2>
 *
 * <p>Mongock executes after the context has refreshed and after Boot has logged {@code Started
 * HcAdminServiceApp}. Its runner bean is {@code MongockApplicationRunner}, which implements
 * {@link org.springframework.boot.ApplicationRunner} and is created by {@code MongockContextBase
 * .applicationRunner}, guarded by the condition {@code
 * ConditionalOnExpression("'${mongock.runner-type:ApplicationRunner}'.toLowerCase().equals('applicationrunner')")}.
 * {@code mongock.runner-type} is set nowhere — in none of this repository's four
 * {@code application*.yml} files, nor in {@code src/test/resources/config/application.yml}, nor in
 * any compose file — so Mongock's own {@code ApplicationRunner} default is what holds.
 *
 * <p>So on a {@code dev}/{@code test} stack this unit runs <b>after</b> {@code
 * DevelopmentDataInitializer}, which is an {@code ApplicationRunner} too. Neither carries an {@code
 * Order} annotation nor implements {@code Ordered} — nor does Mongock's {@code @Bean} method, which
 * is where Boot would look next — so the seed is simply the earlier of two equally-ordered runners,
 * not the deliberately earlier one.
 *
 * <p>⚠ <b>Nothing makes that tie stable, and in particular it is NOT bean-definition registration
 * order.</b> Boot 4.1's {@code SpringApplication.callRunners} collects the runners into an
 * {@code IdentityHashMap} and sorts {@code keySet().stream()}, so the encounter order its stable
 * sort preserves is that map's — which {@code IdentityHashMap} documents as unspecified. Boot 3.x
 * built the list from {@code getBeansOfType}, a {@code LinkedHashMap} in registration order, and
 * <em>did</em> preserve it; do not carry that intuition forward. So the order stated above is
 * <b>observed</b> — item 115's roll and the 2026-09-25 quality start — and not guaranteed. It is
 * harmless if it ever inverts: Mongock first on an empty database finds no patients and returns at
 * the early exit, and on a restart every seeded row already carries the field.
 *
 * <p>⚠ Setting {@code mongock.runner-type} to {@code
 * InitializingBean} selects {@code MongockInitializingBeanRunner}, which executes <em>during</em>
 * refresh and reverses this section and the next entirely. {@code ShiftTypeMigrationProfileTest
 * .mongockRunsAsAnApplicationRunnerAfterTheSeedAndNothingReordersThem} pins that property's absence,
 * and the four ordering facts above with it.
 *
 * <h2>The log invites the opposite conclusion, and three documents drew it</h2>
 *
 * <p>Backlog item 138. The word "Mongock" is printed twice per start and the two lines mean
 * different things. {@code RunnerBuilderBase: Mongock runner COMMUNITY version[[]]} is logged when
 * the {@code @Bean} is <em>built</em>, during refresh, seconds before {@code Started} — which reads
 * exactly like "Mongock ran at refresh" and is not that. The execution is {@code MigrateExecutorBase:
 * Mongock starting the data migration sequence}. <b>Grep a startup log for that line, never for
 * "Mongock".</b>
 *
 * <h2>{@code runAlways = true} — one justification is demonstrable, one is not</h2>
 *
 * <p><b>Demonstrable, and observed.</b> A one-shot unit would log the unresolved rows once, on one
 * deploy, and never look again — while the very mechanism that resolves them keeps running: any
 * later frame about the subject teaches the link its {@code account_id} ({@code
 * DirectoryProjectionService.recordEvent}), after which the next start resolves rows this one could
 * not. The 2026-09-25 quality start stamped exactly one row: the only patient on that stack the seed
 * did not write.
 *
 * <p><b>Not demonstrable on any stack this repository can run.</b> The other half of the argument —
 * that re-running re-reports what still stands, keeping a standing data problem visible instead of
 * buried in one deploy's log — needs the ERROR branch above to execute, and <b>no patient the seed
 * writes can reach it</b>: the seed runs first (see the ordering section) and writes an
 * {@code account_id} onto every row it writes, and after item 115 every path that creates a patient
 * requires the field. The only rows that can reach it are pre-115 leftovers whose link cannot
 * resolve them — and where a stack holds none, the run ends at the {@code patients.isEmpty()} early
 * return, <em>before</em> the link read, so there is nothing to report and no report is made.
 *
 * <p>⚠ <b>That is a claim about rows, not about stacks — do not restate it as "the branch never
 * executes on a seeded stack".</b> A seeded stack can hold rows the seed did not write: the quality
 * database holds sixteen patients, fifteen seeded and one learned from a sibling event. Such a row
 * reaches this ERROR whenever its link cannot supply the value, and today's is resolvable only
 * because {@code directory_link.account_id} exists — a column added by item 130, so before that a
 * start on that very stack would have executed this branch. What is true without qualification is
 * that the branch's one live <em>home</em> is <b>production</b>, which runs {@code prod}, where
 * {@code DevelopmentDataInitializer} ({@code @Profile({dev, test})}) does not exist and there is no
 * seed at all.
 *
 * <p>⚠ <b>Do not close that gap by reordering the two runners, or by making the seed skip rows that
 * already carry a value</b> — item 138 forbids both, the seed is {@code saveAll} by design, and this
 * unit is correct: what is unreachable is a report, not a repair. A seeded fixture cannot reach it
 * either, which is why none was added — {@code Patient.accountId} is {@code @NotNull} and {@code
 * ValidatingMongoEventListener} refuses a mapped save without one, so an accountId-less seeded
 * patient cannot be written by {@code saveAll} at all.
 *
 * <p>It is idempotent by selection — only rows missing the value are read — so a start with nothing
 * missing costs one query and says nothing. Unlike {@code ServicePlanCodeBackfillMigration} there is
 * no gate flag: that unit's matching rule was inferred and dangerous, this one's join ({@code
 * directory_link.local_id = patient._id}, same source {@code
 * DirectoryProjectionService.createAndClaim} claims by) is the estate's own, and a wrong outcome
 * here is an absent stamp, never a wrong one.
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

    /**
     * How many unresolved row ids one ERROR line names before summarising — a bound, not a filter.
     *
     * <p>⚠ <b>The truncating branch and its {@code " and N more"} suffix are exercised nowhere at
     * all.</b> No test drives more than twenty unresolved rows, and no stack reaches the ERROR
     * branch in the first place — see the ordering section of the class comment. Reaching either
     * needs twenty-one unresolvable patients at once, which today is a production state only.
     * Backlog item 138 weighed this and accepted it.
     */
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
