package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.config.ApplicationProperties;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * {@link ServicePlanCodeBackfillMigration} stamps only what it recognises, refuses the rest without
 * writing anything, and can be run twice — backlog item 56.
 *
 * <p>The class is constructed directly rather than left to Mongock, for the same reason
 * {@code ShiftTypeMigrationIT} constructs its subject: it is the only way to hand it a database in a state
 * this fixture chooses, and the only way to reach the refusal paths at all. Mongock's own handling of it
 * — that it is discovered, that it is recorded, that {@code runAlways} makes the gate reversible — is
 * Mongock's behaviour and not this class's.
 *
 * <p><b>The fixture is production's shape and not the seed's.</b> The {@code test} seed's three plans
 * already carry {@code code}, so they exercise nothing here but the skip. What production holds, per
 * {@code ServicePlan}'s own javadoc, is {@code Bridge Essential} / {@code Bridge Plus} /
 * {@code Bridge Family} at GHS 320 / 680 / 1,240, with the retired {@code tier} value still on the
 * document and no {@code code} — so that is what these rows are.
 *
 * <p><b>Rows are removed by id rather than by dropping the collection</b>, which is where this file
 * departs from {@code ShiftTypeMigrationIT}'s pattern on purpose. {@code ServicePlanIndexes} creates the
 * unique sparse index on {@code code} once, at {@code ApplicationReadyEvent}; dropping {@code service_plan}
 * would take that index with it for every later test sharing the container, and nothing would say so.
 */
@IntegrationTest
class ServicePlanCodeBackfillMigrationIT {

    private static final String SERVICE_PLAN = "service_plan";

    /** Every row this file writes carries it, so cleanup can be exact rather than wholesale. */
    private static final String FIXTURE_PREFIX = "bf-";

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanup() {
        mongoTemplate.remove(new Query(Criteria.where("_id").regex("^" + FIXTURE_PREFIX)), SERVICE_PLAN);
    }

