package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.service.PatientServiceClient;
import net.jojoaddison.service.PatientServiceClient.ResolvedName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * <b>The console and the CSV export give two different answers for the same nameless patient, and
 * the difference is deliberate</b> — backlog item 70.
 *
 * <h2>What is being pinned, and why it is a pair rather than a bug</h2>
 *
 * <p>A patient who registered on hc-patient reaches this directory from a domain event, and no event
 * carries a name — their {@code PatientEventPublisher} refuses at runtime to publish one. So this
 * service holds no {@code Profile} for them and has two ways to say who they are:
 *
 * <ul>
 *   <li><b>The file</b> names them from the address on their {@link DirectoryLink} — one Mongo read
 *       for the whole export, in process, no sibling stack involved (item 62,
 *       {@code PatientCsvExporter.displayName}).</li>
 *   <li><b>The screen</b> goes further and asks hc-patient, who own the name, and renders the person
 *       (item 50, {@code resolveNames=true} over {@code DirectoryNameResolutionService}).</li>
 * </ul>
 *
 * <p>So on a stack where hc-patient answers, one record reads {@code Kojo Ampia-Addison} in the
 * console and {@code kojo@jac.net} in the download <em>at the same moment</em>. <b>Both are
 * correct.</b> Item 70 weighed closing the gap — chunking the export's stream so the per-page budget
 * has a unit to spend against — and declined: that adds a remote fan-out to the one endpoint whose
 * whole justification is that it is cheaper than paging the same rows, invents budget semantics for
 * a file nobody is waiting on, and makes a slow hc-patient a slow download. The answer taken was
 * that a document and a screen may legitimately differ, <b>and that the difference must be asserted
 * rather than left in one javadoc to drift</b>. That is this class.
 *
 * <h2>Do not read a failure here as a defect</h2>
 *
 * <p>Every assertion below fails in exactly one situation: somebody moved one of the two tiers. That
 * may be entirely right — giving the export a resolver is a real option, and so is dropping the
 * console's. What it must not be is silent, because the two tiers are what an administrator compares
 * when a downloaded file disagrees with the screen they downloaded it from, and until this class
 * existed nothing in the estate would have reported either one moving.
 *
 * <h2>⚠ This is the twin of {@code PatientNamingTiersTest}, and that one is the primary</h2>
 *
 * <p>The rule each tier applies is decided in a service, so the comparison itself needs neither Mongo
 * nor MockMvc and is asserted there, in two seconds, over one {@link DirectoryLink} instance handed to
 * both readers. <b>That is where to add a case and where a failure will first be seen</b> — this
 * repository's integration tests need a Mongo replica set, and on a loaded workstation that container
 * misses its start window (measured 2026-09-11 at load ~25 across 16 CPUs: three attempts, all timed
 * out, every case erroring before it ran — {@code GEMINI.md}, backlog item 17). An assertion that can
 * only be watched in CI is one the next author will not watch at all.
 *
 * <p>What this class adds, and the only reason it is worth the container: the same pair of answers
 * <b>through the two real endpoints, about one record in a real database</b>. The service-level twin
 * cannot see the {@code resolveNames} parameter being bound, the {@code localId.in} query, the
 * streaming CSV response or the Mongo cursor behind it. Each of those halves is pinned on its own —
 * {@code PatientExportIT} and {@code DirectoryLinkNameResolutionIT} — and neither of those
 * <em>compares</em> the two, which is what item 70 is about.
 *
 * <h2>What is mocked</h2>
 *
 * <p>Only {@link PatientServiceClient}. hc-patient is not on this machine; what they really answer is
 * {@code PatientServiceClientTest}'s question over a loopback server, and the relay of the caller's
 * own token is {@code PatientNameRelayIT}'s.
 *
 * <p>A class of its own rather than cases in {@link PatientExportIT}, for the reason
 * {@code DirectoryLinkNameResolutionIT} gives: a {@code @MockitoBean} changes the context cache key
 * for every case in the class that declares it, and that file's cases have no interest in this
 * client.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
class PatientNamingTiersIT {

    /** What this service holds about the record: the address the link was opened with. */
    private static final String THE_ADDRESS = "kojo@jac.net";

