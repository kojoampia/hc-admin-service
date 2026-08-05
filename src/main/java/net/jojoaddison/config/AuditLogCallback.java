package net.jojoaddison.config;

import java.lang.reflect.Method;
import java.time.Instant;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.repository.AuditLogRepository;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

/**
 * Writes an {@link AuditLog} row for every domain mutation.
 *
 * <p>{@code AuditLog} was a full CRUD entity — resource, repository, integration test — that
 * <em>nothing in the application ever wrote to</em>. A grep for {@code auditLogRepository.save}
 * outside its own resource returned nothing. It was a table administrators could hand-edit, not a
 * log, on a service managing healthcare organisations, facilities and patient subscriptions.
 *
 * <p>This closes that. Saves and deletes across every collection produce a row; the entity's own
 * writes are excluded, which is both the recursion guard and the reason {@code AuditLogResource}
 * ought to be read-only.
 *
 * <p>Note this is an {@link AbstractMongoEventListener} rather than an entity callback: Spring Data
 * MongoDB has {@code BeforeSaveCallback} and {@code AfterSaveCallback} but no delete callback, so
 * the two halves of "what changed" are only available together through the event API. A delete
 * therefore reports the query it matched rather than the document it removed — after the fact,
 * there is nothing else left to report.
 */
@Component
public class AuditLogCallback extends AbstractMongoEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogCallback.class);

    private static final String AUDIT_LOG_COLLECTION = "audit_log";

    private final AuditLogRepository auditLogRepository;

    /**
     * Lazy because the repository is itself built on the MongoTemplate that publishes these events —
     * injecting it eagerly closes a cycle at context startup.
     */
    public AuditLogCallback(@Lazy AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Object> event) {
        record(event.getCollectionName(), "SAVE", idOf(event.getSource()));
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Object> event) {
        Object id = event.getDocument() == null ? null : event.getDocument().get("_id");
        record(event.getCollectionName(), "DELETE", id == null ? null : id.toString());
    }

    private void record(String collection, String action, String entityId) {
        // Without this the first write recurses forever: saving an AuditLog fires onAfterSave,
        // which saves another AuditLog.
        if (AUDIT_LOG_COLLECTION.equals(collection)) {
            return;
        }

        try {
            String actor = SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM);
            Instant now = Instant.now();

            AuditLog log = new AuditLog();
            log.setActionType(action);
            log.setUserId(actor);
            log.setMetadata(action + " on " + collection + (entityId == null ? "" : " [" + entityId + "]"));
            log.setCreatedBy(actor);
            log.setCreatedDate(now);
            log.setModifiedBy(actor);
            log.setModifiedDate(now);
            auditLogRepository.save(log);
        } catch (RuntimeException e) {
            // A failed audit write must not fail the business write it describes. It must be loud,
            // though — a silently missing trail is the state this replaced.
            LOG.error("could not write audit log for {} on {}", action, collection, e);
        }
    }

    /**
     * The entity id, not its contents. Domain documents here carry names, addresses and patient
     * subscription detail; copying all of that into a second collection the dashboard renders to
     * every operator would spread the data rather than account for it.
     */
    private String idOf(Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object id = getId.invoke(entity);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
