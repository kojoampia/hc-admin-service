package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/directory-links/registrations} — the estate-wide half of backlog item 75's
 * dashboard.
 *
 * <h2>⚠ The third bucket is what most of this class is about</h2>
 *
 * <p>The item asked for a two-way split, activated against not-activated.
 * {@code DirectoryLink.activated} is a boxed {@code Boolean} because a clinician known only from an
 * {@code onboarding.state} frame has no answer at all — and folding that into "not activated" is a
 * defect this service has already shipped once, recorded on that field: the reconciliation read
 * {@code state != "AccountCreated"} as activated, so five event types rebuilt one way while the
 * consumer said the other for the same frames.
 *
 * <p>So the three states are mutated <b>separately</b> here, and there are cases whose only purpose
 * is to fail if two of them are conflated. An aggregate assertion cannot do that: with one link in
 * each state, {@code notActivated == 1} and {@code notReported == 1} are both satisfied by a service
 * that has quietly merged them and by one that has not, as long as the total comes out right.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
class DirectoryRegistrationTotalsIT {

    private static final String PATH = "/api/directory-links/registrations";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    /** For the one row no mapper in this service produces — see the explicit-null case. */
    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        directoryLinkRepository.deleteAll();
    }

    /**
     * All three buckets at once, with <b>different</b> counts in each.
     *
     * <p>Deliberately 3 / 2 / 4 rather than one apiece: equal counts would let any two buckets be
     * transposed with nothing to show for it, which is the same class of mistake as conflating them.
     */
    @Test
    void theThreeStatesAreCountedSeparately() throws Exception {
        save(DirectorySource.HC_PATIENT, "a@x", Boolean.TRUE);
        save(DirectorySource.HC_PATIENT, "b@x", Boolean.TRUE);
        save(DirectorySource.HC_PATIENT, "c@x", Boolean.TRUE);
        save(DirectorySource.HC_PATIENT, "d@x", Boolean.FALSE);
        save(DirectorySource.HC_PATIENT, "e@x", Boolean.FALSE);
        save(DirectorySource.HC_PROFESSIONAL, "f@x", null);
        save(DirectorySource.HC_PROFESSIONAL, "g@x", null);
        save(DirectorySource.HC_PROFESSIONAL, "h@x", null);
        save(DirectorySource.HC_PROFESSIONAL, "i@x", null);

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activated").value(3))
            .andExpect(jsonPath("$.notActivated").value(2))
            .andExpect(jsonPath("$.notReported").value(4))
            .andExpect(jsonPath("$.total").value(9));
    }

    /**
     * <b>The case this item warned about.</b> One link that no event has answered for, and nothing
     * else: {@code notReported} must be 1 and {@code notActivated} must be <b>0</b>.
     *
     * <p>The second assertion is the whole case. Read the group key with
     * {@code Document.getBoolean("activated", false)} — the obvious Java, and what a reader reaches
     * for — and {@code notActivated} becomes 1 while every total still adds up, so this endpoint would
     * report a clinician as barred from signing in on the strength of an event nobody sent.
     */
    @Test
    void aLinkNothingHasAnsweredForIsNotReportedAndIsNotCountedAsNotActivated() throws Exception {
        save(DirectorySource.HC_PROFESSIONAL, "unanswered@x", null);

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notReported").value(1))
            .andExpect(jsonPath("$.notActivated").value(0))
            .andExpect(jsonPath("$.activated").value(0));
    }

    /**
     * The mirror, so the two cases together pin the boundary from both sides.
     *
     * <p>Without this one, a service that reported <em>everything</em> as "not reported" would pass
     * the case above — and would be exactly as wrong, in the other direction.
     */
    @Test
    void aLinkAnEventSaidNoAboutIsNotActivatedAndIsNotUnreported() throws Exception {
        save(DirectorySource.HC_PROFESSIONAL, "deactivated@x", Boolean.FALSE);

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notActivated").value(1))
            .andExpect(jsonPath("$.notReported").value(0));
    }

    /**
     * A link with the field <b>absent</b> and one with it <b>explicitly null</b> both read as not
     * reported — two rows, both in one bucket.
     *
     * <p>The distinction is not academic. Spring Data omits a null property when it writes, so
     * {@link #save} produces the <em>absent</em> form and nothing in this repository produces the
     * other; the explicit one is written here through the raw collection. MongoDB's {@code $group}
     * gives both the same {@code null} key, so they are folded by the query rather than by a decision
     * in Java — and that is worth pinning, because the absent form is exactly what a production
     * collection predating a field looks like, and a reader who assumed only the explicit form
     * mattered would write a {@code $ne: null} filter that quietly drops half of them.
     */
    @Test
    void anAbsentActivatedFieldAndAnExplicitNullBothReadAsNotReported() throws Exception {
        // Absent: Spring Data does not write a null property.
        save(DirectorySource.HC_PROFESSIONAL, "field-absent@x", null);
        // Explicit null, written straight to the collection because no mapper here produces one.
        mongoTemplate
            .getCollection("directory_link")
            .insertOne(
                new Document("source", DirectorySource.HC_PROFESSIONAL.name())
                    .append("external_key", "explicit-null@x")
                    .append("activated", null)
            );

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notReported").value(2))
            .andExpect(jsonPath("$.notActivated").value(0))
            .andExpect(jsonPath("$.total").value(2));
    }

    /**
     * Every source has a row, including one this service has never heard from.
     *
     * <p>All zeroes rather than a missing entry: on a screen, a source that has vanished from the
     * response is indistinguishable from a source that is quiet, and those need opposite responses —
     * a broken consumer and a slow week.
     */
    @Test
    void everySourceHasARowEvenWhenItHasSaidNothing() throws Exception {
        save(DirectorySource.HC_PATIENT, "only-patient@x", Boolean.TRUE);

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bySource.length()").value(DirectorySource.values().length))
            .andExpect(jsonPath("$.bySource[?(@.source == 'HC_PROFESSIONAL')].total").value(0))
            .andExpect(jsonPath("$.bySource[?(@.source == 'HC_PATIENT')].activated").value(1));
    }

    /**
     * A care angel and an erased subject are counted, which is a decision rather than an accident.
     *
     * <p>Both registered. The link for an erased subject is kept because it holds the watermark, and
     * dropping it here would make this total fall when a deletion completes — which reads as data
     * loss. Both also appear in {@code GET /api/directory-links}, and a headline that silently
     * disagrees with the list one click away is worse than one that needs a sentence.
     */
    @Test
    void careAngelsAndErasedSubjectsAreCountedRatherThanQuietlyDropped() throws Exception {
        DirectoryLink angel = save(DirectorySource.HC_PATIENT, "angel@x", Boolean.TRUE);
        angel.setSubjectKind(DirectorySubjectKind.CARE_ANGEL);
        directoryLinkRepository.save(angel);

        DirectoryLink erased = save(DirectorySource.HC_PATIENT, "erased@x", Boolean.TRUE);
        erased.setErasedAt(Instant.now());
        directoryLinkRepository.save(erased);

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.activated").value(2));
    }

    /** Nothing learned yet is zeroes, not an error and not an empty body. */
    @Test
    void anEmptyDirectoryAnswersWithZeroes() throws Exception {
        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.activated").value(0))
            .andExpect(jsonPath("$.notActivated").value(0))
            .andExpect(jsonPath("$.notReported").value(0));
    }

    private DirectoryLink save(DirectorySource source, String externalKey, Boolean activated) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(source);
        link.setExternalKey(externalKey);
        link.setActivated(activated);
        link.setSubjectKind(source == DirectorySource.HC_PATIENT ? DirectorySubjectKind.PATIENT : DirectorySubjectKind.PROFESSIONAL);
        link.setFirstSeenAt(Instant.now());
        link.setLastEventAt(Instant.now());
        return directoryLinkRepository.save(link);
    }
}