    /** What hc-patient holds about the same record, and this service never stores. */
    private static final String THE_PERSON = "Kojo Ampia-Addison";

    /**
     * Removed by id in the teardown rather than with {@code deleteAll()}.
     *
     * <p>{@code directory_link} is the identity map two consumers upsert into, so emptying it would
     * delete rows another test — or another run's leftovers — depends on, and the damage would read
     * as a directory that had forgotten who it learned about rather than as a failure here.
     */
    private static final String LINK_ID = "patient-naming-tiers-it-link";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    @MockitoBean
    private PatientServiceClient patientServiceClient;

    /** The Mongo-assigned id of the one record both tiers are asked about. */
    private String recordId;

    @BeforeEach
    void seedOneRecordThatOnlyOneTierCanResolve() {
        patientRepository.deleteAll();
        Patient learned = patientRepository.save(new Patient().status(AccountStatus.PENDING).joinedOn(LocalDate.of(2026, 3, 1)));
        recordId = learned.getId();
        directoryLinkRepository.save(link(recordId));

        // A mock answers false to a boolean, and false is the value that makes
        // DirectoryNameResolutionService mark every candidate unavailable before it calls anything.
        when(patientServiceClient.isEnabled()).thenReturn(true);
        when(patientServiceClient.resolveName(THE_ADDRESS)).thenReturn(new ResolvedName(NameResolution.RESOLVED, THE_PERSON));
    }

    @AfterEach
    void tearDown() {
        patientRepository.deleteAll();
        directoryLinkRepository.deleteById(LINK_ID);
    }

    /**
     * The file's tier, through {@code GET /api/patients/export}: the address on the record's link.
     *
     * <h2>⚠ One tier per case, and the two literals are never pinned together</h2>
     *
     * <p>{@code PatientNamingTiersTest} carries the reasoning in full; the short version is that a
     * single case asserting <em>file says the address</em>, <em>screen says the person</em> and
     * <em>the two differ</em> has a third assertion that cannot fail while the first two pass. Two
     * literals pinned to two different values make "they differ" follow from the fixture rather than
     * from the code — a tautology carrying the most important message in the class. Each tier is
     * pinned in its own case, and
     * {@link #theTwoTiersDoNotGiveTheSameAnswerWhileTheSiblingAnswers} compares the two computed
     * values, pinning neither.
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
            .isEqualTo(theIdentityTheConsoleFallsBackTo());
    }

    /** The screen's tier, through {@code GET /api/directory-links?resolveNames=true}: the person. */
    @Test
    void theConsoleNamesThisRecordByWhatTheSiblingAnswers() throws Exception {
        assertThat(theNameTheConsoleWouldRender())
            .as(
                "The console's tier has moved. The patient directory sends resolveNames=true and hc-patient " +
                    "names the record (backlog item 50). If this is now the address, the resolution is not " +
                    "happening — which is what item 50 shipped inert for, on a getCurrentUserJWT() that answers " +
                    "empty on every real request (item 57)."
            )
            .isEqualTo(THE_PERSON);
    }

