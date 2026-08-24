package net.jojoaddison.web.rest;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.AuditLog}.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogResource.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogResource(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * <b>There is no POST, PUT, PATCH or DELETE here, and their absence is the point.</b>
     *
     * <p>{@code AuditLogCallback} writes a row for every save and delete across every collection.
     * Before it existed this was a full CRUD entity that nothing ever wrote to — so the only rows in
     * the collection were the ones a caller had put there by hand, and the write verbs were the only
     * way anything got in at all.
     *
     * <p>Now that the rows are written server-side, those verbs are worse than unused: an audit
     * trail whose subjects can append to it, edit it and delete from it is not evidence of anything.
     * The blanket {@code /api/**} rule gated them to {@code ROLE_ADMIN}, which is exactly the
     * principal whose actions this collection exists to record.
     *
     * <p>Removed 2026-08-24, closing {@code docs/AUDIT-TODO.md} §2.1's last open box.
     * {@code AuditLogResourceIT} asserts the verbs answer 405 — from the running application, not by
     * reading this comment, because a generated resource grows them back without anyone deciding to
     * add them.
     */

    /**
     * {@code GET  /audit-logs} : get a page of auditLogs.
     *
     * <p>Paginated, unlike most list endpoints in this service, because this is the one collection
     * that grows without bound on its own: {@code AuditLogCallback} appends a row for every save and
     * delete across every other collection. An unbounded {@code findAll()} here would return the
     * entire history of the system in one response, and would grow slower every day it ran.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of auditLogs in body.
     */
    @GetMapping("")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of AuditLogs");
        Page<AuditLog> page = auditLogRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
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
}
