package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.WageRateRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * {@link ProfessionalRoleRenameMigration} rewrites the stored {@code CAREGIVER} on both collections
 * that hold one, touches nothing else, and can be run twice — backlog item 35, decision D3.
 *
 * <p>The class is constructed directly rather than left to Mongock, for the reason its two
 * neighbours' tests give: Mongock's discovery and its changelog are Mongock's behaviour, and running
 * the unit by hand is the only way to choose the state of the database it meets. It is also the only
 * way to write a {@code CAREGIVER} at all — the constant is gone, so the mapped types can no longer
 * express the shape this exists to fix, and every fixture here is a raw {@link Document}.
 *
 * <p><b>{@link #aStoredCaregiverCannotBeReadAtAllUntilThisHasRun} is the test that makes the rest
 * worth having.</b> Without it every other assertion here is satisfied by a migration that does
 * nothing on a database where nothing was wrong. It asserts the premise instead: the row the rename
 * leaves behind does not read as a wrong value, it does not read at all.
 *
 * <p><b>Rows are removed by id rather than by dropping the collections.</b> {@code professional} and
 * {@code wage_rate} are shared with every other integration test in the JVM, which reuses one
 * container, and {@code DirectoryLinkIndexes} and friends build indexes on collections that a drop
 * would silently take with it.
 */
@IntegrationTest
class ProfessionalRoleRenameMigrationIT {

    private static final String PROFESSIONAL = "professional";

    private static final String WAGE_RATE = "wage_rate";

    /** Every row this file writes carries it, so cleanup is exact rather than wholesale. */
    private static final String FIXTURE_PREFIX = "rn-";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private WageRateRepository wageRateRepository;

    @AfterEach
    void cleanup() {
        Query fixtures = new Query(Criteria.where("_id").regex("^" + FIXTURE_PREFIX));
        mongoTemplate.remove(fixtures, PROFESSIONAL);
        mongoTemplate.remove(fixtures, WAGE_RATE);
    }

    private ProfessionalRoleRenameMigration migration() {
        return new ProfessionalRoleRenameMigration(mongoTemplate);
    }

    private void insertProfessional(String id, String role) {
        mongoTemplate
            .getCollection(PROFESSIONAL)
            .insertOne(new Document("_id", FIXTURE_PREFIX + id).append("role", role).append("licence_number", "LIC-" + id));
    }

    private void insertWageRate(String id, String role, int amount) {
        mongoTemplate
            .getCollection(WAGE_RATE)
            .insertOne(
                new Document("_id", FIXTURE_PREFIX + id)
                    .append("role", role)
                    .append("shift_type", "DAY")
                    .append("amount", amount)
                    .append("currency", "GHS")
                    .append("valid_from", "2026-01-01")
            );
    }

    private String storedRole(String collection, String id) {
        return mongoTemplate.findById(FIXTURE_PREFIX + id, Document.class, collection).getString("role");
    }

    /**
     * The premise, asserted rather than assumed.
     *
     * <p>A renamed enum constant does not leave a row holding a stale-but-readable value — it leaves a
     * row that fails to convert, on whichever read reaches it first, naming the field rather than the
     * document. That is why this is a migration and not a note in a release log, and it is the one
     * thing here that would still be true if the change unit were deleted.
     */
    @Test
    void aStoredCaregiverCannotBeReadAtAllUntilThisHasRun() {
        insertProfessional("unreadable", "CAREGIVER");

        assertThatThrownBy(() -> professionalRepository.findById(FIXTURE_PREFIX + "unreadable"))
            .as("the stored value is what fails, so it should be in the message")
            .hasMessageContaining("CAREGIVER");

        migration().migrate();

        assertThat(professionalRepository.findById(FIXTURE_PREFIX + "unreadable"))
            .get()
            .satisfies(professional -> assertThat(professional.getRole()).isEqualTo(ProfessionalRole.CARER));
    }

    /** Both collections, because a migration that covered one of the two is this item's failure mode. */
    @Test
    void rewritesEveryCollectionThatStoresTheRole() {
        insertProfessional("p1", "CAREGIVER");
        insertWageRate("w1", "CAREGIVER", 200);

        migration().migrate();

        assertThat(storedRole(PROFESSIONAL, "p1")).isEqualTo("CARER");
        assertThat(storedRole(WAGE_RATE, "w1")).isEqualTo("CARER");
    }

    /**
     * Only the roles that were {@code CAREGIVER}, and only the {@code role} field.
     *
     * <p>{@code amount} is checked by name because it is money somebody authored: this is a targeted
     * {@code $set} rather than a mapped {@code save}, and the difference between the two is whether
     * every other field on the document survives.
     */
    @Test
    void leavesEveryOtherRoleAndEveryOtherFieldExactlyAsItFoundThem() {
        insertProfessional("doctor", "DOCTOR");
        insertWageRate("w2", "CAREGIVER", 200);

        migration().migrate();

        assertThat(storedRole(PROFESSIONAL, "doctor")).isEqualTo("DOCTOR");
        assertThat(wageRateRepository.findById(FIXTURE_PREFIX + "w2"))
            .get()
            .satisfies(rate -> {
                assertThat(rate.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
                assertThat(rate.getCurrency()).isEqualTo("GHS");
            });
    }

    /**
     * Idempotent on the criterion, not on a flag.
     *
     * <p>The two {@code updateMulti} calls are not in one transaction, so a failure between them is a
     * state this has to be safe to resume from; matching on {@code role: "CAREGIVER"} means the second
     * run finds nothing rather than finding rows it must decide not to touch.
     */
    @Test
    void runningItTwiceChangesNothingTheSecondTime() {
        insertWageRate("w3", "CAREGIVER", 300);

        migration().migrate();
        migration().migrate();

        assertThat(storedRole(WAGE_RATE, "w3")).isEqualTo("CARER");
        assertThat(mongoTemplate.count(new Query(Criteria.where("_id").regex("^" + FIXTURE_PREFIX)), WAGE_RATE)).isEqualTo(1);
    }

    /**
     * The rollback is the genuine inverse here, unlike its neighbours' deliberate no-ops.
     *
     * <p>Mongock calls it when the forward execution throws, and a half-renamed database is exactly
     * the state two non-atomic writes can leave.
     */
    @Test
    void theRollbackPutsTheOldNameBack() {
        insertProfessional("p4", "CAREGIVER");

        migration().migrate();
        migration().rollback();

        assertThat(storedRole(PROFESSIONAL, "p4")).isEqualTo("CAREGIVER");
    }

    /**
     * The survey, pinned.
     *
     * <p>A collection missing from this list is a set of rows nobody can read and nothing reports, so
     * the list is asserted rather than left to the javadoc that explains it. {@code duty_roster} is
     * deliberately absent: it outlives its deleted entity and holds the retired {@code DutyRole},
     * which is a different vocabulary and never had a {@code CAREGIVER} in it.
     */
    @Test
    void sweepsExactlyTheTwoCollectionsThatStoreTheEnum() {
        assertThat(ProfessionalRoleRenameMigration.COLLECTIONS).containsExactly(PROFESSIONAL, WAGE_RATE);
    }
}