    /**
     * <b>And the two endpoints do not give the same answer</b> — the whole of backlog item 70, through
     * the two surfaces an administrator actually compares.
     */
    @Test
    void theTwoTiersDoNotGiveTheSameAnswerWhileTheSiblingAnswers() throws Exception {
        String inTheFile = theNameInTheExportedFile();
        String onTheScreen = theNameTheConsoleWouldRender();

        assertThat(inTheFile)
            .as(
                "The file and the screen now give the SAME answer, and that is a decision rather than a fix. " +
                    "Backlog item 70 settled that a document and a screen may legitimately name one record " +
                    "differently, because only the console can afford to ask hc-patient. Converging them is fine if " +
                    "it was meant — update PatientCsvExporter.displayName, this class and item 70 together. It is " +
                    "not fine if it happened because one tier quietly stopped doing its half, which is what the two " +
                    "cases above will say."
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
     * — the address item 45 put on the row — and the reason the export is not "worse", only
     * narrower.</b>
     */
    @Test
    void theTwoTiersAgreeWhenTheSiblingCannotNameTheRecord() throws Exception {
        when(patientServiceClient.resolveName(THE_ADDRESS)).thenReturn(new ResolvedName(NameResolution.NOT_FOUND, null));

        assertThat(theNameInTheExportedFile()).isEqualTo(THE_ADDRESS);
        assertThat(theNameTheConsoleWouldRender()).isNull();
        assertThat(theIdentityTheConsoleFallsBackTo()).isEqualTo(THE_ADDRESS);
    }

    /**
     * Neither tier names the record by its key, whatever else they disagree about.
     *
     * <p>The one rule that spans both, and the invariant a future convergence must not cross on its
     * way. {@code PatientCsvExporterTest.noCellInTheFileIsTheIdOfAnyRecordTheRowReaches} is the sweep
     * that enforces it inside the file (backlog item 69); this is the same rule asked of a real
     * record, through both real endpoints, with a real Mongo-assigned ObjectId rather than a fixture
     * literal — which is the form the defect took in production both times.
     */
    @Test
    void neitherTierNamesTheRecordByItsOwnId() throws Exception {
        assertThat(recordId).as("a persisted patient always has a Mongo-assigned id").isNotBlank();

        assertThat(theNameInTheExportedFile()).isNotEqualTo(recordId);
        assertThat(theNameTheConsoleWouldRender()).isNotEqualTo(recordId);
        assertThat(theIdentityTheConsoleFallsBackTo()).isNotEqualTo(recordId);
        // The whole file rather than its first column: an id in any cell of any row is the defect, and
        // a per-cell assertion would not see it move.
        assertThat(exportBody()).doesNotContain(recordId);
    }

    /** The first column of this record's row in {@code GET /api/patients/export}. */
    private String theNameInTheExportedFile() throws Exception {
        List<String> lines = List.of(exportBody().replace("﻿", "").split("\r\n"));
        // The collection holds exactly the record seeded above, so the file is a header and one row.
        // Asserted rather than assumed: reading row 1 of a file that unexpectedly held several would
        // silently pin the wrong record.
        assertThat(lines).as("the export should hold the one seeded record and a header").hasSize(2);
        String row = lines.get(1);
        return row.substring(1, row.indexOf("\",\""));
    }

    /** What {@code resolveNames=true} adds to this record's link, or null when nothing was resolved. */
    private String theNameTheConsoleWouldRender() throws Exception {
        return field("$[0].resolvedName");
    }

    /**
     * The address the console renders when no name comes back — its {@code resolveLinkIdentity}, and
     * the same value the file uses.
     */
    private String theIdentityTheConsoleFallsBackTo() throws Exception {
        return field("$[0].email");
    }

    /**
     * One field of the row, or null when the response does not carry it.
     *
     * <p>Absent and null are one answer here on purpose. {@code resolvedName} is null for a row
     * nobody could name and may be serialised or omitted depending on the mapper's inclusion policy;
     * a reader that threw on one and returned null for the other would make this class's assertions
     * depend on a Jackson setting that has nothing to do with either tier.
     */
    private String field(String path) throws Exception {
        try {
            return JsonPath.read(directoryLinksForThisRecord(), path);
        } catch (PathNotFoundException absent) {
            return null;
        }
    }

    private String directoryLinksForThisRecord() throws Exception {
        return mvc
            .perform(get("/api/directory-links").param("localId.in", recordId).param("resolveNames", "true"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    /**
     * The export's body, after the async dispatch a streaming response needs.
     *
     * <p>{@code StreamingResponseBody} returns from the handler before anything is written, so the
     * first {@code perform} completes with a 200 and an empty body — which passes every status
     * assertion and checks nothing. {@code asyncStarted()} is asserted so that a handler changed to
     * return its body directly fails loudly instead of dispatching an empty result.
     */
    private String exportBody() throws Exception {
        var started = mvc.perform(get("/api/patients/export")).andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** One {@code HC_PATIENT} link, as the projection writes it. */
    private static DirectoryLink link(String localId) {
        DirectoryLink link = new DirectoryLink();
        link.setId(LINK_ID);
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(THE_ADDRESS);
        link.setEmail(THE_ADDRESS);
        link.setLocalId(localId);
        link.setFirstSeenAt(Instant.parse("2026-03-01T00:00:00Z"));
        link.setLastEventAt(Instant.parse("2026-03-01T00:00:00Z"));
        return link;
    }
}
