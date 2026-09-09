package net.jojoaddison.web.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.repository.DirectoryLinkRepository;
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
 * What {@code GET /api/directory-links?resolveNames=true} puts on the wire — backlog item 50.
 *
 * <p><b>The point of asserting it here rather than in a component spec</b> is that the console can
 * only render what the response carries: the rule that a patient's name may cross this boundary and
 * their blood group may not is a property of this endpoint, and a browser-side assertion would pass
 * against a server that had shipped the whole document and a client that happened to ignore it.
 *
 * <p>The client is mocked, which is deliberate and is the only thing mocked. What hc-patient
 * actually answers is {@code PatientServiceClientTest}'s question, over a loopback HTTP server;
 * whether their running stack really names an hc-admin administrator's patients is the quality
 * stack's, and it was checked by hand on 2026-09-09 (admin 200, operator 404, operator's own
 * patients 200). What is left — which rows are decorated, and what a decorated row looks like as
 * JSON — is this file's.
 *
 * <p>A class of its own rather than more cases in {@link DirectoryLinkResourceIT}: a
 * {@code @MockitoBean} changes the context cache key for every case in the class that declares it,
 * and that file has thirty-odd cases with no interest in this client.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
class DirectoryLinkNameResolutionIT {

    private static final String LEARNED = "kojo@jac.net";
    private static final String UNKNOWN_TO_THEM = "naa.adjeley@mail.gh";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    @MockitoBean
    private PatientServiceClient patientServiceClient;

    @BeforeEach
    @AfterEach
    void clean() {
        directoryLinkRepository.deleteAll();
    }

