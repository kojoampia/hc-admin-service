package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.repository.AuditLogRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.AuditLog}.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceAuditLog";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuditLogRepository auditLogRepository;

    public AuditLogResource(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * {@code POST  /audit-logs} : Create a new auditLog.
     *
     * @param auditLog the auditLog to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new auditLog, or with status {@code 400 (Bad Request)} if the auditLog has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<AuditLog> createAuditLog(@Valid @RequestBody AuditLog auditLog) throws URISyntaxException {
        LOG.debug("REST request to save AuditLog : {}", auditLog);
        if (auditLog.getId() != null) {
            throw new BadRequestAlertException("A new auditLog cannot already have an ID", ENTITY_NAME, "idexists");
        }
        auditLog = auditLogRepository.save(auditLog);
        return ResponseEntity
            .created(new URI("/api/audit-logs/" + auditLog.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, auditLog.getId()))
            .body(auditLog);
    }

    /**
     * {@code PUT  /audit-logs/:id} : Updates an existing auditLog.
     *
     * @param id the id of the auditLog to save.
     * @param auditLog the auditLog to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated auditLog,
     * or with status {@code 400 (Bad Request)} if the auditLog is not valid,
     * or with status {@code 500 (Internal Server Error)} if the auditLog couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AuditLog> updateAuditLog(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody AuditLog auditLog
    ) throws URISyntaxException {
        LOG.debug("REST request to update AuditLog : {}, {}", id, auditLog);
        if (auditLog.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, auditLog.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!auditLogRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        auditLog = auditLogRepository.save(auditLog);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, auditLog.getId()))
            .body(auditLog);
    }

    /**
     * {@code PATCH  /audit-logs/:id} : Partial updates given fields of an existing auditLog, field will ignore if it is null
     *
     * @param id the id of the auditLog to save.
     * @param auditLog the auditLog to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated auditLog,
     * or with status {@code 400 (Bad Request)} if the auditLog is not valid,
     * or with status {@code 404 (Not Found)} if the auditLog is not found,
     * or with status {@code 500 (Internal Server Error)} if the auditLog couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<AuditLog> partialUpdateAuditLog(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody AuditLog auditLog
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update AuditLog partially : {}, {}", id, auditLog);
        if (auditLog.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, auditLog.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!auditLogRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<AuditLog> result = auditLogRepository
            .findById(auditLog.getId())
            .map(existingAuditLog -> {
                if (auditLog.getActionType() != null) {
                    existingAuditLog.setActionType(auditLog.getActionType());
                }
                if (auditLog.getUserId() != null) {
                    existingAuditLog.setUserId(auditLog.getUserId());
                }
                if (auditLog.getMetadata() != null) {
                    existingAuditLog.setMetadata(auditLog.getMetadata());
                }
                if (auditLog.getCreatedBy() != null) {
                    existingAuditLog.setCreatedBy(auditLog.getCreatedBy());
                }
                if (auditLog.getCreatedDate() != null) {
                    existingAuditLog.setCreatedDate(auditLog.getCreatedDate());
                }
                if (auditLog.getModifiedBy() != null) {
                    existingAuditLog.setModifiedBy(auditLog.getModifiedBy());
                }
                if (auditLog.getModifiedDate() != null) {
                    existingAuditLog.setModifiedDate(auditLog.getModifiedDate());
                }

                return existingAuditLog;
            })
            .map(auditLogRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, auditLog.getId())
        );
    }

    /**
     * {@code GET  /audit-logs} : get all the auditLogs.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of auditLogs in body.
     */
    @GetMapping("")
    public List<AuditLog> getAllAuditLogs() {
        LOG.debug("REST request to get all AuditLogs");
        return auditLogRepository.findAll();
    }

    /**
     * {@code GET  /audit-logs/:id} : get the "id" auditLog.
     *
     * @param id the id of the auditLog to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the auditLog, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLog> getAuditLog(@PathVariable("id") String id) {
        LOG.debug("REST request to get AuditLog : {}", id);
        Optional<AuditLog> auditLog = auditLogRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(auditLog);
    }

    /**
     * {@code DELETE  /audit-logs/:id} : delete the "id" auditLog.
     *
     * @param id the id of the auditLog to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuditLog(@PathVariable("id") String id) {
        LOG.debug("REST request to delete AuditLog : {}", id);
        auditLogRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