    /** The gate, on. The change unit reads it through {@link ApplicationProperties} and nothing else. */
    private ServicePlanCodeBackfillMigration enabled() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getServicePlanCodeBackfill().setEnabled(true);
        return new ServicePlanCodeBackfillMigration(mongoTemplate, properties);
    }

    /** The gate as it ships. */
    private ServicePlanCodeBackfillMigration asShipped() {
        return new ServicePlanCodeBackfillMigration(mongoTemplate, new ApplicationProperties());
    }

    private void insert(String id, String name, String tier, String price) {
        Document plan = new Document("_id", FIXTURE_PREFIX + id).append("name", name).append("currency", "GHS");
        if (tier != null) {
            plan.append("tier", tier);
        }
        if (price != null) {
            plan.append("monthly_price", new Decimal128(new BigDecimal(price)));
        }
        mongoTemplate.getCollection(SERVICE_PLAN).insertOne(plan);
    }

    private void insertTheThreeBridgePlans() {
        insert("essential", "Bridge Essential", "ESSENTIAL", "320");
        insert("plus", "Bridge Plus", "PLUS", "680");
        insert("family", "Bridge Family", "FAMILY", "1240");
    }

    private List<Document> fixtureRows() {
        return mongoTemplate.find(new Query(Criteria.where("_id").regex("^" + FIXTURE_PREFIX)), Document.class, SERVICE_PLAN);
    }

    private Document row(String id) {
        return mongoTemplate.findById(FIXTURE_PREFIX + id, Document.class, SERVICE_PLAN);
    }

    /**
     * The three legacy rows gain their published codes and keep everything else.
     *
     * <p>{@code monthlyPrice} is asserted beside the code deliberately: item 56 names it as the field that
     * must survive the stamp — it is the one figure hc-admin still owns, the catalogue sync never writes
     * it, and the dashboard's plan-mix revenue column is computed from it. A migration that adopted
     * Abofonsa's prices in passing would be a repricing wearing a backfill's name, and this is what would
     * catch it.
     */
    @Test
    void stampsEachRecognisedPlanAndChangesNothingElseOnIt() {
        insertTheThreeBridgePlans();

        enabled().migrate();

        assertThat(row("essential").getString("code")).isEqualTo("PEAR");
        assertThat(row("plus").getString("code")).isEqualTo("PAWPAW");
        assertThat(row("family").getString("code")).isEqualTo("MELON");

        assertThat(row("essential").get("monthly_price")).hasToString("320");
        assertThat(row("plus").get("monthly_price")).hasToString("680");
        assertThat(row("family").get("monthly_price")).hasToString("1240");
        // The retired PlanTier value is left on the document. It is the strongest evidence of what each
        // row was, and a rerun of a wrongly-reviewed mapping needs it as much as the first run did.
        assertThat(row("essential").getString("tier")).isEqualTo("ESSENTIAL");
    }

    /**
     * A plan whose {@code tier} has already been rewritten away is still recognised, by name.
     *
     * <p>Not a hypothetical: item 51 removed {@code tier} from the mapped class, so any plan an
     * administrator has edited through the console since that deploy has had it dropped by the mapped
     * save. A rule resting on {@code tier} alone would silently refuse exactly the rows somebody had
     * touched most recently.
     */
    @Test
    void recognisesAPlanWhoseRetiredTierFieldHasAlreadyBeenDroppedByAnEdit() {
        insert("edited", "Bridge Plus", null, "680");

        enabled().migrate();

        assertThat(row("edited").getString("code")).isEqualTo("PAWPAW");
    }

    /**
     * Running it twice is running it once.
     *
     * <p>Asserted on the whole documents rather than on the codes: "the codes are still right" would pass
     * against a second run that rewrote every field back to the same value, and the property wanted here
     * is that the second run finds no codeless plan and touches nothing at all.
     */
    @Test
    void isANoOpOnASecondRun() {
        insertTheThreeBridgePlans();
        ServicePlanCodeBackfillMigration migration = enabled();

        migration.migrate();
        List<Document> afterFirst = fixtureRows();

        assertThatCode(migration::migrate).doesNotThrowAnyException();

        assertThat(fixtureRows()).containsExactlyInAnyOrderElementsOf(afterFirst);
    }

    /**
     * A plan that already has a code is not a candidate at all.
     *
     * <p>The state a second deploy, a hand edit or an already-run sync leaves behind. Mutated on its own,
     * with no codeless row beside it, so the assertion is about the selection and not about the stamping.
     */
    @Test
    void leavesAPlanThatAlreadyCarriesACodeExactlyAsItFoundIt() {
        insert("published", "PEAR Plan", null, "3000");
        mongoTemplate.updateFirst(
            new Query(Criteria.where("_id").is(FIXTURE_PREFIX + "published")),
            new Update().set("code", "PEAR"),
            SERVICE_PLAN
        );
        Document before = row("published");

        enabled().migrate();

        assertThat(row("published")).isEqualTo(before);
    }

    /**
     * A plan this rule was not written for stops the whole run, and nothing is written.
     *
     * <p><b>The second half is the point.</b> Stamping the rows it understood and then failing on the one
     * it did not would leave a collection that reads as reconciled, with two tiers joined to Abofonsa and
     * one silently not — which is item 56's own failure mode one level up, and the reason the refusal is
     * computed in full before the first write.
     */
    @Test
    void refusesAnUnrecognisedPlanAndLeavesTheRecognisedOnesUnstamped() {
        insertTheThreeBridgePlans();
        insert("gold", "Bridge Gold", null, "2400");

        assertThatThrownBy(() -> enabled().migrate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("wrote nothing")
            .hasMessageContaining("Bridge Gold")
            .hasMessageContaining("matches no recogniser");

        assertThat(fixtureRows()).allSatisfy(plan -> assertThat(plan.getString("code")).isNull());
    }

    /**
     * A plan that two recognisers both claim is refused rather than resolved by whichever came first.
     *
     * <p>The list is ordered, so "take the first match" would be a rule with an answer for this and no
     * evidence behind it. A name and a tier that disagree mean the row's history is not what either key
     * says it is, which is precisely when a stamp would re-point the wrong patients.
     */
    @Test
    void refusesAPlanWhoseNameAndTierPointAtDifferentCodes() {
        insert("confused", "Bridge Plus", "FAMILY", "900");

        assertThatThrownBy(() -> enabled().migrate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PAWPAW")
            .hasMessageContaining("MELON");

        assertThat(row("confused").getString("code")).isNull();
    }

    /**
     * Two codeless plans resolving to one code are refused, because the index would not have caught it.
     *
     * <p>{@code ServicePlanIndexes}' unique index is sparse and is created after Mongock has run, so on a
     * first start there is nothing between this migration and two rows claiming one published tier — with
     * that tier's subscribers split across two plausible-looking cards on the plan mix.
     */
    @Test
    void refusesTwoCodelessPlansThatResolveToTheSameCode() {
        insert("essential", "Bridge Essential", "ESSENTIAL", "320");
        insert("essential-copy", "Bridge Essential", "ESSENTIAL", "330");

        assertThatThrownBy(() -> enabled().migrate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("both resolve to PEAR");

        assertThat(fixtureRows()).allSatisfy(plan -> assertThat(plan.getString("code")).isNull());
    }

    /**
     * A code another plan already holds is refused too, and that is a different refusal from the one above.
     *
     * <p>Half-run, hand-edited and partly-synced databases all reach this state, and stamping into it would
     * create the duplicate the whole item exists to prevent rather than the one it was asked to reconcile.
     */
    @Test
    void refusesACodeAnotherPlanAlreadyHolds() {
        insert("published", "PEAR Plan", null, "3000");
        mongoTemplate.updateFirst(
            new Query(Criteria.where("_id").is(FIXTURE_PREFIX + "published")),
            new Update().set("code", "PEAR"),
            SERVICE_PLAN
        );
        insert("essential", "Bridge Essential", "ESSENTIAL", "320");

        assertThatThrownBy(() -> enabled().migrate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("which another plan already holds");

        assertThat(row("essential").getString("code")).isNull();
    }

    /**
     * As shipped it does nothing at all, which is the decision rather than a default nobody chose.
     *
     * <p>A change unit runs wherever the application runs, so without this gate the matching rule above
     * would execute on the next deploy of this service — before anybody had compared it against the plans
     * production actually holds, and with a throw on the unrecognised path that would be an outage rather
     * than a report. Every environment starts with it off; turning it on is a deliberate act, and
     * {@code runAlways = true} on the change unit is what keeps that act possible after Mongock has already
     * recorded a disabled run.
     */
    @Test
    void doesNothingWhileTheGateIsOff() {
        insertTheThreeBridgePlans();
        insert("gold", "Bridge Gold", null, "2400");

        // Not even the unrecognisable row throws: nothing is read and nothing is decided.
        assertThatCode(() -> asShipped().migrate()).doesNotThrowAnyException();

        assertThat(fixtureRows())
            .hasSize(4)
            .allSatisfy(plan -> assertThat(plan.getString("code")).isNull());
    }
}
