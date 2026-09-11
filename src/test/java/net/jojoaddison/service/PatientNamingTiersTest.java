package net.jojoaddison.service;

import static net.jojoaddison.config.ApplicationPropertiesFixture.resolveBudget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.service.PatientServiceClient.ResolvedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * <b>The downloaded file and the console name one nameless patient differently, and the difference
 * is a decision rather than a defect</b> — backlog item 70.
 *
 * <h2>The two tiers</h2>
 *
 * <p>A patient who registered on hc-patient arrives here from a domain event, and no event carries a
 * name — their {@code PatientEventPublisher} refuses at runtime to publish one. So this service holds
 * no {@code Profile} for them and has two ways to say who they are:
 *
 * <ul>
 *   <li><b>The file.</b> {@link PatientCsvExporter} names them from the address on their
 *       {@link DirectoryLink} — one Mongo read for the whole export, in process, no sibling stack
 *       involved (item 62).</li>
 *   <li><b>The screen.</b> {@link DirectoryNameResolutionService} goes further and asks hc-patient,
 *       who own the name, and the console renders the person (item 50).</li>
 * </ul>
 *
 * <p>So on a stack where hc-patient answers, one record reads {@code Kojo Ampia-Addison} in the
 * console and {@code kojo@jac.net} in the download <em>at the same moment</em>. <b>Both are
 * correct.</b> Item 70 weighed closing the gap — chunking the export's stream so the per-page budget
 * had a unit to spend against — and declined: it puts a remote fan-out on the one endpoint whose
 * justification is being cheaper than paging the same rows, invents budget semantics for a file
 * nobody waits on, and makes a slow hc-patient a slow download. What it required instead is that the
 * difference be <b>asserted</b>, because until this class existed it lived in one javadoc and nothing
 * would have reported either tier moving.
 *
 * <h2>Do not read a failure here as a defect</h2>
 *
 * <p>Every case below fails in exactly one situation: somebody moved one of the two tiers. That may
 * be entirely right — giving the export a resolver is a real option, and so is dropping the
 * console's. What it must not be is silent, because these two answers are what an administrator
 * compares when a downloaded file disagrees with the screen they downloaded it from.
 *
 * <h2>⚠ Why this is a unit test, and what {@link net.jojoaddison.web.rest.PatientNamingTiersIT}
 * adds</h2>
 *
 * <p>The rule each tier applies is decided in a service — {@code PatientCsvExporter.displayName} and
 * {@code DirectoryNameResolutionService.resolve} — so comparing the two rules needs neither Mongo nor
 * MockMvc, and <b>one {@link DirectoryLink} instance is handed to both readers here</b>, which is a
 * closer statement of "the same record" than two queries against one database.
 *
 * <p>It is a unit test rather than only an integration test for a reason worth stating, because the
 * conventional instinct is the other way round. This repository's integration tests need a Mongo
 * replica set, and on a loaded workstation that container misses its start window — measured on
 * 2026-09-11 at load ~25 across 16 CPUs: three attempts, all timed out, every case erroring before it
 * ran (see {@code GEMINI.md} and backlog item 17). <b>An assertion that can only be watched in CI is
 * one the next author will not watch at all</b>, and this one exists precisely to be read by somebody
 * who has just moved a tier. So the comparison lives where it runs in two seconds, and the
 * integration twin proves the same pair through the two real endpoints on a stored record.
 *
 * <p>What is given up by asserting here is covered and was checked rather than assumed:
 * {@code PatientExportIT} drives the export endpoint over a real database, and
 * {@code DirectoryLinkNameResolutionIT} drives {@code resolveNames=true} over the real resource. What
 * neither of them does — and what nothing in the estate did before item 70 — is <em>compare</em> the
 * two answers.
 */
class PatientNamingTiersTest {

    /** What this service holds about the record: the address its link was opened with. */
    private static final String THE_ADDRESS = "kojo@jac.net";

    /** What hc-patient holds about the same record, and this service never stores. */
    private static final String THE_PERSON = "Kojo Ampia-Addison";

    /**
     * The record both tiers are asked about.
     *
     * <p>Twenty-four hex characters, because that is what an ObjectId is and what an administrator
     * saw in the first column in production. A short literal would satisfy every assertion below and
     * understate the defect the shared floor exists to keep out.
     */
    private static final String RECORD_ID = "68b4f2a19c3d5e7f81a02c15";

