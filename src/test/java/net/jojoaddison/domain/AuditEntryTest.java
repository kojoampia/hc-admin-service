package net.jojoaddison.domain;

import static net.jojoaddison.domain.AuditEntryTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AuditEntry.class);
        AuditEntry auditEntry1 = getAuditEntrySample1();
        AuditEntry auditEntry2 = new AuditEntry();
        assertThat(auditEntry1).isNotEqualTo(auditEntry2);

        auditEntry2.setId(auditEntry1.getId());
        assertThat(auditEntry1).isEqualTo(auditEntry2);

        auditEntry2 = getAuditEntrySample2();
        assertThat(auditEntry1).isNotEqualTo(auditEntry2);
    }
}
