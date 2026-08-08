package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.domain.DocumentItem;
import net.jojoaddison.domain.enumeration.DocumentType;
import net.jojoaddison.repository.AuditLogRepository;
import net.jojoaddison.repository.DocumentItemRepository;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * The audit trail, which was configured-but-inert before: {@code AuditLog} was a full CRUD entity
 * that nothing in the application ever wrote to.
 *
 * <p>DocumentItem is the subject because it is an ordinary domain document — nothing here is
 * specific to it. It was Team until the console model was applied: the console entities carry no
 * audit fields at all, so the subject has to be one of the entities that predate them. Any of
 * Facility, Person, Photo, SystemCatalog and the rest would do equally well.
 *
 * <h2>Two identifiers, deliberately</h2>
 *
 * <p>{@code AuditLog.userId} records the <b>login</b>; the domain documents' {@code createdBy} and
 * {@code modifiedBy} record the gateway user <b>id</b>. That is not an inconsistency — the seed data
 * puts {@code a0eebc99-…-a11} in {@code createdBy} and CLAUDE.md names those ids a contract shared
 * with hc-patient-ms and hc-professional-service, so those fields have to keep holding ids. The
 * gateway now mints the id as a {@code uid} claim precisely so this service can honour that without
 * a lookup it has no route to make.
 *
 * <p>Tests here run without a JWT principal, so {@link SecurityUtils#getCurrentUserId()} is empty
 * and the auditor falls back to {@code system}. That fallback is asserted below, because it is what
 * seed loading and service-to-service calls will see.
 */
@IntegrationTest
@WithMockUser(username = "alice")
class AuditingIT {

    @Autowired
    private DocumentItemRepository documentItemRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private DocumentItem saved;

    @BeforeEach
    void clearAuditLog() {
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        if (saved != null) {
            documentItemRepository.deleteById(saved.getId());
            saved = null;
        }
        auditLogRepository.deleteAll();
    }

    /**
     * The four audit fields are set here because the domain declares them {@code @NotNull} and the
     * client is what supplies them — which is precisely the limitation documented above, and the
     * reason the trustworthy record is the AuditLog row rather than these.
     */
    private DocumentItem newDocumentItem() {
        Instant now = Instant.now();
        return new DocumentItem()
            .name("Night shift rota")
            .description("Covers 22:00 to 06:00")
            .documentType(DocumentType.CERTIFICATE)
            .url("https://example.invalid/rota.pdf")
            .createdBy("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            .createdDate(now)
            .modifiedBy("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            .modifiedDate(now);
    }

    // --- server-side attribution on the documents themselves --------------------------------------

    /**
     * The finding this closes. These fields were never missing — they were client-supplied, so any
     * caller could {@code POST "modifiedBy": "somebody-else"} and have it stored verbatim. An
     * attribution trail the attributed party controls is worse than none, because it reads as
     * evidence.
     */
    @Test
    void clientSuppliedAttributionIsOverwrittenNotTrusted() {
        DocumentItem forged = newDocumentItem();
        forged.setCreatedBy("somebody-else");
        forged.setModifiedBy("somebody-else");
        forged.setCreatedDate(Instant.EPOCH);
        forged.setModifiedDate(Instant.EPOCH);

        saved = documentItemRepository.save(forged);

        assertThat(saved.getCreatedBy()).isEqualTo(Constants.SYSTEM);
        assertThat(saved.getModifiedBy()).isEqualTo(Constants.SYSTEM);
        assertThat(saved.getCreatedDate()).isAfter(Instant.EPOCH);
        assertThat(saved.getModifiedDate()).isAfter(Instant.EPOCH);
    }

    /**
     * Merely ignoring the payload on update is not enough: {@code PUT} sends a whole document, so a
     * caller supplying a different {@code createdBy} would have it written straight through. The
     * stored value is re-read and restored instead.
     */
    @Test
    void updatingCannotRewriteProvenance() {
        saved = documentItemRepository.save(newDocumentItem());
        Instant originalCreated = saved.getCreatedDate();

        saved.setDescription("Covers 22:00 to 07:00");
        saved.setCreatedBy("somebody-else");
        saved.setCreatedDate(Instant.EPOCH);
        saved = documentItemRepository.save(saved);

        assertThat(saved.getCreatedBy()).isEqualTo(Constants.SYSTEM);
        assertThat(saved.getCreatedDate()).isEqualTo(originalCreated);
        assertThat(saved.getModifiedBy()).isEqualTo(Constants.SYSTEM);
    }

    /**
     * The case the whole arrangement exists for: a token minted by the gateway carries the account's
     * database id in {@code uid}, and that — not the login in {@code sub} — is what gets stamped.
     *
     * <p>The context is set by hand rather than with {@code @WithMockUser}, which produces a
     * {@code UserDetails} principal and no claims at all. The id used here is the admin account the
     * api's own seed data references as {@code createdBy}, so this asserts against the real contract
     * rather than an invented value.
     */
    @Test
    void theUserIdClaimIsWhatGetsStamped() {
        String adminId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS512").subject("admin").claim(SecurityUtils.USER_ID_KEY, adminId).build();

        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
            SecurityContextHolder.setContext(context);

            saved = documentItemRepository.save(newDocumentItem());

            // The id, not "admin" — a login here would break every seeded reference.
            assertThat(saved.getCreatedBy()).isEqualTo(adminId);
            assertThat(saved.getModifiedBy()).isEqualTo(adminId);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /**
     * With no {@code uid} claim the auditor is {@code system}, never a guess and never the login —
     * writing a login into a field holding ids is the mistake this whole arrangement exists to
     * avoid. Seed loading and service-to-service calls both land here.
     */
    @Test
    void withoutAUserIdClaimTheAuditorIsSystem() {
        assertThat(SecurityUtils.getCurrentUserId()).isEmpty();

        saved = documentItemRepository.save(newDocumentItem());

        assertThat(saved.getCreatedBy()).isEqualTo(Constants.SYSTEM);
    }

    // --- the audit log itself ---------------------------------------------------------------------

    @Test
    void savingADocumentWritesAnAuditLogEntry() {
        saved = documentItemRepository.save(newDocumentItem());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getActionType()).isEqualTo("SAVE");
        assertThat(entries.getFirst().getUserId()).isEqualTo("alice");
        // The collection name, as AuditLogCallback records it — "document_item", not the class name.
        assertThat(entries.getFirst().getMetadata()).contains("document_item").contains(saved.getId());
    }

    @Test
    void deletingADocumentWritesAnAuditLogEntry() {
        DocumentItem item = documentItemRepository.save(newDocumentItem());
        auditLogRepository.deleteAll();

        documentItemRepository.deleteById(item.getId());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getActionType()).isEqualTo("DELETE");
        assertThat(entries.getFirst().getUserId()).isEqualTo("alice");
    }

    /**
     * The recursion guard. Without it the first write of any kind loops forever: saving an AuditLog
     * fires the listener, which saves another AuditLog.
     */
    @Test
    void auditLogWritesDoNotAuditThemselves() {
        AuditLog manual = new AuditLog();
        manual.setActionType("MANUAL");
        manual.setUserId("alice");
        manual.setMetadata("hand-written entry");
        manual.setCreatedDate(Instant.now());
        manual.setModifiedDate(Instant.now());
        manual.setCreatedBy("alice");
        manual.setModifiedBy("alice");

        auditLogRepository.save(manual);

        assertThat(auditLogRepository.findAll()).hasSize(1);
    }

    /**
     * The metadata is a reference, not a copy. Domain documents carry names, addresses and patient
     * subscription detail, and the dashboard renders the audit log to every operator — so recording
     * the whole document would spread that data rather than account for it.
     */
    @Test
    void auditMetadataRecordsTheReferenceNotTheContents() {
        saved = documentItemRepository.save(newDocumentItem());

        String metadata = auditLogRepository.findAll().getFirst().getMetadata();
        assertThat(metadata).doesNotContain("Night shift").doesNotContain("Covers 22:00");
    }
}
