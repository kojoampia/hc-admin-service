package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The four products' {@code EntityChanged} envelopes, held to the shape they agreed — backlog item
 * 124(a).
 *
 * <p>Item 124 aligned the {@code admin.event} / {@code patient.event} / {@code professional.event} /
 * {@code vendor.event} envelopes by prose; its own "done when" says something mechanical must fail if
 * a fifth producer or a future edit diverges again. This class is that mechanism's build-time half:
 * one captured frame per product under {@code src/test/resources/event-frames/entity-changed/} —
 * a directory chosen because no JSON fixtures existed under test resources before this, and
 * {@code event-frames/entity-changed/} names both what the files are and which event they are, leaving
 * room for a future capture of another type beside rather than among them. The consume-time half is
 * item 109's schema validation (the architect's decision (c), 2026-09-24), which is deliberately not
 * built here — this fails at build time in this repo; that fails at consume time on the frame that
 * diverged.
 *
 * <h2>⚠ This test is a NARROWING, not a guarantee — a fixture is a copy</h2>
 *
 * <p>If a producer changes its frame and nobody updates the fixture, the fixture is stale and this
 * test goes on passing. It cannot prove any sibling still publishes what its copy shows; it proves
 * only that the envelope the four products agreed on, as read from each producer at the commits named
 * in the fixture directory's {@code README.md}, fails mechanically here if a fixture is added or
 * edited that diverges from it. Do not read it as a contract test. This repo has been bitten by
 * artefacts that looked like guarantees and were not (items 71 and 106).
 *
 * <h2>The two real divergences the frames make visible — recorded, deliberately not fixed</h2>
 *
 * <p><b>(i) {@code data.action} values do not agree.</b> Verified at each product's
 * {@code origin/main} on 2026-09-24: hc-admin publishes {@code SAVED}/{@code DELETED}; hc-patient,
 * hc-professional and hc-vendor each publish {@code CREATED}/{@code UPDATED}/{@code DELETED}
 * (hc-vendor's is a Java enum, wire form {@code Enum.name()}). hc-admin's {@code SAVED} is deliberate
 * and argued in {@link AdminEntityEvent#SAVED}'s javadoc — records here carry client-assigned ids
 * almost everywhere, so the siblings' id heuristic would report nearly every insert as
 * {@code UPDATED}. Do not "align" it. A consumer reading all four channels sees <b>four</b> values,
 * which is why the assertion below is that the key exists as a non-blank string and never that the
 * value is in a fixed set.
 *
 * <p><b>(ii) {@code source} has two naming conventions.</b> {@code hcAdminService} and
 * {@code hcPatientService} are camelCase; {@code hc-professional-service} and
 * {@code hc-vendor-service} are kebab-case. {@code source} is free-form by design, so only a
 * non-blank string is asserted — but a consumer switching on it needs those four exact values, which
 * is why they are written here and in the fixture README rather than left to be rediscovered.
 *
 * <h2>Why it derives its inputs instead of naming them</h2>
 *
 * <p>The frames are found by listing the directory, not by enumerating four paths — a fifth
 * producer's frame is covered the moment the file exists. {@code PaginationIT} is this repo's lesson:
 * its enumerated list of 23 paths silently stopped covering the eight entities generated after it was
 * written. The floor assertion below is the positive control that keeps an unmatched directory from
 * reading as a clean sweep — an empty listing must fail, not pass vacuously.
 *
 * <p>{@code occurredAt} is asserted to be an ISO-8601 <b>string</b> and not a number, because that is
 * the divergence this stack has already shipped once in miniature: a typed {@code Instant} through a
 * bare {@code ObjectMapper} emits {@code 1.7890317305E9} — see
 * {@link AdminEntityEvent#getOccurredAt()}. If that assertion fails on a future fixture, that is the
 * artefact working.
 */
class EntityChangedEnvelopeTest {

    private static final Path FRAMES = Path.of("src/test/resources/event-frames/entity-changed");

    private final ObjectMapper om = new ObjectMapper();

    /**
     * The positive control. Without it, a renamed directory or an unmatched glob would leave
     * {@link #everyFrameCarriesTheAgreedEnvelope()} iterating nothing and reporting green — an absence
     * claim indistinguishable from a clean sweep. A floor rather than an exact count, so a fifth
     * producer's frame is welcome without a test edit.
     */
    @Test
    void theFixtureDirectoryHoldsAtLeastTheFourProducts() throws IOException {
        assertThat(frames()).as("one frame per product; see this directory's README.md for provenance").hasSizeGreaterThanOrEqualTo(4);
    }

    @TestFactory
    Stream<DynamicTest> everyFrameCarriesTheAgreedEnvelope() throws IOException {
        return frames()
            .stream()
            .map(frame -> DynamicTest.dynamicTest(frame.getFileName().toString(), () -> assertEnvelope(frame)));
    }

    private void assertEnvelope(Path file) throws IOException {
        JsonNode frame = om.readTree(Files.readString(file));

        assertThat(keysOf(frame))
            .as("%s: the seven agreed top-level keys, exactly — an eighth key must fail rather than pass quietly", file.getFileName())
            .containsExactlyInAnyOrder("eventId", "type", "version", "occurredAt", "source", "subject", "data");

        assertThat(frame.get("type").asString())
            .as("%s: one type string across all four products", file.getFileName())
            .isEqualTo("EntityChanged");

        assertThat(keysOf(frame.get("subject")))
            .as("%s: subject is the entity — the ruling that ended hc-patient's actor-as-subject reading", file.getFileName())
            .containsExactlyInAnyOrder("entityType", "entityId");

        assertThat(keysOf(frame.get("data")))
            .as("%s: identifiers and metadata only — a third data key is content arriving under a metadata heading", file.getFileName())
            .containsExactlyInAnyOrder("action", "actorAccountId");

        assertThat(frame.get("occurredAt").isTextual())
            .as(
                "%s: occurredAt is an ISO-8601 string, never the scientific-notation double a bare ObjectMapper writes for a typed Instant",
                file.getFileName()
            )
            .isTrue();
        assertThatCode(() -> Instant.parse(frame.get("occurredAt").asString()))
            .as("%s: occurredAt parses as an ISO-8601 instant", file.getFileName())
            .doesNotThrowAnyException();

        assertThat(frame.get("source").isTextual() && !frame.get("source").asString().isBlank())
            .as(
                "%s: source is a non-blank string — free-form by design, two naming conventions in the wild (see class javadoc)",
                file.getFileName()
            )
            .isTrue();

        // The key, never a value set: hc-admin's SAVED is deliberate and the other three split
        // CREATED from UPDATED, so any fixed set here would either outlaw an argued decision or
        // paper over divergence (i) in the class javadoc.
        assertThat(frame.get("data").get("action").isTextual() && !frame.get("data").get("action").asString().isBlank())
            .as("%s: data.action exists as a non-blank string", file.getFileName())
            .isTrue();
    }

    private List<Path> frames() throws IOException {
        try (Stream<Path> entries = Files.list(FRAMES)) {
            return entries
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .toList();
        }
    }

    private List<String> keysOf(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }
}