    /**
     * One link document, read by both tiers.
     *
     * <p>Shared deliberately. The subject of this class is two readers disagreeing about one record,
     * and building a link per tier would leave a case that passed while the two were reading
     * different rows — which is the failure it is supposed to detect, one level up.
     */
    private DirectoryLink theLink;

    private Patient theRecord;

    private final DirectoryLinkRepository directoryLinkRepository = mock(DirectoryLinkRepository.class);

    private final PatientServiceClient patientServiceClient = mock(PatientServiceClient.class);

    private final PatientCsvExporter exporter = new PatientCsvExporter(directoryLinkRepository);

    private final DirectoryNameResolutionService resolution = new DirectoryNameResolutionService(patientServiceClient, resolveBudget(4000));

    @BeforeEach
    void oneRecordThatOnlyOneTierCanResolve() {
        theLink = patientLink();
        theRecord = new Patient().status(AccountStatus.PENDING).joinedOn(LocalDate.of(2026, 3, 1));
        theRecord.setId(RECORD_ID);
        when(directoryLinkRepository.findBySource(any())).thenReturn(List.of(theLink));

        // A mock answers false to a boolean, and false is the one value that makes the resolver mark
        // every candidate unavailable and return before it calls anything — so leaving it out would
        // have every case below assert against the not-configured short circuit.
        when(patientServiceClient.isEnabled()).thenReturn(true);
        when(patientServiceClient.resolveName(THE_ADDRESS)).thenReturn(new ResolvedName(NameResolution.RESOLVED, THE_PERSON));
    }

    /**
     * The file's tier: the address on the record's own link.
     *
     * <h2>⚠ One tier per case, and the two literals are deliberately never pinned together</h2>
     *
     * <p>This started as a single case asserting all three facts — file says the address, screen says
     * the person, and the two differ — and <b>the third assertion could not fail while the first two
     * passed</b>. Pin {@code inTheFile} to one literal and {@code onTheScreen} to a different one and
     * "they differ" follows from the literals rather than from the code: a tautology wearing the most
     * important message in the class. It was caught by watching the convergence mutation and seeing it
     * reported by the wrong assertion.
     *
     * <p>So each tier is pinned in its own case and
     * {@link #theTwoTiersDoNotGiveTheSameAnswerWhileTheSiblingAnswers} compares the two <em>computed</em>
     * values, pinning neither. All three now fail under different changes, which is the property that
     * makes the third one worth having.
     *
     * <p>Asserted against {@code theLink.getEmail()} rather than the constant for the same reason —
     * the rule is "whatever address the link carries", and a constant would also pass for an exporter
     * that had hard-coded it.
     */
    @Test
    void theFileNamesThisRecordByTheAddressOnItsLink() throws Exception {
        assertThat(theNameInTheExportedFile())
            .as(
                "The export's tier has moved. A patient with no Profile is named from the address on its " +
                    "DirectoryLink (backlog item 62, PatientCsvExporter.displayName): one Mongo read for the whole " +
                    "file, no sibling stack. If this is now the person's name, the export has acquired the remote " +
                    "fan-out item 70 declined — say so there and here. If it is the record's id, that is item 45's " +
                    "rule breached for the third time in that class."
            )
            .isEqualTo(theLink.getEmail());
    }

    /** The screen's tier: whatever hc-patient answers, which this service never stores. */
    @Test
    void theConsoleNamesThisRecordByWhatTheSiblingAnswers() {
        assertThat(theNameTheConsoleWouldRender())
            .as(
                "The console's tier has moved. The patient directory sends resolveNames=true and hc-patient names " +
                    "the record (backlog item 50). If this is now the address, the resolution is not happening — which " +
                    "is how item 50 shipped inert, on a getCurrentUserJWT() that answered empty on every real request " +
                    "(item 57)."
            )
            .isEqualTo(THE_PERSON);
    }

