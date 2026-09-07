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
     * <b>The read behind the clinician directory's awaiting-a-record panel.</b>
     *
     * <p>A clinician who registers on hc-professional produces a link and no {@code Professional} at
     * all, so {@code GET /api/professionals} cannot show them however it is filtered — the console
     * asked no other question, and a registration on production was stored and invisible (backlog
     * item 46). This is the other question.
     *
     * <p>The two directions are asserted together on purpose: {@code unlinked=true} and
     * {@code unlinked=false} have to partition the collection, and a filter that quietly matched
     * nothing would pass a one-sided test while emptying the panel.
     */
    @Test
    void filtersToTheLinksThatHaveNoLocalRecord() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "a-patient-with-a-record"));
        directoryLinkRepository.save(clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"));

        mvc
            .perform(get("/api/directory-links").param("unlinked", "true"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].externalKey").value("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"))
            .andExpect(jsonPath("$[0].source").value("HC_PROFESSIONAL"));

        mvc
            .perform(get("/api/directory-links").param("unlinked", "false"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].localId").value("a-patient-with-a-record"));

        // And with the filter absent, both — "do not ask" is a third answer and not a synonym for
        // either of the two above.
        mvc.perform(get("/api/directory-links")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"));
    }

    /**
     * A link with no {@code local_id} field at all matches, not only one carrying an explicit null.
     *
     * <p>This is what the collection really holds: {@code DirectoryProjectionService.setIfPresent}
     * never writes the field until there is a record to name, so every clinician's link is written
     * <em>without</em> it.
     *
     * <p><b>The reason given for this case until 2026-09-07 was backwards</b>, here and in the commit
     * message: both said an {@code exists: false} filter "would pass against a fixture built through
     * the Java setter and match nothing in production". It is the other way round —
     * {@code MappingMongoConverter} omits a null property rather than writing an explicit null, so a
     * {@code DirectoryLink} saved through the setter with no local id has no {@code local_id} field
     * either, exactly like the document the projection writes. {@code exists: false} would have
     * matched both. {@code is(null)} is still the right filter, because it is the same match
     * {@code createAndClaim} uses and it covers an explicit null if one is ever written; the case
     * still earns its place by inserting the raw document, since that is the shape production really
     * holds and a fixture is not evidence about it. Only the justification was wrong.
     */
    @Test
    void anUnwrittenLocalIdCountsAsUnlinked() throws Exception {
        mongoTemplate.insert(
            org.bson.Document.parse(
                """
                { "source": "HC_PROFESSIONAL", "external_key": "written-by-the-projection",
                  "subject_kind": "PROFESSIONAL", "state": "APPLICATION_SUBMITTED" }
                """
            ),
            "directory_link"
        );

        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL").param("unlinked", "true"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].externalKey").value("written-by-the-projection"));
    }

    /**
     * The corrected half of the case above, made executable rather than only written down.
     *
     * <p>A link saved through the Java setter with a null local id carries <b>no</b> {@code local_id}
     * field, because {@code MappingMongoConverter} omits a null property instead of writing an
     * explicit null. That is what makes the old justification backwards: the two fixtures produce the
     * same document, so {@code exists: false} would have matched them both. Asserted here so that a
     * mapping configuration which started writing nulls — {@code MongoMappingContext} can be told to
     * — would redden a case that names the property, rather than quietly making one of these two
     * fixtures stop representing production.
     */
    @Test
    void aNullLocalIdIsNotWrittenToTheDocumentAtAll() {
        DirectoryLink saved = directoryLinkRepository.save(clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"));
        assertThat(saved.getLocalId()).isNull();

        org.bson.Document stored = mongoTemplate.getCollection("directory_link").find().first();

        assertThat(stored).isNotNull();
        assertThat(stored.containsKey("local_id")).as("a null property is omitted, not written as null").isFalse();
    }

    /**
     * <b>A blank {@code unlinked} is a mistake, not a synonym for "do not ask".</b>
     *
     * <p>Item 45's review found that a blank {@code ?localId.in=} could silently become no filter at
     * all; this is the same hole one parameter along, and it is the one that mattered.
     * {@code unlinked} is a {@code Boolean}, and Spring's converter answers {@code null} for the empty
     * string with no exception — measured on this classpath — so
     * {@code ?source=HC_PROFESSIONAL&unlinked=} added no criterion and returned <b>every</b> link of
     * that source.
     *
     * <p>It was not a live defect, which is exactly why it needs a test: the console always sends
     * {@code true} and every {@code HC_PROFESSIONAL} link is unlinked today, so both answers are the
     * same page and no screen could have shown the difference. They diverge the day backlog item 35
     * fills clinician records in, and the panel would then list clinicians who have a record under a
     * heading saying they have none.
     *
     * <p>Inverted, both halves: removing either {@code rejectBlank} call turns the matching case here
     * from {@code 400} into {@code 200} with the whole source in the body, which is why the count is
     * asserted on the control request rather than the error being asserted on its own.
     */
    @Test
    void aBlankUnlinkedIsRefusedRatherThanReadAsAbsent() throws Exception {
        directoryLinkRepository.save(clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"));
        DirectoryLink withRecord = clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a12");
        withRecord.setLocalId("a-clinician-with-a-record");
        directoryLinkRepository.save(withRecord);

        // The control: two links of this source, one of which the panel must never list.
        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"));
        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL").param("unlinked", "true"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"));

        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL").param("unlinked", ""))
            .andExpect(status().isBadRequest());
        // Whitespace binds null too, and " " is not blank to every String check in this codebase.
        mvc.perform(get("/api/directory-links").param("unlinked", " ")).andExpect(status().isBadRequest());
    }

    /**
     * The same hole on the other typed parameter, and it composes with the one above.
     *
     * <p>{@code ?source=} binds null for the identical reason — the enum converter answers null for
     * the empty string — so {@code ?source=&unlinked=true} would have listed hc-patient's care angels
     * and erased subjects in the clinician directory. {@code combinesTheSourceAndUnlinkedFilters}
     * exists because that page must be one query; this exists because the query must have been asked.
     */
    @Test
    void aBlankSourceIsRefusedToo() throws Exception {
        directoryLinkRepository.save(clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"));

        mvc.perform(get("/api/directory-links").param("source", "").param("unlinked", "true")).andExpect(status().isBadRequest());
    }

    /** Absent stays absent: the refusal is about a blank value, not about the parameter being optional. */
    @Test
    void anAbsentFilterIsStillNoFilter() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "a-patient-with-a-record"));
        directoryLinkRepository.save(clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"));

        mvc.perform(get("/api/directory-links")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"));
    }

    /** The source and the unlinked filter compose, which is the request the console actually sends. */
    @Test
    void combinesTheSourceAndUnlinkedFilters() throws Exception {
        // A patient link with no record — an erased subject, or a care angel — is unlinked too, and
        // it is not a clinician. Asking for one source without the other would list it in the
        // professional directory.
        DirectoryLink angel = link("angel@" + EMAIL, null);
        angel.setSubjectKind(DirectorySubjectKind.CARE_ANGEL);
        directoryLinkRepository.save(angel);
        directoryLinkRepository.save(clinician("9f1c3e77-52aa-4a0b-9a5c-6b3f1d7e0a11"));

        mvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL").param("unlinked", "true"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].source").value("HC_PROFESSIONAL"));
    }

    /**
     * Asking for the links of no records is not asking for all of them.
     *
     * <p><b>The mechanism stated here until 2026-09-07 was wrong.</b> This javadoc, the handler's
     * comment and the commit message all said {@code ?localId.in=} binds to a list holding one empty
     * string. Measured against the versions on this classpath, with a probe controller under
     * standalone MockMvc:
     *
     * <pre>
     *   ?localId.in=                 ==&gt; []            an empty list
     *   ?localId.in=,                ==&gt; ["", ""]
     *   ?localId.in=&amp;localId.in=     ==&gt; ["", ""]
     * </pre>
     *
     * <p>A single value reaches the converter as a {@code String} and
     * {@code StringToCollectionConverter} yields nothing for {@code ""}; two values reach it as a
     * {@code String[]} and every element survives.
     *
     * <p><b>Verified by inversion, both halves, and the result is not what the old note claimed
     * either.</b> Removing the handler's early return fails this case <em>and</em>
     * {@link #aFilterOfNothingButBlanksMatchesNothingEither} with
     * {@code X-Total-Count expected:<0> but was:<1>}, so that check is the guard. Removing the
     * blank-strip fails <b>nothing</b> — all 15 cases stay green — because {@code NamedFilters.in}
     * passes blank elements through and no stored {@code local_id} is ever the empty string, so
     * {@code $in: ["", ""]} matches exactly as little as the early return returns. The strip is a
     * normalisation, kept so the three forms above take one path; it is not what makes the answer
     * right, and the class it is in now says so too.
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
     * The form that really does bind blanks — the one the old note thought {@code ?localId.in=} was.
     *
     * <p>It is here because the binding is surprising and the surprise should be executable rather
     * than only written down. It does <b>not</b> prove the blank-strip: see the inversion above.
     */
    @Test
    void aFilterOfNothingButBlanksMatchesNothingEither() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "patient-on-screen"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "", " "))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "0"))
            .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * A blank beside a real id names that id, rather than widening the query or emptying it.
     *
     * <p>The case that would go wrong if somebody "simplified" the emptiness check into a blanket
     * "any blank means match nothing": one stray blank in a page's worth of ids would then unresolve
     * every row on it.
     */
    @Test
    void aBlankBesideARealIdIsDroppedAndTheRealIdStillMatches() throws Exception {
        directoryLinkRepository.save(link(EMAIL, "patient-on-screen"));
        directoryLinkRepository.save(link("someone.else@" + EMAIL, "patient-on-another-page"));

        mvc
            .perform(get("/api/directory-links").param("localId.in", "", "patient-on-screen"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].localId").value("patient-on-screen"));
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

    /**
     * A clinician's link: keyed on an {@code accountId}, and with no local record by design.
     *
     * <p>{@code localId} is not merely unset here, it is unsettable — {@code Professional} requires a
     * {@code role} and a {@code licenceNumber} and no event on that topic carries either.
     */
    private DirectoryLink clinician(String accountId) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PROFESSIONAL);
        link.setExternalKey(accountId);
        link.setExternalId(accountId);
        link.setLogin("kquartey");
        link.setEmail("k.quartey@abofonsa.care");
        link.setState("DOCUMENTS_SUBMITTED");
        link.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        link.setFirstSeenAt(Instant.parse("2026-09-03T13:20:00Z"));
        link.setLastEventAt(Instant.parse("2026-09-05T08:05:00Z"));
        return link;
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
