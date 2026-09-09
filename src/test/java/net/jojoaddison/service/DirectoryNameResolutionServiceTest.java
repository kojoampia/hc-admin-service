package net.jojoaddison.service;

import static net.jojoaddison.config.ApplicationPropertiesFixture.resolveBudget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.service.PatientServiceClient.ResolvedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Which rows are asked about, how often, and what a row that was never a candidate looks like —
 * backlog item 50.
 *
 * <p>Over a mocked {@link PatientServiceClient}, because everything asserted here is a decision this
 * class makes before a socket would be opened. {@link PatientServiceClientTest} covers what happens
 * once one is.
 */
class DirectoryNameResolutionServiceTest {

    private final PatientServiceClient client = mock(PatientServiceClient.class);

    /**
     * Configured, unless a case says otherwise.
     *
     * <p>A mock answers {@code false} to a boolean by default, which is the one value that makes
     * {@link DirectoryNameResolutionService#resolve(List)} return before it does anything — so
     * without this every case below would assert against the disabled short circuit and pass for the
     * wrong reason. Stated here rather than per case, and {@link #aDisabledClientMarksTheCandidatesAndIsNeverCalled}
     * is the one that turns it off.
     */
    @BeforeEach
    void theClientIsConfigured() {
        when(client.isEnabled()).thenReturn(true);
    }

    /**
     * A patient link with an address is asked about, and the name lands on the transient field.
     *
     * <p>The document is handed back mutated and is never saved — item 27(a)'s rule, and the reason
     * the field is {@code @Transient} rather than stored.
     */
    @Test
    void namesAPatientLinkFromTheAddressOnIt() {
        when(client.resolveName("naa.adjeley@mail.gh")).thenReturn(new ResolvedName(NameResolution.RESOLVED, "Naa Adjeley"));
        DirectoryLink link = patientLink("naa.adjeley@mail.gh");

        service(4000).resolve(List.of(link));

        assertThat(link.getResolvedName()).isEqualTo("Naa Adjeley");
        assertThat(link.getNameResolution()).isEqualTo(NameResolution.RESOLVED);
    }

    /**
     * <b>A row nobody could have asked about carries no outcome at all.</b>
     *
     * <p>Absent is "never a candidate" and {@code UNAVAILABLE} is "asked and no answer", and the
     * console branches on the difference: a clinician's row must not acquire a note saying their
     * name could not be looked up, which would be true of nothing.
     *
     * <p>Two ineligible shapes, and both are refusals this class makes rather than 404s it collects.
     * hc-professional's endpoint is not this one and its correlation key is a UUID; a patient link
     * with no address has nothing to look up.
     */
    @Test
    void leavesAClinicianAndAnAddresslessRowUntouchedRatherThanMarkingThemUnavailable() {
        DirectoryLink clinician = new DirectoryLink();
        clinician.setSource(DirectorySource.HC_PROFESSIONAL);
        clinician.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        clinician.setEmail("k.quartey@abofonsa.care");
        DirectoryLink addressless = patientLink(null);

        service(4000).resolve(List.of(clinician, addressless));

        assertThat(clinician.getNameResolution()).isNull();
        assertThat(clinician.getResolvedName()).isNull();
        assertThat(addressless.getNameResolution()).isNull();
        verify(client, never()).resolveName(anyString());
    }

    /**
     * A page where nothing is a candidate makes <b>no request at all</b>.
     *
     * <p>The common case and the one that has to stay free: a directory of ordinary patients has no
     * links on it, and a page of clinicians has no addresses this endpoint could use. Asserted on
     * the call count rather than on the rows, because "the rows are undecorated" is also true of a
     * page that asked and got nothing.
     */
    @Test
    void aPageWithNoCandidatesCostsNoRequests() {
        service(4000).resolve(List.of());
        service(4000).resolve(List.of(patientLink(" ")));

        verify(client, never()).resolveName(any());
    }

    /**
     * One address is asked about once, however many rows carry it.
     *
     * <p>The per-request cache. Its <b>lifetime</b> is the decision — a map that outlived the request
     * would be a stored copy of a name hc-patient owns, which item 27(a) forbids, and it would go
     * stale exactly when it matters. Deduping inside one page cannot go stale because everything in
     * it was read in the same second.
     */
    @Test
    void asksOncePerAddressWithinOnePage() {
        when(client.resolveName("kojo@jac.net")).thenReturn(new ResolvedName(NameResolution.RESOLVED, "Kojo Ampia-Addison"));
        DirectoryLink first = patientLink("kojo@jac.net");
        DirectoryLink second = patientLink("KOJO@JAC.NET");

        service(4000).resolve(List.of(first, second));

        verify(client, times(1)).resolveName(anyString());
        assertThat(first.getResolvedName()).isEqualTo("Kojo Ampia-Addison");
        assertThat(second.getResolvedName()).as("the second row is answered from the page's own map").isEqualTo("Kojo Ampia-Addison");
    }

