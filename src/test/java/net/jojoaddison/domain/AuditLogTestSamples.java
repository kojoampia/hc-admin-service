package net.jojoaddison.domain;

import java.util.UUID;

public class AuditLogTestSamples {

    public static AuditLog getAuditLogSample1() {
        return new AuditLog()
            .id("id1")
            .actionType("actionType1")
            .userId("userId1")
            .metadata("metadata1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static AuditLog getAuditLogSample2() {
        return new AuditLog()
            .id("id2")
            .actionType("actionType2")
            .userId("userId2")
            .metadata("metadata2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static AuditLog getAuditLogRandomSampleGenerator() {
        return new AuditLog()
            .id(UUID.randomUUID().toString())
            .actionType(UUID.randomUUID().toString())
            .userId(UUID.randomUUID().toString())
            .metadata(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
