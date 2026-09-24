package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.repository.PatientRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * {@link PatientAccountIdBackfillMigration} stamps {@code account_id} from the link that names the
 * record, reports what it cannot resolve without defaulting it and without failing, and can be run
 * on every start — backlog item 115.
 *
 * <p>The class is constructed directly rather than left to Mongock, for the reason its neighbours'
 * tests give: Mongock's discovery and its changelog are Mongock's behaviour, and running the unit by
 * hand is the only way to choose the state of the database it meets. It is also the only way to
 * write the state this exists to fix — {@code Patient.accountId} is {@code @NotNull} and
 * {@code ValidatingMongoEventListener} refuses a mapped save without one, so every fixture here that
 * models a pre-item-115 row is a raw {@link Document}.
 *
 * <p><b>{@link #aRowMissingTheFieldCannotBeSavedThroughTheMappedTypeAtAll} is the premise, asserted
 * rather than assumed.</b> Without it every other assertion is satisfied by a migration that does
 * nothing on a database where nothing was wrong: it shows the unresolved row is not merely
 * incomplete but unwritable, which is why an absent stamp is a standing problem worth re-reporting
 * on every start.
 *
 * <p>Rows are removed by id rather than by dropping the collections, which are shared with every
 * other integration test in the JVM's reused container.
 */
@IntegrationTest
class PatientAccountIdBackfillMigrationIT {

    private static final String PATIENT = "patient";

    private static final String DIRECTORY_LINK = "directory_link";

    /** Every row this file writes carries it, so cleanup is exact rather than wholesale. */
    private static final String FIXTURE_PREFIX = "acctbf-";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PatientRepository patientRepository;

    @AfterEach
    void cleanup() {
        Query fixtures = new Query(Criteria.where("_id").regex("^" + FIXTURE_PREFIX));
        mongoTemplate.remove(fixtures, PATIENT);
        mongoTemplate.remove(fixtures, DIRECTORY_LINK);
    }

    private PatientAccountIdBackfillMigration migration() {
        return new PatientAccountIdBackfillMigration(mongoTemplate);
    }

    /** A row as every pre-item-115 deploy wrote it: no {@code account_id} at all. */
    private void insertLegacyPatient(String id) {
        mongoTemplate
            .getCollection(PATIENT)
            .insertOne(
                new Document("_id", FIXTURE_PREFIX + id)
                    .append("status", "ACTIVE")
                    .append("joined_on", "2026-01-01")
                    .append("case_count", 2)
            );
    }

    private void insertLink(String id, String source, String localId, String accountId) {
        Document link = new Document("_id", FIXTURE_PREFIX + id)
            .append("source", source)
            .append("external_key", FIXTURE_PREFIX + id + "@fixture.invalid")
            .append("subject_kind", "PATIENT");
        if (localId != null) {
            link.append("local_id", FIXTURE_PREFIX + localId);
        }
        if (accountId != null) {
            link.append("account_id", accountId);
        }
        mongoTemplate.getCollection(DIRECTORY_LINK).insertOne(link);
    }

    private Document storedPatient(String id) {
        return mongoTemplate.findById(FIXTURE_PREFIX + id, Document.class, PATIENT);
    }

    /**
     * The premise. A row without the field reads fine and refuses every mapped save — so before this
     * migration runs, the record is one no administrator can edit and no sibling frame can update,
     * and after it has, the same row round-trips.
     */
    @Test
    void aRowMissingTheFieldCannotBeSavedThroughTheMappedTypeAtAll() {
        insertLegacyPatient("premise");
        insertLink("premise-link", "HC_PATIENT", "premise", "6ab555b90202b7c99d2be2d6");

        Patient readable = patientRepository.findById(FIXTURE_PREFIX + "premise").orElseThrow();
        assertThatThrownBy(() -> patientRepository.save(readable))
            .as("the missing field is what refuses the save, so it should be in the message")
            .hasMessageContaining("accountId");

        migration().migrate();

        Patient healed = patientRepository.findById(FIXTURE_PREFIX + "premise").orElseThrow();
        assertThat(healed.getAccountId()).isEqualTo("6ab555b90202b7c99d2be2d6");
        patientRepository.save(healed); // must not throw any more
    }

    /** The stamp is targeted: the joined value lands and every other field survives byte for byte. */
    @Test
    void backfillsFromTheLinkThatNamesTheRecordAndTouchesNothingElse() {
        insertLegacyPatient("p1");
        insertLink("l1", "HC_PATIENT", "p1", "acc-p1");

        migration().migrate();

        Document after = storedPatient("p1");
        assertThat(after.getString("account_id")).isEqualTo("acc-p1");
        assertThat(after.getString("status")).isEqualTo("ACTIVE");
        assertThat(after.getInteger("case_count")).isEqualTo(2);
    }

    /**
     * <b>The item's own wording: a row it cannot resolve is REPORTED, not defaulted, and not
     * fatal.</b> Both unresolvable populations at once — no link at all (a console-created record
     * from before 2026-08-28), and a link that never learned an {@code account_id} (a subject whose
     * every frame predates hc-patient's refactor) — beside a resolvable row, because the half-done
     * outcome is the one worth pinning: the migration must stamp what it can and say what it could
     * not, in one run, without throwing.
     */
    @Test
    void aRowItCannotResolveIsReportedNotDefaultedAndNotFatal() {
        insertLegacyPatient("resolvable");
        insertLink("l-resolvable", "HC_PATIENT", "resolvable", "acc-resolvable");
        insertLegacyPatient("no-link");
        insertLegacyPatient("link-no-account");
        insertLink("l-empty", "HC_PATIENT", "link-no-account", null);

        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            migration().migrate(); // not fatal: any throw fails this test before the assertions
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(storedPatient("resolvable").getString("account_id")).as("what can be stamped is stamped").isEqualTo("acc-resolvable");
        assertThat(storedPatient("no-link").containsKey("account_id")).as("never defaulted — absent stays absent").isFalse();
        assertThat(storedPatient("link-no-account").containsKey("account_id")).as("a link with no account_id supplies nothing").isFalse();

        assertThat(appender.list)
            .as("reported: one ERROR naming the rows and the remedy")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                String line = event.getFormattedMessage();
                assertThat(line)
                    .contains(FIXTURE_PREFIX + "no-link")
                    .contains(FIXTURE_PREFIX + "link-no-account");
                assertThat(line).as("the remedy, not just the count").contains("directory_link").contains("NEVER invent");
                assertThat(line)
                    .as("the resolved row is not among the reported")
                    .doesNotContain(FIXTURE_PREFIX + "resolvable");
            });
    }

    /**
     * Only an {@code HC_PATIENT} link's {@code local_id} names a patient row. A link of another
     * source that happened to carry one must supply nothing — the join must not widen the day some
     * other source starts keeping local records.
     */
    @Test
    void aLinkOfAnotherSourceSuppliesNothing() {
        insertLegacyPatient("p2");
        insertLink("l2", "HC_PROFESSIONAL", "p2", "acc-wrong-side");

        migration().migrate();

        assertThat(storedPatient("p2").containsKey("account_id")).isFalse();
    }

    /**
     * Idempotent by selection: the second run reads only rows still missing the value, finds the
     * stamped one gone from its result set, and rewrites nothing — which is what {@code runAlways}
     * makes safe on every start forever.
     */
    @Test
    void runningItTwiceChangesNothingTheSecondTime() {
        insertLegacyPatient("p3");
        insertLink("l3", "HC_PATIENT", "p3", "acc-p3");

        migration().migrate();
        migration().migrate();

        assertThat(storedPatient("p3").getString("account_id")).isEqualTo("acc-p3");
        assertThat(mongoTemplate.count(new Query(Criteria.where("_id").regex("^" + FIXTURE_PREFIX)), PATIENT)).isEqualTo(1);
    }

    /** The rollback is a deliberate no-op: every stamp is real data with an owner and stays. */
    @Test
    void theRollbackLeavesEveryStampInPlace() {
        insertLegacyPatient("p4");
        insertLink("l4", "HC_PATIENT", "p4", "acc-p4");

        migration().migrate();
        migration().rollback();

        assertThat(storedPatient("p4").getString("account_id")).isEqualTo("acc-p4");
    }
}
