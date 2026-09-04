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
 * {@link ShiftTypeMigration} reports unparseable stored shift values and rewrites nothing.
 *
 * <p>The class is profile-excluded from {@code test} so it cannot log against another test's
 * fixtures mid-run, so this constructs it directly — which is also the only way to hand it a value
 * the enum cannot express, since the enum is the only way to write one otherwise.
 *
 * <p><b>The assertion that matters is the negative one.</b> Adding {@code FLEXIBLE} on 2026-09-04
 * rewrote nothing, and a migration that quietly started rewriting rows would be far worse than the
 * one that does not: the two are indistinguishable from a passing build, so the row count and the
 * stored values are checked either side of the run rather than only the fact that it did not throw.
 */
@IntegrationTest
class ShiftTypeMigrationIT {

    private static final String COLLECTION = "shift_assignment";

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanup() {
        mongoTemplate.dropCollection(COLLECTION);
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
        assertThat(stored.stream().map(document -> document.getString("shift")))
            .containsExactlyInAnyOrder(java.util.Arrays.stream(ShiftType.values()).map(Enum::name).toArray(String[]::new));
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

    @Test
    void runsHappilyAgainstCollectionsThatDoNotExist() {
        // A fresh database, or a deployment where the console has never been used. A migration that
        // needs its own subject to exist is a migration that fails on exactly the install it is for.
        assertThatCode(() -> migration().run(null)).doesNotThrowAnyException();
    }
}
