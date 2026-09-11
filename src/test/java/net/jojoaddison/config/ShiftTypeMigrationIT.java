package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.enumeration.ShiftType;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * {@link ShiftTypeMigration} backfills the one column the 2026-09-04 change added, reports
 * unparseable stored shift values, and rewrites nothing else.
 *
 * <p>This constructs the class directly rather than letting the context run it. That was described
 * here as a consequence of the profile exclusion until 2026-09-04 and it never was one: Spring Boot
 * does not invoke {@link org.springframework.boot.ApplicationRunner} beans under
 * {@code @SpringBootTest}, whatever profile the bean registers on, so calling {@code run} is the only
 * way to exercise it from a test at all. It is also the only way to hand it a value the enum cannot
 * express, since the enum is the only way to write one otherwise. See
 * {@link ShiftTypeMigrationProfileTest} for what the annotation does and does not exclude.
 *
 * <p><b>Two assertions matter and they pull opposite ways</b>, which is why both are here. On the
 * roster collections the negative one is the point: adding {@code FLEXIBLE} rewrote nothing, and a
 * migration that quietly started rewriting rows would be far worse than one that does not, so the
 * row count and the stored values are checked either side of the run rather than only the fact that
 * it did not throw. On {@code wage_rate} the positive one is: the same release made
 * {@code shift_type} required, every row written before it has none, and one such row takes down
 * every earnings screen through {@code WageRateService.rateTableUpTo}'s {@code EnumMap}. The
 * migration covered the two collections that needed nothing and not the one that did, which is the
 * failure its own Javadoc had described in the abstract — "the migration was empty" and "the
 * migration was forgotten" look identical from a green build.
 */
@IntegrationTest
class ShiftTypeMigrationIT {

    private static final String COLLECTION = "shift_assignment";

    private static final String WAGE_RATES = "wage_rate";

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanup() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(WAGE_RATES);
    }

    private ShiftTypeMigration migration() {
        return new ShiftTypeMigration(mongoTemplate);
    }

    @Test
    void leavesEveryKnownShiftValueExactlyAsItFoundIt() {
        for (ShiftType shiftType : ShiftType.values()) {
            mongoTemplate.insert(new Document("_id", "sa-" + shiftType).append("shift", shiftType.name()), COLLECTION);
        }

        migration().run(null);

        List<Document> stored = mongoTemplate.findAll(Document.class, COLLECTION);
        assertThat(stored).hasSize(ShiftType.values().length);
        assertThat(stored.stream().map(document -> document.getString("shift"))).containsExactlyInAnyOrder(
            java.util.Arrays.stream(ShiftType.values())
                .map(Enum::name)
                .toArray(String[]::new)
        );
    }

    /**
     * A value the enum cannot express is reported and <b>left alone</b>.
     *
     * <p>Guessing would be the wrong behaviour here and this pins that it does not: hc-professional's
     * equivalent could map MORNING and AFTERNOON because somebody decided which surviving window each
     * one overlapped most, and no such decision exists for a string nobody has seen. Rewriting it to
     * a plausible neighbour would destroy the only evidence of what it had been.
     */
    @Test
    void leavesAnUnknownValueInPlaceRatherThanGuessingAtIt() {
        mongoTemplate.insert(new Document("_id", "sa-legacy").append("shift", "TWILIGHT"), COLLECTION);

        assertThatCode(() -> migration().run(null)).doesNotThrowAnyException();

        assertThat(mongoTemplate.findAll(Document.class, COLLECTION).getFirst().getString("shift")).isEqualTo("TWILIGHT");
    }

    /**
     * <b>The one rewrite this migration does.</b> A {@code wage_rate} row written before the
     * shift-type dimension has no {@code shift_type}, and {@code WageRateService.rateTableUpTo}
     * groups rates into an {@code EnumMap}, which rejects a null key — so a single such row makes
     * the wage-rates screen and every earnings screen answer 500 with a stack trace naming an
     * {@code EnumMap} rather than a row.
     *
     * <p>It becomes a {@code DAY} rate, and the other four cells stay unpriced. Fanning it out
     * across the worked shift types would preserve every valuation by inventing four prices nobody
     * authored, which is the claim the second dimension exists to stop making; see
     * {@link ShiftTypeMigration} for the reasoning and for what the choice costs.
     */
    @Test
    void backfillsAWageRateRowThatPredatesTheShiftTypeDimension() {
        mongoTemplate.insert(new Document("_id", "wr-legacy").append("role", "NURSE").append("amount", 300), WAGE_RATES);

        migration().run(null);

        Document stored = mongoTemplate.findAll(Document.class, WAGE_RATES).getFirst();
        assertThat(stored.getString("shift_type")).isEqualTo(ShiftType.DAY.name());
        // Nothing else about the row moved: this is a backfill, not a rewrite.
        assertThat(stored.getString("role")).isEqualTo("NURSE");
        assertThat(stored.getInteger("amount")).isEqualTo(300);
    }

    /**
     * A row that already has a shift type is left exactly as it is, whichever one it holds.
     *
     * <p>Without this the backfill could be written as "set every row to DAY" and still pass the
     * case above — which would silently reprice every night in the collection to the day cell, the
     * worst outcome available. An explicitly null field is covered in the same sweep as a missing
     * one, because {@code is(null)} matches both and only one of them is producible by a document
     * written before the field existed.
     */
    @Test
    void leavesAWageRateThatAlreadyCarriesAShiftTypeAlone() {
        mongoTemplate.insert(new Document("_id", "wr-night").append("role", "NURSE").append("shift_type", "NIGHT"), WAGE_RATES);
        mongoTemplate.insert(new Document("_id", "wr-null").append("role", "NURSE").append("shift_type", null), WAGE_RATES);

        migration().run(null);

        assertThat(mongoTemplate.findAll(Document.class, WAGE_RATES))
            .extracting(document -> document.getString("shift_type"))
            .containsExactlyInAnyOrder(ShiftType.NIGHT.name(), ShiftType.DAY.name());
    }

    /**
     * Running twice changes nothing the first run did not, so a restart is free.
     *
     * <p>An {@link org.springframework.boot.ApplicationRunner} runs on every start and has no record
     * of having run, so idempotence cannot be arranged — it has to be a property of the query. It is:
     * the backfill matches only rows whose {@code shift_type} is missing or null, and after the first
     * run there are none.
     */
    @Test
    void backfillsIdempotently() {
        mongoTemplate.insert(new Document("_id", "wr-legacy").append("role", "NURSE").append("amount", 300), WAGE_RATES);

        migration().run(null);
        migration().run(null);

        assertThat(mongoTemplate.findAll(Document.class, WAGE_RATES))
            .singleElement()
            .satisfies(document -> assertThat(document.getString("shift_type")).isEqualTo(ShiftType.DAY.name()));
    }

    @Test
    void runsHappilyAgainstCollectionsThatDoNotExist() {
        // A fresh database, or a deployment where the console has never been used. A migration that
        // needs its own subject to exist is a migration that fails on exactly the install it is for.
        assertThatCode(() -> migration().run(null)).doesNotThrowAnyException();
    }
}