    /**
     * <b>And the two answers are not the same one</b> — which is the whole of backlog item 70.
     *
     * <p>Compares what the two tiers computed and pins neither to a literal, so this fails when they
     * converge from either direction and only then. It is the case that turns a documented difference
     * into a checked one.
     */
    @Test
    void theTwoTiersDoNotGiveTheSameAnswerWhileTheSiblingAnswers() throws Exception {
        String inTheFile = theNameInTheExportedFile();
        String onTheScreen = theNameTheConsoleWouldRender();

        assertThat(inTheFile)
            .as(
                "The file and the screen now give the SAME answer for this record, and that is a decision rather " +
                    "than a fix. Backlog item 70 settled that a document and a screen may legitimately name one record " +
                    "differently, because only the console can afford to ask hc-patient. Converging them is fine if it " +
                    "was meant — update PatientCsvExporter.displayName, this class and item 70 together. It is not fine " +
                    "if it happened because one tier quietly stopped doing its half, which is what the two cases above " +
                    "will say."
            )
            .isNotEqualTo(onTheScreen);
    }

    /**
     * When hc-patient cannot name the record, the two tiers agree — and the agreement is the point.
     *
     * <p>Without this case the class asserts "these two always differ", which is false and would send
     * the next reader looking for a defect the first time they saw them match. The difference exists
     * only while the sibling answers; a sibling that is down, or that has never heard of the address,
     * leaves the console with exactly what the file already had. <b>That is the floor both tiers share
     * — the address item 45 put on the row — and the reason the export is narrower rather than
     * worse.</b>
     */
    @Test
    void theTwoTiersAgreeWhenTheSiblingCannotNameTheRecord() throws Exception {
        when(patientServiceClient.resolveName(THE_ADDRESS)).thenReturn(new ResolvedName(NameResolution.NOT_FOUND, null));

        assertThat(theNameTheConsoleWouldRender()).as("nothing came back, so there is no name to render").isNull();
        assertThat(theLink.getNameResolution()).isEqualTo(NameResolution.NOT_FOUND);
        // With no name the console renders resolveLinkIdentity — the link's address. Compared against
        // what the file computed rather than against the constant both were built from, which would
        // assert the fixture rather than the two tiers.
        assertThat(theLink.getEmail())
            .as("the floor the two tiers share: with the sibling silent, the screen shows what the file always shows")
            .isEqualTo(theNameInTheExportedFile());
    }

    /**
     * Neither tier names the record by its key, whatever else they disagree about.
     *
     * <p>The one rule that spans both, and the invariant a future convergence must not cross on its
     * way. {@code PatientCsvExporterTest.noCellInTheFileIsTheIdOfAnyRecordTheRowReaches} enforces it
     * inside the file (backlog item 69); this asks it of both tiers at once, which is the form the
     * question takes when somebody is deciding what a nameless row should say.
     */
    @Test
    void neitherTierNamesTheRecordByItsOwnId() throws Exception {
        assertThat(theNameInTheExportedFile()).isNotEqualTo(RECORD_ID);
        assertThat(theNameTheConsoleWouldRender()).isNotEqualTo(RECORD_ID);
        // The whole row rather than its first cell: an id in any column is the defect, and a
        // first-cell assertion would not see it move.
        assertThat(theExportedRow()).doesNotContain(RECORD_ID);
    }

    /** The first column of this record's row in the exported file. */
    private String theNameInTheExportedFile() throws Exception {
        String row = theExportedRow();
        return row.substring(1, row.indexOf("\",\""));
    }

    private String theExportedRow() throws Exception {
        StringWriter writer = new StringWriter();
        exporter.write(writer, Stream.of(theRecord), LocalDate.of(2026, 9, 11));
        List<String> lines = List.of(writer.toString().split("\r\n"));
        assertThat(lines).as("a header and the one record under test").hasSize(2);
        return lines.get(1);
    }

    /** What {@code resolveNames=true} would put on this record's link, or null when nothing resolved. */
    private String theNameTheConsoleWouldRender() {
        resolution.resolve(List.of(theLink));
        return theLink.getResolvedName();
    }

    /** One {@code HC_PATIENT} link, as the projection writes it. */
    private static DirectoryLink patientLink() {
        DirectoryLink link = new DirectoryLink();
        link.setId("link-under-test");
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(THE_ADDRESS);
        link.setEmail(THE_ADDRESS);
        link.setLocalId(RECORD_ID);
        link.setFirstSeenAt(Instant.parse("2026-03-01T00:00:00Z"));
        link.setLastEventAt(Instant.parse("2026-03-01T00:00:00Z"));
        return link;
    }
}
