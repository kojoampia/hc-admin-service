package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The two halves of backlog item 43's guarantee that a behavioural test cannot reach.
 *
 * <h2>Why there is a source sweep here at all</h2>
 *
 * <p>{@code DirectoryEventConsumptionIT.noLogLineCarriesTheSubjectKey} is the real guard: it drives
 * events through the binding with the package at TRACE and asserts no address is rendered. It covers
 * every statement that a message can reach, which is four of the five.
 *
 * <p>The fifth cannot be driven. {@code DirectoryProjectionService.createAndClaim} logs when its
 * compare-and-set finds that another writer claimed the link first — a partition rebalance, or the
 * reconciliation endpoint running against a live consumer. It is real code that will log in
 * production, and provoking it needs two writers interleaved between two Mongo round trips, which is
 * not a test, it is a race that would pass by luck. So the property is asserted twice, by two
 * methods with different blind spots: the IT proves the lines that run are safe and still useful,
 * and the sweep below proves that <b>no</b> statement passes the key, including the ones no fixture
 * arrives at and the ones added next year.
 *
 * <p>A source-reading test is a blunt instrument and it is used deliberately narrowly — it asks one
 * question about one identifier in two files, and the thing it is protecting is a defect that
 * shipped to production and was invisible to every green build.
 */
class LogPseudonymTest {

    /** A {@code LOG.x(...)} call, up to the closing bracket of its argument list. */
    private static final Pattern LOG_CALL = Pattern.compile("LOG\\.(trace|debug|info|warn|error)\\s*\\((.*?)\\);", Pattern.DOTALL);

    /**
     * A {@code LogPseudonym.subject(...)} call, including one level of nested parentheses so that the
     * usual {@code LogPseudonym.subject(event.subjectKey())} is matched whole. Wrapped calls are
     * deleted from an argument list before it is searched, rather than excluded by a lookbehind: the
     * lookbehind form reads correctly and is wrong, because after refusing the outer
     * {@code event.subjectKey()} the engine simply matches the {@code subjectKey} inside it.
     */
    private static final Pattern WRAPPED = Pattern.compile("LogPseudonym\\.subject\\((?:[^()]|\\([^()]*\\))*\\)");

    @Test
    void noLogStatementPassesTheSubjectKeyUnwrapped() {
        for (Path source : List.of(main("DirectoryProjectionService"), main("SiblingEventParser"))) {
            String text = read(source);
            Matcher call = LOG_CALL.matcher(text);
            while (call.find()) {
                String arguments = call.group(2);
                assertThat(WRAPPED.matcher(arguments).replaceAll("").contains("subjectKey"))
                    .as(
                        "%s logs the correlation key unwrapped — it is a patient's email address. " +
                        "Wrap it in LogPseudonym.subject(...), which explains why. Statement: LOG.%s(%s)",
                        source.getFileName(),
                        call.group(1),
                        arguments.strip()
                    )
                    .isFalse();
            }
        }
    }

    /** The sweep is worthless if its pattern has stopped matching the file it reads. */
    @Test
    void theSweepStillFindsTheStatementsItIsSweeping() {
        long statements = Stream
            .of(main("DirectoryProjectionService"), main("SiblingEventParser"))
            .map(LogPseudonymTest::read)
            .mapToLong(text -> LOG_CALL.matcher(text).results().count())
            .sum();

        assertThat(statements)
            .as("the log-call pattern matches nothing, so the sweep above passes vacuously — fix the pattern, not this number")
            .isGreaterThanOrEqualTo(10);
    }

    /**
     * The digest is reproducible from an address by hand, which is the whole reason it is a digest
     * and not the {@code DirectoryLink} id.
     *
     * <p>The expected value is the real SHA-256 of the normalised address, computed independently:
     * {@code printf '%s' 'ama.mensah@example.com' | sha256sum | cut -c1-12}. A literal rather than a
     * second call to the code under test — deriving it here would assert only that the method equals
     * itself, and the property that matters is that an operator at a shell reaches the same string.
     */
    @Test
    void aDigestIsReproducibleFromTheAddress() {
        assertThat(LogPseudonym.subject("ama.mensah@example.com")).isEqualTo("subj-5c9b0dc282ec");
    }

    /** Casing and stray whitespace must not produce a second handle for one person. */
    @Test
    void theKeyIsNormalisedBeforeHashing() {
        String canonical = LogPseudonym.subject("ama.mensah@example.com");

        assertThat(LogPseudonym.subject("  Ama.Mensah@Example.COM  ")).isEqualTo(canonical);
        assertThat(LogPseudonym.subject("acc-12345")).as("a clinician's accountId is a key too").isNotEqualTo(canonical);
    }

    /** An absent key is a state worth seeing in a log, not a crash and not an empty string. */
    @Test
    void anAbsentKeyIsNamedRatherThanBlank() {
        assertThat(LogPseudonym.subject(null)).isEqualTo("subj-none");
        assertThat(LogPseudonym.subject("   ")).isEqualTo("subj-none");
    }

    /** Two frames differing anywhere are distinguishable — which truncation to 100 chars was not. */
    @Test
    void aFrameFingerprintTellsTwoLongFramesApart() {
        byte[] one = ("{\"padding\":\"" + "x".repeat(300) + "\",\"subject\":\"first\"}").getBytes(StandardCharsets.UTF_8);
        byte[] two = ("{\"padding\":\"" + "x".repeat(300) + "\",\"subject\":\"second\"}").getBytes(StandardCharsets.UTF_8);

        assertThat(LogPseudonym.frame(one)).isNotEqualTo(LogPseudonym.frame(two));
        assertThat(LogPseudonym.frame(one)).as("the same frame redelivered reads the same").isEqualTo(LogPseudonym.frame(one));
        assertThat(LogPseudonym.frame(one)).containsPattern("^frame-[0-9a-f]{12} \\(\\d+ bytes\\)$");
        assertThat(LogPseudonym.frame(null)).as("an absent payload is describable rather than a crash").contains("(0 bytes)");
    }

    private static Path main(String simpleName) {
        return Path.of("src/main/java/net/jojoaddison/service", simpleName + ".java");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path.toAbsolutePath() + " — has the class moved?", e);
        }
    }
}
