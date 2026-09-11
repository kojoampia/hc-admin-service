package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * <b>A record that can exist without a person on it has to exist without one in the fixture.</b>
 *
 * <p>This is backlog item 52, which is the generalisation of items 45, 47, 48, 49 and 50: five times
 * in three weeks, a change was made so that a state would render correctly, and that state was
 * reachable on no stack anybody could drive. The fix went to production unseen, and in two of the
 * five the state the change <em>produced</em> was still not in the fixture afterwards. Item 49 is the
 * one this class was written for — {@code professional-detail.ts}'s {@code headingName()} grew two
 * branches below the name, and all nine seeded professionals carried a {@code Profile}, so on
 * {@code quality/}, on {@code deploy/e2e/compose.yml} and under {@code ng serve} the only branch that
 * could ever render was the one that already worked.
 *
 * <h2>Why this rule and not a wider one</h2>
 *
 * <p>The obvious generalisation is "no rendering branch may be unreachable from the fixture", and it
 * was measured before it was rejected. The {@code test} profile seeds <b>162 optional fields across
 * 26 collections, and 118 of them are uniform</b> — 89 populated on every record, 29 on none. Of the
 * 89, roughly 45 are bound somewhere under {@code app/}'s {@code entities/directory/} or
 * {@code console/} with a fallback. So a sweep of that shape would fire about ninety times, and
 * nearly all of it would be an em dash or a zero that is obviously right and that no fix ever
 * depended on seeing. {@code record-identity.spec.ts} makes the argument in the other repository and
 * it holds here: <i>a rule that fires only where it cannot matter teaches that the rule is noise.</i>
 *
 * <p>Unreachability is normal. What is <b>not</b> normal is a record whose <em>identity</em> is
 * unrenderable, because that is the one absence the console cannot answer with a dash — it has to
 * choose a substitute, and every one of the five occurrences is a screen choosing a bad one. So the
 * rule is scoped to exactly that: {@link Profile} is this service's only source of a person's name,
 * and any document that may hold one and may not is a document some screen has to name without it.
 *
 * <h2>Why it is derived rather than listed</h2>
 *
 * <p>The pairs come from reflection over {@link DevelopmentDataInitializer.ProfileData} and the
 * domain classes, so an entity that gains an optional {@code Profile} is covered on the commit that
 * creates it rather than on the commit that notices. That is {@code PaginationIT}'s argument and
 * {@code record-identity.spec.ts}'s: a test whose coverage has to be extended by hand silently stops
 * covering things. <b>Do not replace the reflection with an enumeration.</b>
 *
 * <p>The discovery is pinned all the same, because a scan can fail as quietly as the rule can — a
 * renamed field or a moved class would leave this passing over nothing. The pin is exact rather than
 * a floor, so a new entity with an optional {@code Profile} reddens it by name and has to be
 * classified rather than absorbed.
 *
 * <h2>What it deliberately does not assert</h2>
 *
 * <p>Only that the <em>absent</em> state is present. The mirror — that some record carries one — is
 * not asserted, because the measurement above is lopsided: of the 118 uniform fields, 89 are
 * uniformly populated and every one of the five occurrences was in that direction. {@code Address}
 * carries an optional {@code Profile} back-reference that no seeded address populates and no screen
 * reads, and a two-sided rule would redden on it for nothing.
 *
 * <p><b>{@code test} only, not {@code dev}.</b> {@code test} is the console's own dataset and the
 * profile every stack short of production runs — {@code quality/compose.yml},
 * {@code deploy/e2e/compose.yml}, and {@code ng serve} against a locally-run api. {@code dev} seeds
 * no patients and no professionals at all, so requiring the state there would mean inventing a
 * directory in a profile that deliberately has none.
 *
 * <p>It says nothing about whether a screen renders the absence <em>well</em>. That is
 * {@code app/src/main/webapp/app/entities/directory/record-identity.spec.ts}, which forbids the bad
 * rendering, and the two are halves of one rule that cannot live in one repository: the fixture is
 * here and the screen is there, and the only place both exist at once is a running stack.
 */
class SeedNamelessRecordCoverageTest {

