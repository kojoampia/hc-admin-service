package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.domain.Team;
import net.jojoaddison.repository.AuditLogRepository;
import net.jojoaddison.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * The audit trail, which was configured-but-inert before: {@code AuditLog} was a full CRUD entity
 * that nothing in the application ever wrote to.
 *
 * <p>Team is the subject because it is an ordinary domain document — nothing here is specific to it.
 *
 * <h2>What this deliberately does not cover</h2>
 *
 * <p>The {@code createdBy}/{@code modifiedBy} fields on the domain documents themselves are still
 * client-supplied, and are still not trustworthy attribution. A first attempt stamped them from the
 * security context and had to be backed out: the seed data puts <em>gateway user ids</em> in them
 * ({@code a0eebc99-…-a11} is the admin account), which CLAUDE.md names as a cross-service contract,
 * while the only identity this service has is the JWT subject — a login. Writing logins into a field
 * holding ids would put two identifier spaces in one column and break the seeded references, and
 * this service cannot resolve one to the other: it runs with {@code skipUserManagement: true} and
 * has no route to the gateway's user collection.
 *
 * <p>So attribution lives here, in {@code AuditLog.userId}, where the login is the whole point and
 * no existing contract is displaced.
 */
@IntegrationTest
@WithMockUser(username = "alice")
class AuditingIT {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Team saved;

    @BeforeEach
    void clearAuditLog() {
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        if (saved != null) {
            teamRepository.deleteById(saved.getId());
            saved = null;
        }
        auditLogRepository.deleteAll();
    }

    /**
     * The four audit fields are set here because the domain declares them {@code @NotNull} and the
     * client is what supplies them — which is precisely the limitation documented above, and the
     * reason the trustworthy record is the AuditLog row rather than these.
     */
    private Team newTeam() {
        Instant now = Instant.now();
        return new Team()
            .name("Night shift")
            .description("Covers 22:00 to 06:00")
            .createdBy("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            .createdDate(now)
            .modifiedBy("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            .modifiedDate(now);
    }

    // --- the audit log itself ---------------------------------------------------------------------

    @Test
    void savingADocumentWritesAnAuditLogEntry() {
        saved = teamRepository.save(newTeam());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getActionType()).isEqualTo("SAVE");
        assertThat(entries.getFirst().getUserId()).isEqualTo("alice");
        assertThat(entries.getFirst().getMetadata()).contains("team").contains(saved.getId());
    }

    @Test
    void deletingADocumentWritesAnAuditLogEntry() {
        Team team = teamRepository.save(newTeam());
        auditLogRepository.deleteAll();

        teamRepository.deleteById(team.getId());

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
        saved = teamRepository.save(newTeam());

        String metadata = auditLogRepository.findAll().getFirst().getMetadata();
        assertThat(metadata).doesNotContain("Night shift").doesNotContain("Covers 22:00");
    }
}