    /**
     * The name is on the row, and <b>not one clinical field is</b>.
     *
     * <p>Item 50's consequence (a): their endpoint returns the whole {@code Profile} — 27 fields,
     * counted against their running stack — and this service reads two of them. The absence
     * assertions name the four worst individually rather than counting keys, because a count passes
     * on the day a twenty-eighth field is added and quietly forwarded.
     */
    @Test
    void putsTheResolvedNameOnTheRowAndNothingElseFromTheirProfile() throws Exception {
        directoryLinkRepository.save(patientLink(LEARNED, "a15"));
        when(patientServiceClient.resolveName(LEARNED)).thenReturn(new ResolvedName(NameResolution.RESOLVED, "Kojo Ampia-Addison"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "a15").param("resolveNames", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].resolvedName").value("Kojo Ampia-Addison"))
            .andExpect(jsonPath("$[0].nameResolution").value("RESOLVED"))
            .andExpect(jsonPath("$[0].bloodGroup").doesNotExist())
            .andExpect(jsonPath("$[0].cardNumber").doesNotExist())
            .andExpect(jsonPath("$[0].birthDate").doesNotExist())
            .andExpect(jsonPath("$[0].careAngelPhone").doesNotExist())
            .andExpect(jsonPath("$[0].patientId").doesNotExist());
    }

    /**
     * A 404 from them is a row with an outcome and no name — which renders exactly as it did before
     * item 50, and says so rather than saying nothing.
     */
    @Test
    void anAddressTheyDoNotHoldIsNotFoundWithNoName() throws Exception {
        directoryLinkRepository.save(patientLink(UNKNOWN_TO_THEM, "a13"));
        when(patientServiceClient.resolveName(UNKNOWN_TO_THEM)).thenReturn(new ResolvedName(NameResolution.NOT_FOUND, null));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "a13").param("resolveNames", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nameResolution").value("NOT_FOUND"))
            .andExpect(jsonPath("$[0].resolvedName").doesNotExist())
            .andExpect(jsonPath("$[0].email").value(UNKNOWN_TO_THEM));
    }

    /**
     * And a stack that could not be reached is {@code UNAVAILABLE} — the one outcome the console
     * adds a sentence for.
     *
     * <p>The row still carries the address, so the screen degrades to item 45's rendering plus a note
     * rather than to a blank.
     */
    @Test
    void aTransportFailureIsUnavailableAndTheAddressStillTravels() throws Exception {
        directoryLinkRepository.save(patientLink(LEARNED, "a15"));
        when(patientServiceClient.resolveName(LEARNED)).thenReturn(new ResolvedName(NameResolution.UNAVAILABLE, null));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "a15").param("resolveNames", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nameResolution").value("UNAVAILABLE"))
            .andExpect(jsonPath("$[0].resolvedName").doesNotExist())
            .andExpect(jsonPath("$[0].email").value(LEARNED));
    }

    /**
     * <b>Without the parameter nothing is asked and nothing is added.</b>
     *
     * <p>Three callers read this handler and only one wants names. Asserted on the call count as
     * well as on the response, because an undecorated row is also what a failed lookup produces.
     */
    @Test
    void asksNobodyWhenTheCallerDidNotAsk() throws Exception {
        directoryLinkRepository.save(patientLink(LEARNED, "a15"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "a15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nameResolution").doesNotExist())
            .andExpect(jsonPath("$[0].resolvedName").doesNotExist());

        verify(patientServiceClient, never()).resolveName(anyString());
    }

    /**
     * A blank {@code resolveNames=} means "no", and is not a 400.
     *
     * <p>Unlike {@code source}, {@code unlinked} and {@code planStatus}, this parameter has a safe
     * empty reading: the endpoint answers as it did before item 50. The three filters have none,
     * because a filter that vanishes is a query that returns everything.
     */
    @Test
    void aBlankResolveNamesIsAnAnswerRatherThanAnError() throws Exception {
        directoryLinkRepository.save(patientLink(LEARNED, "a15"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "a15").param("resolveNames", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nameResolution").doesNotExist());

        verify(patientServiceClient, never()).resolveName(anyString());
    }

    /**
     * <b>One page of nameless rows costs one call to this service and one lookup per distinct
     * address</b>, and a clinician on the same page costs nothing at all.
     *
     * <p>Two properties in one case because they are the same request. The first is item 50's
     * consequence (c) — the console makes one call per page and the fan-out is here, not in the
     * browser. The second is that a clinician's row is left with no outcome on it: their
     * correlation key is a UUID and hc-patient's endpoint is keyed by address, so asking would be a
     * guaranteed 404 per clinician per page, and an {@code UNAVAILABLE} on their row would tell the
     * console a lookup had failed when none was owed.
     */
    @Test
    void resolvesEveryPatientRowOnThePageInOneRequestAndLeavesAClinicianAlone() throws Exception {
        directoryLinkRepository.save(patientLink(LEARNED, "a15"));
        directoryLinkRepository.save(patientLink(UNKNOWN_TO_THEM, "a13"));
        directoryLinkRepository.save(clinicianLink());
        when(patientServiceClient.resolveName(LEARNED)).thenReturn(new ResolvedName(NameResolution.RESOLVED, "Kojo Ampia-Addison"));
        when(patientServiceClient.resolveName(UNKNOWN_TO_THEM)).thenReturn(new ResolvedName(NameResolution.NOT_FOUND, null));

        // Sorted, so the clinician is at a known index — the two patient links are seeded with one
        // timestamp and a filter expression cannot express "and no key at all": a row serialized
        // with an explicit null answers `[null]`, which is not the same assertion.
        mvc
            .perform(get("/api/directory-links").param("resolveNames", "true").param("size", "20").param("sort", "firstSeenAt,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].source").value("HC_PROFESSIONAL"))
            .andExpect(jsonPath("$[0].nameResolution").doesNotExist())
            .andExpect(jsonPath("$[0].resolvedName").doesNotExist());

        verify(patientServiceClient, times(2)).resolveName(anyString());
    }

    private static DirectoryLink patientLink(String email, String localId) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(email);
        link.setEmail(email);
        link.setLocalId(localId);
        link.setActivated(true);
        link.setFirstSeenAt(Instant.parse("2026-09-08T08:14:00Z"));
        link.setLastEventAt(Instant.parse("2026-09-08T08:14:00Z"));
        return link;
    }

    private static DirectoryLink clinicianLink() {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PROFESSIONAL);
        link.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        link.setExternalKey("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11");
        link.setLogin("kquartey");
        link.setEmail("k.quartey@abofonsa.care");
        link.setFirstSeenAt(Instant.parse("2026-09-03T13:20:00Z"));
        return link;
    }
}
