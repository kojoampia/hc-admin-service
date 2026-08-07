package net.jojoaddison.domain;

import java.util.UUID;

public class AuditEntryTestSamples {

    public static AuditEntry getAuditEntrySample1() {
        return new AuditEntry().id("id1").actor("actor1").action("action1").target("target1");
    }

    public static AuditEntry getAuditEntrySample2() {
        return new AuditEntry().id("id2").actor("actor2").action("action2").target("target2");
    }

    public static AuditEntry getAuditEntryRandomSampleGenerator() {
        return new AuditEntry()
            .id(UUID.randomUUID().toString())
            .actor(UUID.randomUUID().toString())
            .action(UUID.randomUUID().toString())
            .target(UUID.randomUUID().toString());
    }
}