    private static final String SEED_DATA_LOCATION = "data/hc-admin-ms-data.json";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * One seeded collection, and one field on its documents that may hold a {@link Profile} and may
     * be absent.
     */
    private record NameBearer(String collection, String field) {
        @Override
        public String toString() {
            return collection + "." + field;
        }
    }

    /**
     * Every {@code (collection, field)} where the seed can write a document with nobody on it.
     *
     * <p>The collection name is {@link DevelopmentDataInitializer.ProfileData}'s own field name,
     * which is the JSON key Jackson binds — so the two cannot drift apart here without drifting
     * apart in the initializer as well.
     */
    private static List<NameBearer> nameBearers() {
        List<NameBearer> bearers = new ArrayList<>();
        for (java.lang.reflect.Field seeded : DevelopmentDataInitializer.ProfileData.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(seeded.getType())) {
                continue;
            }
            Type generic = seeded.getGenericType();
            if (!(generic instanceof ParameterizedType parameterized)) {
                continue;
            }
            if (!(parameterized.getActualTypeArguments()[0] instanceof Class<?> element)) {
                continue;
            }
            Arrays.stream(element.getDeclaredFields())
                .filter(field -> field.getType() == Profile.class)
                // A required reference cannot be absent, so no screen has to name the record without
                // one. Nothing declares this today; the filter is what keeps the rule true if
                // something does.
                .filter(field -> !field.isAnnotationPresent(NotNull.class))
                .forEach(field -> bearers.add(new NameBearer(seeded.getName(), field.getName())));
        }
        return bearers;
    }

    private Map<String, List<JsonNode>> readTestProfile() throws Exception {
        try (InputStream inputStream = new ClassPathResource(SEED_DATA_LOCATION).getInputStream()) {
            JsonNode test = mapper.readTree(inputStream).path("test");
            Map<String, List<JsonNode>> collections = new LinkedHashMap<>();
            test.properties().forEach(entry -> {
                List<JsonNode> records = new ArrayList<>();
                entry.getValue().forEach(records::add);
                collections.put(entry.getKey(), records);
            });
            return collections;
        }
    }

    /**
     * The scan found the record types this rule exists for, and found nothing it has not been
     * classified against.
     *
     * <p>Exact rather than a floor. A fourth entity gaining an optional {@code Profile} is a fourth
     * screen that has to name a record with nobody on it, and it should arrive as a red line naming
     * itself rather than as silent coverage nobody chose.
     */
    @Test
    void findsEveryRecordTypeThatCanExistWithoutAPerson() {
        assertThat(nameBearers())
            .extracting(NameBearer::toString)
            .as("a new optional Profile reference means a new screen that must name a record with nobody on it")
            .containsExactlyInAnyOrder("patients.profile", "professionals.profile", "addresses.profile");
    }

    /**
     * And the {@code test} fixture contains one of each, so the absence renders somewhere a person
     * can look at it.
     *
     * <p>Empty collections are skipped rather than failed. Three collections are seeded empty on
     * purpose and none of them carries a {@code Profile}; the case above is what stops a new one
     * arriving unnoticed, so this does not need to double as that check.
     */
    @Test
    void everyRecordTypeThatCanExistWithoutAPersonHasOneInTheTestFixture() throws Exception {
        Map<String, List<JsonNode>> test = readTestProfile();

        for (NameBearer bearer : nameBearers()) {
            List<JsonNode> records = test.getOrDefault(bearer.collection(), List.of());
            if (records.isEmpty()) {
                continue;
            }
            // The ids rather than the records: a failing `anySatisfy` prints every element it
            // rejected, which for `shiftAssignments` alone would be nine hundred documents and for
            // `professionals` is a screenful that names nothing useful. What a reader needs is the
            // short answer — nobody — and the description that says what to do about it.
            List<String> withNobodyOnThem = records
                .stream()
                .filter(record -> record.path(bearer.field()).isMissingNode() || record.path(bearer.field()).isNull())
                .map(record -> record.path("id").asText("<no id>"))
                .toList();

            assertThat(withNobodyOnThem)
                .as(
                    "%s is optional, so some screen has to name a record in `%s` with nobody on it — seed one, or that " +
                        "rendering is reachable on no stack short of production (backlog item 52)",
                    bearer,
                    bearer.collection()
                )
                .isNotEmpty();
        }
    }
}
