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

    /**
     * The head of a {@code LogPseudonym.subject(} call, whitespace-tolerant.
     *
     * <p>One declaration because it is used twice and the two must not drift: {@link #WRAPPED} strips
     * these calls out of an argument list, and
     * {@link #noPseudonymisingClassLogsAnExceptionMessage} decides which files it sweeps by looking
     * for one. That second use was a plain {@code String.contains("LogPseudonym.subject(")} until
     * item 57's second review round, which made it the third pattern in this file whose reach a
     * formatter could silently remove — and the worst of them, because a class dropping out of the
     * pseudonymising set takes the exception-message rule with it and nothing goes red.
     */
    private static final String PSEUDONYMISER_CALL = "LogPseudonym\\s*\\.\\s*subject\\s*\\(";

    /**
     * A {@code LOG.x(...)} call, up to the closing bracket of its argument list.
     *
     * <p>Tolerant of whitespace around the dot, for the reason {@link #SECURITY_UTILS_RELAY} records:
     * a formatter may split a call across lines, and this pattern failing to match is <b>silent</b> —
     * the statements simply are not scanned and every rule below passes vacuously.
     */
    private static final Pattern LOG_CALL = Pattern.compile("LOG\\s*\\.\\s*(trace|debug|info|warn|error)\\s*\\((.*?)\\);", Pattern.DOTALL);

    /**
     * A {@code LogPseudonym.subject(...)} call, including one level of nested parentheses so that the
     * usual {@code LogPseudonym.subject(event.subjectKey())} is matched whole. Wrapped calls are
     * deleted from an argument list before it is searched, rather than excluded by a lookbehind: the
     * lookbehind form reads correctly and is wrong, because after refusing the outer
     * {@code event.subjectKey()} the engine simply matches the {@code subjectKey} inside it.
     *
     * <p>Whitespace-tolerant like the rest, though this is the one place where failing to match is
     * <b>fail-safe</b> rather than silent: an unmatched wrapper is not deleted, so the
     * {@code subjectKey} inside it stays visible and the rule goes red. Corrected anyway — a rule that
     * reddens on a reformat is a rule somebody switches off.
     *
     * <h2>The other direction is a known limit, accepted rather than fixed</h2>
     *
     * <p>This pattern counts parentheses and cannot read Java, so an <b>unbalanced {@code (} inside a
     * string literal</b> inside a {@code subject(...)} call lets the nested-group alternative consume
     * the call's real closing bracket. The strip then runs past the end of the wrapper and can delete
     * an <em>unwrapped</em> forbidden identifier appearing later in the same statement — which passes
     * green. All three conditions have to hold at once: the stray bracket, inside a string, inside a
     * pseudonymised call, co-located in one statement with an unwrapped key.
     *
     * <p><b>The whitespace tolerance did not introduce it and could not have.</b> Only the match
     * <em>head</em> changed; the body that decides where a match ends is byte-identical, so no match
     * can extend further right than it did before. Measured on the pre-fix pattern against the same
     * constructed text: the identical over-strip. It is a property of the unchanged body.
     *
     * <p>Not fixed, deliberately. The cure is a parser that understands string literals and balanced
     * brackets, and a source-reading test that grew a Java parser would have stopped being the blunt,
     * narrow instrument {@link #IN_SCOPE}'s javadoc argues it must remain. Written down instead,
     * because a limit that is recorded is a decision and the same limit unrecorded is exactly what
     * this file exists to prevent.
     */
    private static final Pattern WRAPPED = Pattern.compile(PSEUDONYMISER_CALL + "(?:[^()]|\\([^()]*\\))*\\)");

    /**
     * An exception's message being read into a log argument, in either of the two forms the JDK
     * offers. Not {@code toString()}, which is a different tempting mistake and is not one anybody
     * has made here — adding it would be a rule written from imagination rather than from a defect.
     *
     * <p><b>Needs no whitespace tolerance and was checked rather than assumed</b> (item 57, round 2):
     * it never spans the dot — {@code \b} anchors on the method name, so {@code e.getMessage()} and a
     * fluent {@code e\n    .getMessage()} both match. {@link #IN_SCOPE} is immune for a different
     * reason: its four alternatives are bare identifiers, and no formatter may split one.
     */
    private static final Pattern EXCEPTION_MESSAGE = Pattern.compile("\\bget(Message|LocalizedMessage)\\s*\\(\\s*\\)");

    /**
     * What makes a file part of this concern: it handles the link, the event or the key itself — or
     * it calls the pseudonymiser, which is the alternative added on 2026-09-09 and the only one of
     * the four that a comment cannot fake.
     *
     * <h2>Why {@code LogPseudonym} is on the list, and it is not for symmetry</h2>
     *
     * <p><b>{@code PatientServiceClient} was in scope through prose alone.</b> Nothing in its code
     * matched any of the first three alternatives; it matched {@code DirectoryLink} twice, in two
     * javadoc paragraphs. Measured rather than reasoned: restoring the {@code e.getMessage()} leak
     * and then changing <em>only</em> those two comments — {@code {@code DirectoryLink}} to "the
     * directory link" — left this whole file <b>7/7 green with a patient's address going to the log
     * on every unreachable sibling</b>. A rename, a tidy-up, or somebody softening a javadoc would
     * have silently unswept the class this sweep was extended for.
     *
     * <p>That is the estate's own recurring failure — <b>a check whose reach depends on prose is not
     * a check</b> — and the fix has to be a discriminator the compiler can see. A class that calls
     * {@code LogPseudonym} is handling a correlation key by construction: it cannot stop matching
     * without the call itself going away, at which point it has nothing left to leak. Prose is now
     * belt to that braces rather than the only strap.
     *
     * <p>The widening is safe in the other direction too: it adds {@code LogPseudonym} itself to the
     * swept set, which holds no logger, and nothing else at all — every other caller already matched
     * on {@code subjectKey}.
     */
    private static final Pattern IN_SCOPE = Pattern.compile("DirectoryLink|SiblingDomainEvent|subjectKey|LogPseudonym");

    /**
     * <b>A method call in Java source may be split across lines, and every pattern here that spans a
     * {@code .} has to say so.</b>
     *
     * <p>Backlog item 57's second review round. {@code CROSS_STACK_CLIENT}'s token-relay alternative
     * was written {@code SecurityUtils\.getCurrentRequestJwt\(} and
     * {@code ProfessionalServiceClient} writes that call fluently —
     *
     * <pre>
     * String token = SecurityUtils
     *     .getCurrentRequestJwt()
     * </pre>
     *
     * <p>— so the two halves are on different lines and the alternative never matched it. The class
     * was in the swept set through the <em>catch</em> alternative alone, while that pattern's own
     * javadoc promised the relay alternative as the backstop for a client catching bare
     * {@code Exception}. The promised redundancy did not exist for the one class it had been written
     * for, and the mutation that should have revealed it — breaking the catch alternative — was read
     * as proof of the anchor working rather than as proof that nothing else reached the class.
     *
     * <p><b>It was loud rather than silent, which is worth stating exactly because the fix is the
     * same either way.</b> Measured: weakening {@code ProfessionalServiceClient}'s catch to bare
     * {@code Exception} against the unfixed pattern failed the build on this rule's own anchor —
     * <em>"the cross-stack client discovery has stopped finding the classes this rule exists for"</em>
     * — so the leak could not have crept back unnoticed, and naming the classes rather than counting
     * them is what bought that. What was actually wrong was a <b>false statement in a javadoc about
     * why the sweep was safe</b>: a reader who trusted it would have weakened the catch, been failed
     * by a message about discovery rather than about their change, and had no way to connect the two.
     * With the pattern repaired the same mutation now leaves the class swept and fails on the
     * <em>leak</em> instead, which is the assertion that names what they did.
     *
     * <p><b>The reach depended on line-wrapping, which Prettier and Spotless own.</b> That is this
     * file's own failure mode one layer along: {@code IN_SCOPE}'s javadoc is about a discriminator a
     * comment edit could defeat, and this is one a <em>formatter</em> could defeat — neither being
     * something the author of the change would see.
     *
     * <p>It does not match {@code {@link SecurityUtils#getCurrentRequestJwt()}} in a javadoc, which
     * is what keeps a class that only <em>mentions</em> the relay out of the set: {@code #} is
     * neither a dot nor whitespace.
     */
    private static final String SECURITY_UTILS_RELAY = "SecurityUtils\\s*\\.\\s*getCurrentRequestJwt\\s*\\(";

    /**
     * What makes a file a <b>cross-stack client</b>, which is the second concern this file sweeps and
     * arrived with backlog item 57.
     *
     * <h2>Why the pseudonymising set could not simply be widened</h2>
     *
     * <p>{@code ProfessionalServiceClient} logged {@code e.getMessage()} and matched <b>0 of 4</b> of
     * {@link #IN_SCOPE}'s alternatives — it handles no correlation key, pseudonymises nothing, and
     * had no reason to. So {@link #noPseudonymisingClassLogsAnExceptionMessage} could never reach it,
     * however the prose around it was worded. That rule is scoped on <em>handling a subject</em>; the
     * danger it was written for is <em>holding an exception a library built</em>, and those are two
     * different sets that happened to coincide while only one client existed.
     *
     * <p>The message is dangerous in two ways and only the first was written down. A transport
     * failure is a {@code ResourceAccessException} quoting the <b>request URL</b>, which is what
     * leaked a patient's address from {@code PatientServiceClient}. A refusal goes through
     * {@code RestClient}'s default error handler, whose
     * {@code getErrorMessage(int, String, byte[], Charset)} appends the <b>response body</b> — so any
     * far service that echoes a rejected value puts it in this process's log, whatever the URL looks
     * like.
     *
     * <h2>Two discriminators, unioned, and both are ones the compiler can see</h2>
     *
     * <ul>
     *   <li><b>Catching a {@code RestClient} failure.</b> The class holds the dangerous object. It
     *       cannot stop matching without giving up the catch, at which point it has nothing to
     *       log.</li>
     *   <li><b>Relaying the caller's token</b> ({@code SecurityUtils.getCurrentRequestJwt}). The
     *       property that makes a call carry a subject at all, and therefore makes the far side's
     *       answer capable of echoing one.</li>
     * </ul>
     *
     * <p>Each alternative covers a gap the other leaves: a client catching bare {@code Exception}
     * escapes the first, and a client reading an impersonal endpoint escapes the second. <b>The union
     * is not therefore closed, and the residual is not hypothetical — it is today's population.</b> A
     * class that catches bare {@code Exception} <em>and</em> relays no token matches neither
     * alternative, and two such classes exist; see below.
     *
     * <p>Neither alternative can be defeated by editing a comment, which is this file's standard for
     * itself ({@link #IN_SCOPE}). The converse is not true and is worth knowing: a comment that merely
     * <em>mentions</em> {@code SecurityUtils.getCurrentRequestJwt(} pulls a class <b>into</b> the set.
     * That direction is fail-closed — the worst case is a false red naming a file, which is the
     * diagnosable one — so it is left alone rather than guarded against.
     *
     * <h2>What is deliberately NOT swept — a hand-audited set, not a closed one</h2>
     *
     * <p>{@code ObservabilityClient} and {@code AbofonsaContentClient} both log
     * {@code e.getMessage()} and are both left alone. They catch bare {@code Exception} and relay no
     * token, so neither discriminator reaches them — and that is the right answer rather than a lucky
     * one: they read Mimir and a public marketing site, send no identifier in a body, take no
     * correlation key in a URL, and their far sides have nothing of a patient's to echo. Taking their
     * messages would delete a real diagnosis to prevent a leak that cannot happen, which is how a
     * rule stops being applied and starts being worked around.
     *
     * <p><b>But that is an argument about two classes on their facts, not a property of the
     * pattern</b>, and the distinction is the whole of what a future reader needs. <b>A third client
     * that catches bare {@code Exception}, relays no token, and nevertheless carries a subject — an
     * id in a request body, a key in a path — would be swept by nothing, and nothing would report
     * it.</b> The anchor in {@link #noCrossStackClientLogsAnExceptionMessage} names two files, so it
     * cannot notice a third that was never there. If you are writing such a client, you are joining a
     * population audited by hand: either give it one of the two discriminators — catch the
     * {@code RestClient} type rather than {@code Exception} — or come back and widen this pattern
     * deliberately.
     */
    private static final Pattern CROSS_STACK_CLIENT = Pattern.compile(
        "catch\\s*\\(\\s*(?:RestClient\\w*Exception|ResourceAccessException)|" + SECURITY_UTILS_RELAY
    );

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
     * <b>And no such class logs an exception's message, because a message quotes what it was given.</b>
     *
     * <p>This rule exists because the sweep above could not see a real leak. {@code
     * PatientServiceClient} logged {@code e.getMessage()} beside a pseudonymised subject, and Spring
     * wraps an I/O failure in a {@code ResourceAccessException} whose message quotes the <b>request
     * URL</b> — which for that client has the patient's address as a path segment:
     *
     * <pre>
     * ... for subject subj-df2b50538c26: I/O error on GET request for
     * "http://.../api/profiles/email/kojo%40jac.net": null
     * </pre>
     *
     * <p>Every identifier in {@link #FORBIDDEN} is absent from that statement. The address arrives
     * inside a string the JDK built, so an identifier list can never catch it — and it fires exactly
     * when the sibling stack is down, which is when somebody is reading the logs.
     *
     * <h2>Scoped to the class rather than to the statement, deliberately</h2>
     *
     * <p>The tempting rule is "no {@code getMessage()} in the same statement as a {@code subject(}
     * call", which is the shape of the defect that was found. It is too narrow: the leak is that a
     * library was handed a correlation key and put it in a string, so the next statement to leak may
     * mention no subject at all — a debug line one branch away, or a second catch block. What makes a
     * class dangerous is that it <em>handles</em> the key, and a class that has to pseudonymise is
     * exactly one that does.
     *
     * <p>It costs the two pseudonymising classes — {@code PatientServiceClient} and
     * {@code DirectoryProjectionService} — the message text. ({@code SiblingEventParser} and
     * {@code DirectoryLinkResource} are swept by the rule above and call {@code subject(} nowhere, so
     * this one does not reach them.) That is affordable and it is the point: the
     * type of the cause says which failure it was, which is what an operator needs, and it cannot
     * quote anything. Nothing in the repository was made to fail by this rule other than the line it
     * was written for.
     */
    @Test
    void noPseudonymisingClassLogsAnExceptionMessage() {
        // Pattern, not String.contains: the literal form required the call to be written on one line
        // and a formatter could have taken a class out of this set with nothing going red. See
        // PSEUDONYMISER_CALL.
        Pattern pseudonymiser = Pattern.compile(PSEUDONYMISER_CALL);
        List<Path> pseudonymising = inScopeSources().stream().filter(source -> pseudonymiser.matcher(read(source)).find()).toList();

        // Non-emptiness is not enough and was not enough: DirectoryProjectionService satisfies it on
        // its own, so this rule went on "sweeping something" while the class it was written for had
        // dropped out of the set. Named, therefore, the same way DevelopmentDataInitializerTest names
        // fixture rows rather than counting them.
        assertThat(pseudonymising.stream().map(path -> path.getFileName().toString()))
            .as("the class this rule was extended for is no longer being swept — see IN_SCOPE's javadoc")
            .contains("PatientServiceClient.java", "DirectoryProjectionService.java");

        for (Path source : pseudonymising) {
            Matcher call = LOG_CALL.matcher(read(source));
            while (call.find()) {
                String arguments = call.group(2);
                assertThat(EXCEPTION_MESSAGE.matcher(arguments).find())
                    .as(
                        "%s logs an exception's message. A message quotes what the library was given — for an " +
                        "HTTP client that is the request URL, and this service puts a patient's address in one " +
                        "(backlog items 43 and 50). Log the exception's type, and its cause's type if the " +
                        "distinction matters. Statement: LOG.%s(%s)",
                        source.getFileName(),
                        call.group(1),
                        arguments.strip()
                    )
                    .isFalse();
            }
        }
    }

    /**
     * <b>And no client that talks to another product logs an exception's message either</b> —
     * backlog item 57.
     *
     * <p>The same rule as {@link #noPseudonymisingClassLogsAnExceptionMessage} over a different
     * derived set, for the reason {@link #CROSS_STACK_CLIENT} argues at length: that rule reaches
     * classes that handle a subject, this one reaches classes that hold an exception a library built,
     * and {@code ProfessionalServiceClient} was in the second set and not the first. It matched none
     * of {@code IN_SCOPE}'s four alternatives, so nothing in this file could have caught it and
     * nothing did — the statement shipped with a comment asserting the opposite.
     *
     * <p><b>{@code PatientServiceClient} is the positive control</b> and the reason this rule can be
     * trusted the day it is written: it is in the swept set, it catches the same exception type, and
     * it passes — because item 50's review already took its message away. A rule whose whole swept
     * set fails on arrival is indistinguishable from a rule that matches too much.
     */
    @Test
    void noCrossStackClientLogsAnExceptionMessage() {
        List<Path> clients = crossStackClientSources();

        // Named, not counted, for the reason the sibling rule records: "two clients" goes on passing
        // when one of them is renamed out of the set, which is exactly the state that stops sweeping.
        assertThat(clients.stream().map(path -> path.getFileName().toString()))
            .as("the cross-stack client discovery has stopped finding the classes this rule exists for")
            .contains("PatientServiceClient.java", "ProfessionalServiceClient.java");

        for (Path source : clients) {
            Matcher call = LOG_CALL.matcher(read(source));
            while (call.find()) {
                String arguments = call.group(2);
                assertThat(EXCEPTION_MESSAGE.matcher(arguments).find())
                    .as(
                        "%s logs an exception's message, and it calls another product. A ResourceAccessException " +
                        "quotes the request URL; a refusal goes through RestClient's default error handler, which " +
                        "appends the RESPONSE BODY — so a far service echoing a rejected value logs it here " +
                        "(backlog items 43, 50 and 57). Log the exception's type and its cause's type. " +
                        "Statement: LOG.%s(%s)",
                        source.getFileName(),
                        call.group(1),
                        arguments.strip()
                    )
                    .isFalse();
            }
        }
    }

    /**
     * The sweep is worthless if its patterns have stopped matching, and there are now two of them.
     *
     * <p>The file discovery can fail as quietly as the statement pattern can — a rename, a move to
     * another source root, or a smaller {@code IN_SCOPE} would leave a green build sweeping nothing.
     * So both are pinned: the files that carry the concern today must be found by name, and the
     * statements inside the discovered set must still be matched.
     *
     * <p><b>{@code PatientServiceClient} is on the list because it was the one that got away.</b> It
     * joined the swept set with the exception-message rule and was not pinned here, so the only thing
     * standing between the sweep and that class was two javadoc mentions of {@code DirectoryLink} —
     * see {@code IN_SCOPE}. Adding a class to a discovered set without adding it here leaves exactly
     * the gap this method exists to close.
     */
    @Test
    void theSweepStillFindsTheStatementsItIsSweeping() {
        List<Path> sources = inScopeSources();

        assertThat(sources.stream().map(path -> path.getFileName().toString()))
            .as("the file discovery has stopped finding the classes this rule exists for")
            .contains(
                "DirectoryProjectionService.java",
                "SiblingEventParser.java",
                "DirectoryLinkResource.java",
                "PatientServiceClient.java"
            );

        long statements = sources.stream().map(LogPseudonymTest::read).mapToLong(text -> LOG_CALL.matcher(text).results().count()).sum();

        assertThat(statements)
            .as("the log-call pattern matches nothing, so the sweep above passes vacuously — fix the pattern, not this number")
            .isGreaterThanOrEqualTo(10);
    }

    /** Every main source that touches the sibling-link identity, whatever package it lives in. */
    private static List<Path> inScopeSources() {
        return mainSourcesMatching(IN_SCOPE);
    }

    /** Every main source that calls another product over HTTP — see {@link #CROSS_STACK_CLIENT}. */
    private static List<Path> crossStackClientSources() {
        return mainSourcesMatching(CROSS_STACK_CLIENT);
    }

    private static List<Path> mainSourcesMatching(Pattern discriminator) {
        try (Stream<Path> tree = Files.walk(Path.of("src/main/java"))) {
            return tree
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(path -> discriminator.matcher(read(path)).find())
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