    /**
     * <b>Once the budget is spent the rest of the page is reported unavailable without being
     * dialled.</b>
     *
     * <p>This runs on the request thread, one blocking call per nameless row, so a page of twenty
     * against a sibling that accepts connections and never answers is twenty read timeouts in
     * series. Backlog item 39a is the same shape one component along. The budget is zero here so the
     * first row spends it; a real one is 4000ms.
     *
     * <p>The outcome is honest: nobody asked, which is exactly what {@code UNAVAILABLE} means, and
     * the console shows the address it showed before item 50 rather than a wrong name.
     */
    @Test
    void stopsAskingOnceTheBudgetIsSpentAndSaysSoOnTheRemainingRows() {
        when(client.resolveName(anyString())).thenReturn(new ResolvedName(NameResolution.RESOLVED, "Kojo Ampia-Addison"));
        DirectoryLink first = patientLink("one@mail.gh");
        DirectoryLink second = patientLink("two@mail.gh");
        DirectoryLink third = patientLink("three@mail.gh");

        service(0).resolve(List.of(first, second, third));

        assertThat(first.getNameResolution()).isEqualTo(NameResolution.UNAVAILABLE);
        assertThat(second.getNameResolution()).isEqualTo(NameResolution.UNAVAILABLE);
        assertThat(third.getNameResolution()).isEqualTo(NameResolution.UNAVAILABLE);
        verify(client, never()).resolveName(anyString());
    }

    /**
     * <b>A deployment that is not configured to ask marks its candidates without asking, and without
     * counting a lookup.</b>
     *
     * <p>Two properties, and the second is why {@link PatientServiceClient#isEnabled()} is consulted
     * here rather than left to the client's own refusal. The rows must still carry an outcome — they
     * are candidates, and a candidate with no outcome tells the console it was never one — but
     * nothing may be recorded as a lookup that opened no socket, or the budget warning reports
     * network activity to a reader on a machine that has none.
     *
     * <p>This is the state {@code deploy/e2e/compose.yml} and every integration test in the
     * repository run in, so it is also the state a reader is most likely to be looking at.
     */
    @Test
    void aDisabledClientMarksTheCandidatesAndIsNeverCalled() {
        when(client.isEnabled()).thenReturn(false);
        // Stubbed although it must never be reached, so that a regression fails on the assertion
        // below rather than on a null the mock would otherwise return: "it threw" and "it asked" are
        // not the same finding, and only one of them is this case's.
        when(client.resolveName(anyString())).thenReturn(new ResolvedName(NameResolution.UNAVAILABLE, null));
        DirectoryLink candidate = patientLink("kojo@jac.net");
        DirectoryLink clinician = new DirectoryLink();
        clinician.setSource(DirectorySource.HC_PROFESSIONAL);
        clinician.setEmail("k.quartey@abofonsa.care");

        service(4000).resolve(List.of(candidate, clinician));

        assertThat(candidate.getNameResolution()).isEqualTo(NameResolution.UNAVAILABLE);
        assertThat(clinician.getNameResolution()).as("still not a candidate, disabled or not").isNull();
        verify(client, never()).resolveName(anyString());
    }

    /**
     * A blank name from a {@code RESOLVED} lookup is stored as null, not as an empty string.
     *
     * <p>hc-patient's {@code Profile} does not require a name, so the state is real. An empty string
     * on the row would be printed as somebody's name — falsy in the template's terms but not
     * nullish, which is the trap {@code Patient.location()} documents one screen along.
     */
    @Test
    void aResolvedLookupWithNoNameLeavesTheFieldNull() {
        when(client.resolveName(anyString())).thenReturn(new ResolvedName(NameResolution.RESOLVED, "   "));
        DirectoryLink link = patientLink("nameless@mail.gh");

        service(4000).resolve(List.of(link));

        assertThat(link.getNameResolution()).isEqualTo(NameResolution.RESOLVED);
        assertThat(link.getResolvedName()).isNull();
    }

    private DirectoryNameResolutionService service(long budgetMs) {
        return new DirectoryNameResolutionService(client, resolveBudget(budgetMs));
    }

    private static DirectoryLink patientLink(String email) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setEmail(email);
        return link;
    }
}
