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
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
            .andExpect(jsonPath("$.alreadyPresent").value(0));

        assertThat(patientRepository.count()).isEqualTo(1);
        assertThat(directoryLinkRepository.findSubject(DirectorySource.HC_PATIENT, EMAIL).orElseThrow().getLocalId())
            .as("the link should now name the record it rebuilt")
            .isNotEqualTo("an-id-that-no-longer-exists");
    }

    /**
     * Running it twice creates nothing the second time.
     *
     * <p>The reconciliation goes through the same idempotent path a live message does, which is why
     * the two were built together — a backfill with its own write is a second place for the merge
     * rule to be got wrong.
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

    private DirectoryLink link(String externalKey, String localId) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PATIENT);
        link.setExternalKey(externalKey);
        link.setEmail(externalKey);
        link.setLogin("kasante");
        link.setState("AccountActivated");
        link.setLocalId(localId);
        link.setFirstSeenAt(Instant.parse("2026-08-20T10:00:00Z"));
        link.setLastEventAt(Instant.parse("2026-08-20T10:00:00Z"));
        return link;
    }
}
