package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The read surface over what the broker has taught this directory, and the reconciliation.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
class DirectoryLinkResourceIT {

    private static final String EMAIL = "kofi.asante@directory-link-resource-it.example.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        directoryLinkRepository.deleteAll();
        patientRepository.deleteAll();
    }

    @Test
    void listsTheLinksWithPaginationHeaders() throws Exception {
        directoryLinkRepository.save(link(EMAIL, null));

        mvc
            .perform(get("/api/directory-links").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$[0].externalKey").value(EMAIL))
            .andExpect(jsonPath("$[0].source").value("HC_PATIENT"));
    }

    @Test
    void filtersBySource() throws Exception {
        directoryLinkRepository.save(link(EMAIL, null));

        mvc
            .perform(get("/api/directory-links").param("source", "HC_PATIENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isNotEmpty());
        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * The read behind the patient directory's name column, for a patient learned from an event.
     *
     * <p>Such a patient has no {@code Profile} and can never be given one from the wire, so the row's
     * only identity is on the link. The console sends the ids of the nameless rows on the page it is
     * showing — one request, not one per row — and this is that request.
     */
    @Test
    void filtersByLocalIdSoOnePageCostsOneRequest() throws Exception {
        DirectoryLink wanted = directoryLinkRepository.save(link(EMAIL, "patient-on-screen"));
        directoryLinkRepository.save(link("someone.else@" + EMAIL, "patient-on-another-page"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "patient-on-screen", "an-id-with-no-link"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(wanted.getId()))
            .andExpect(jsonPath("$[0].localId").value("patient-on-screen"))
            // The address is what the screen renders in place of a name. This endpoint may serve it;
            // a log may not — the class javadoc argues why those are consistent.
            .andExpect(jsonPath("$[0].email").value(EMAIL));
    }

    /** The two filters compose rather than one silently winning. */
    @Test
    void combinesTheSourceAndLocalIdFilters() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "patient-on-screen"));

        mvc
            .perform(get("/api/directory-links").param("source", "HC_PATIENT").param("localId.in", "patient-on-screen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL").param("localId.in", "patient-on-screen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * Asking for the links of no records is not asking for all of them.
     *
     * <p>{@code ?localId.in=} binds to a list holding one empty string, the handler drops the blank,
     * and {@code NamedFilters} then drops the emptied collection — a filter that vanishes is a query
     * that returns everything, which that class's own javadoc warns about at the call site.
     *
     * <p><b>Verified by inversion rather than by reading it:</b> with the handler's early return
     * disabled this case fails with {@code X-Total-Count expected:<0> but was:<1>}, so the guard is
     * what produces the answer and the case is not passing on the {@code $in: [""]} it would
     * otherwise have built.
     */
    @Test
    void anEmptyLocalIdFilterMatchesNothingRatherThanEverything() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "patient-on-screen"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", ""))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "0"))
            .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * Refused rather than truncated.
     *
     * <p>A silently shortened filter returns fewer links than the caller named, and the console would
     * render the remainder as unresolved — which is the defect this parameter was added to fix,
     * wearing a different cause.
     */
    @Test
    void refusesMoreLocalIdsThanAPageCouldHold() throws Exception {
        String[] tooMany = new String[DirectoryLinkResource.MAX_LOCAL_IDS + 1];
        java.util.Arrays.setAll(tooMany, index -> "id-" + index);

        mvc.perform(get("/api/directory-links").param("localId.in", tooMany)).andExpect(status().isBadRequest());
    }

    /**
     * A link whose local record has gone — a restore from a backup older than the consumer group's
     * committed offsets, or a row deleted by hand.
     *
     * <p>Re-reading the topic cannot fix this: the offsets are already past those messages, and
     * moving them is an operation on the broker rather than on this service.
     */
    @Test
    void reconcileRebuildsAMissingRecord() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "an-id-that-no-longer-exists"));

        mvc
            .perform(post("/api/directory-links/reconcile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.examined").value(1))
            .andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.alreadyPresent").value(0))
            .andExpect(jsonPath("$.skipped").value(0));

        assertThat(patientRepository.count()).isEqualTo(1);
        assertThat(directoryLinkRepository.findSubject(DirectorySource.HC_PATIENT, EMAIL).orElseThrow().getLocalId())
            .as("the link should now name the record it rebuilt")
            .isNotEqualTo("an-id-that-no-longer-exists");
    }

    /**
     * <b>The reconciliation reads the status the consumer reached; it does not re-derive one.</b>
     *
     * <p>It used to derive "activated" as any state other than the literal {@code AccountCreated}, so
     * a link last seen in {@code OnboardingStarted} — or any of the four other types, or any type it
     * did not know — rebuilt as {@code ACTIVE}, while the consumer says those events are not evidence
     * an account can sign in at all. Two derivations of one rule, disagreeing, under a javadoc
     * claiming they were the same path. The fixture in this class hard-coded
     * {@code state = "AccountActivated"}, which is why nothing here saw it.
     */
    @Test
    void reconcileDoesNotInventAnActiveAccountFromTheLastEventType() throws Exception {
        DirectoryLink onboarding = link(EMAIL, null);
        onboarding.setState("OnboardingStarted");
        onboarding.setActivated(null);
        directoryLinkRepository.save(onboarding);

        mvc.perform(post("/api/directory-links/reconcile")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        assertThat(patientRepository.findAll().get(0).getStatus())
            .as("beginning onboarding is not evidence the account can sign in, and the consumer says so too")
            .isEqualTo(AccountStatus.PENDING);
    }

    /** And where the stream did say so, the rebuilt record says so — the same stored answer. */
    @Test
    void reconcileRestoresAnActivatedAccountAsActive() throws Exception {
        DirectoryLink activated = link(EMAIL, null);
        activated.setActivated(true);
        directoryLinkRepository.save(activated);

        mvc.perform(post("/api/directory-links/reconcile")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        assertThat(patientRepository.findAll().get(0).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    /**
     * The lookup index exists, and it is unique.
     *
     * <p>Two things at once, and the collection had neither. {@code (source, external_key)} is read on
     * every message and re-read in full on every backfill, against a collection that grows at the rate
     * two other stacks create accounts — without an index that is a collection scan per event. And
     * uniqueness is what the idempotency claim actually needs: {@code findAndModify(upsert)} is atomic
     * per document, but MongoDB documents that two concurrent upserts matching nothing can both
     * insert, and names a unique index on the query field as the requirement.
     *
     * <p>This service creates no indexes by convention — no {@code @Indexed} anywhere,
     * {@code auto-index-creation} off — so it is created explicitly at startup rather than declared on
     * the document, where it would have been a comment.
     */
    @Test
    void theSubjectKeyIsIndexedAndUnique() {
        assertThat(mongoTemplate.indexOps(DirectoryLink.class).getIndexInfo())
            .as("directory_link is read on (source, external_key) for every single message")
            .anySatisfy(index -> {
                assertThat(index.getIndexFields().stream().map(IndexField::getKey)).containsExactly("source", "external_key");
                assertThat(index.isUnique()).as("without uniqueness two concurrent first sightings can both insert").isTrue();
            });
    }

    /**
     * Running it twice creates nothing the second time.
     *
     * <p>The reconciliation shares {@code createAndClaim} with the consumer and reads the status the
     * consumer stored, which is as close to "the same path" as the two can honestly be — one is
     * applying an event and the other has none. A backfill with its own write is a second place for
     * the merge rule to be got wrong, which is exactly what happened to the status derivation above.
     */
    @Test
    void reconcileIsSafeToRunTwice() throws Exception {
        directoryLinkRepository.save(link(EMAIL, null));

        mvc.perform(post("/api/directory-links/reconcile")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
        mvc
            .perform(post("/api/directory-links/reconcile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(0))
            .andExpect(jsonPath("$.alreadyPresent").value(1));

        assertThat(patientRepository.count()).as("a second run must not add a person").isEqualTo(1);
    }

    /**
     * A link whose record is present is left entirely alone, including the fields an administrator
     * owns. The reconciliation rebuilds what is missing; it does not restate what is there.
     */
    @Test
    void reconcileDoesNotTouchAnExistingRecord() throws Exception {
        Patient patient = patientRepository.save(
            new Patient().status(AccountStatus.SUSPENDED).joinedOn(LocalDate.of(2026, 1, 1)).caseCount(4)
        );
        directoryLinkRepository.save(link(EMAIL, patient.getId()));

        mvc.perform(post("/api/directory-links/reconcile")).andExpect(status().isOk()).andExpect(jsonPath("$.alreadyPresent").value(1));

        Patient after = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(after.getCaseCount()).isEqualTo(4);
        assertThat(after.getJoinedOn()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    /**
     * A link that keeps no local record is not one the reconciliation rebuilds.
     *
     * <p>Its {@code localId} is null, which is the shape the loop is looking for, and rebuilding it
     * would put back exactly what the consumer refuses to create — a care angel as a patient, or
     * somebody hc-patient has erased. Reported as {@code skipped} rather than folded into
     * {@code alreadyPresent}, because those links have no record and are supposed not to.
     */
    @Test
    void reconcileSkipsTheLinksThatKeepNoRecord() throws Exception {
        DirectoryLink angel = link("angel@" + EMAIL, null);
        angel.setSubjectKind(DirectorySubjectKind.CARE_ANGEL);
        directoryLinkRepository.save(angel);

        DirectoryLink erased = link("erased@" + EMAIL, null);
        erased.setErasedAt(Instant.parse("2026-08-21T10:00:00Z"));
        directoryLinkRepository.save(erased);

        mvc
            .perform(post("/api/directory-links/reconcile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.examined").value(2))
            .andExpect(jsonPath("$.created").value(0))
            .andExpect(jsonPath("$.skipped").value(2));

        assertThat(patientRepository.count()).as("neither an angel nor an erased subject is rebuilt as a patient").isZero();
    }

    private DirectoryLink link(String externalKey, String localId) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PATIENT);
        link.setExternalKey(externalKey);
        link.setEmail(externalKey);
        link.setLogin("kasante");
        link.setState("AccountActivated");
        link.setActivated(true);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setLocalId(localId);
        link.setFirstSeenAt(Instant.parse("2026-08-20T10:00:00Z"));
        link.setLastEventAt(Instant.parse("2026-08-20T10:00:00Z"));
        return link;
    }
}
