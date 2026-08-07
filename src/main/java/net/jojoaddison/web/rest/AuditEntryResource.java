package net.jojoaddison.web.rest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.AuditEntry;
import net.jojoaddison.repository.AuditEntryRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.AuditEntry}.
 */
@RestController
@RequestMapping("/api/audit-entries")
public class AuditEntryResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEntryResource.class);

    private final AuditEntryRepository auditEntryRepository;

    public AuditEntryResource(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    /**
     * {@code GET  /audit-entries} : get all the Audit Entries.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Audit Entries in body.
     */
    @GetMapping("")
    public ResponseEntity<List<AuditEntry>> getAllAuditEntries(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of AuditEntries");
        Page<AuditEntry> page = auditEntryRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /audit-entries/:id} : get the "id" auditEntry.
     *
     * @param id the id of the auditEntry to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the auditEntry, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditEntry> getAuditEntry(@PathVariable("id") String id) {
        LOG.debug("REST request to get AuditEntry : {}", id);
        Optional<AuditEntry> auditEntry = auditEntryRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(auditEntry);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
