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
 * question about a handful of identifiers, and the thing it is protecting is a defect that shipped
 * to production and was invisible to every green build.
 *
 * <h2>The file set is derived, and it was a list of two names until 2026-09-07</h2>
 *
 * <p>It named {@code DirectoryProjectionService} and {@code SiblingEventParser}. That was every file
 * that could log the key on the day it was written and it stopped being so the moment
 * {@code DirectoryLinkResource} gained the {@code localId.in} filter — a class that reads links,
 * holds a logger, and is the one place in the service where the log/screen distinction is argued at
 * length. Nothing failed; the sweep simply did not look at it.
 *
 * <p>So the set is now discovered rather than enumerated, on the same reasoning {@code PaginationIT}
 * records: <b>a test whose coverage has to be extended by hand silently stops covering things.</b>
 * Any file under {@code src/main/java} that mentions the link, the event or the key is swept, which
 * means a new consumer, a new resource or a new reconciliation job is covered on the commit that
 * creates it. {@link #theSweepStillFindsTheStatementsItIsSweeping} is what keeps the discovery
 * honest — a pattern that has stopped matching would otherwise make all of this pass vacuously.
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

    /** What makes a file part of this concern: it handles the link, the event, or the key itself. */
    private static final Pattern IN_SCOPE = Pattern.compile("DirectoryLink|SiblingDomainEvent|subjectKey");

    /**
     * The identifiers that must never reach a log line, and why each is on the list.
     *
     * <ul>
     *   <li>{@code subjectKey} — the correlation key on an inbound event. A lowercased email address
     *       for a patient, which is what backlog item 43 was about.</li>
     *   <li>{@code externalKey} / {@code getExternalKey} — the same value, read back off a stored
     *       {@code DirectoryLink} rather than off the wire. It is the field the resource added a
     *       filter over, so this is the reachable form now.</li>
     *   <li>{@code getEmail} — a link's address, which is a patient's {@code externalKey} under
     *       another name and the only field on a {@code DirectoryLink} a screen renders.</li>
     * </ul>
     *
     * <p>Deliberately not on the list: {@code getLogin}, because this service already writes a login
     * into every {@code AuditLog} row by design, and {@code getExternalId}, which is a patient id
     * rather than an address. The rule is about the correlation key, not about identity in general —
     * widening it to everything would make it a rule nobody could apply.
     */
    private static final List<String> FORBIDDEN = List.of("subjectKey", "externalKey", "getExternalKey", "getEmail");

    @Test
    void noLogStatementPassesTheSubjectKeyUnwrapped() {
        for (Path source : inScopeSources()) {
            String text = read(source);
            Matcher call = LOG_CALL.matcher(text);
            while (call.find()) {
                String arguments = call.group(2);
                String unwrapped = WRAPPED.matcher(arguments).replaceAll("");
                for (String forbidden : FORBIDDEN) {
                    assertThat(unwrapped.contains(forbidden))
                        .as(
                            "%s logs %s unwrapped — it is a patient's email address. Wrap it in " +
                            "LogPseudonym.subject(...), which explains why, and note that a screen is a " +
                            "different question with a different answer (DirectoryLinkResource's javadoc). " +
                            "Statement: LOG.%s(%s)",
                            source.getFileName(),
                            forbidden,
                            call.group(1),
                            arguments.strip()
                        )
                        .isFalse();
                }
            }
        }
    }

    /**
     * The sweep is worthless if its patterns have stopped matching, and there are now two of them.
     *
     * <p>The file discovery can fail as quietly as the statement pattern can — a rename, a move to
     * another source root, or a smaller {@code IN_SCOPE} would leave a green build sweeping nothing.
     * So both are pinned: the three files that carry the concern today must be found by name, and the
     * statements inside the discovered set must still be matched.
     */
    @Test
    void theSweepStillFindsTheStatementsItIsSweeping() {
        List<Path> sources = inScopeSources();

        assertThat(sources.stream().map(path -> path.getFileName().toString()))
            .as("the file discovery has stopped finding the classes this rule exists for")
            .contains("DirectoryProjectionService.java", "SiblingEventParser.java", "DirectoryLinkResource.java");

        long statements = sources.stream().map(LogPseudonymTest::read).mapToLong(text -> LOG_CALL.matcher(text).results().count()).sum();

        assertThat(statements)
            .as("the log-call pattern matches nothing, so the sweep above passes vacuously — fix the pattern, not this number")
            .isGreaterThanOrEqualTo(10);
    }

    /** Every main source that touches the sibling-link identity, whatever package it lives in. */
    private static List<Path> inScopeSources() {
        try (Stream<Path> tree = Files.walk(Path.of("src/main/java"))) {
            return tree
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(path -> IN_SCOPE.matcher(read(path)).find())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk src/main/java — is this running from the module root?", e);
        }
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

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path.toAbsolutePath() + " — has the class moved?", e);
        }
    }
}
