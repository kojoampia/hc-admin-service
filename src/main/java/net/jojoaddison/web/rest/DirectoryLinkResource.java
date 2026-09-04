package net.jojoaddison.web.rest;

import java.util.List;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.service.DirectoryProjectionService;
import net.jojoaddison.service.dto.DirectoryReconciliationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * What this directory has learned from the sibling stacks, and the button that re-derives it.
 *
 * <h2>Read-only, and that is the whole surface</h2>
 *
 * <p>There is no create, no update and no delete. A link is a fact about another system's account:
 * editing one here would make this service's copy disagree with the stream that produced it, and the
 * next event would silently put it back. The only write is the reconciliation, which recomputes and
 * invents nothing.
 *
 * <p>Authorities come from the blanket read/write split in {@code SecurityConfiguration} and are not
 * restated here — {@code GET} reaches {@code ROLE_ADMIN} and {@code ROLE_OPERATOR}, the
 * {@code POST} reaches {@code ROLE_ADMIN} alone, which is the right shape for an operation that
 * writes to the patient directory.
 *
 * <p>The list is paginated like every other {@code GET /api/<collection>} in this service, and
 * {@code PaginationIT} sweeps it automatically because it derives its paths from the handler
 * mapping. It will grow at the rate accounts are created on two other stacks, which is exactly the
 * kind of collection the unpaginated shape was removed from.
 */
@RestController
@RequestMapping("/api/directory-links")
public class DirectoryLinkResource {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryLinkResource.class);

    private final DirectoryLinkRepository directoryLinkRepository;
    private final DirectoryProjectionService directoryProjectionService;

    public DirectoryLinkResource(DirectoryLinkRepository directoryLinkRepository, DirectoryProjectionService directoryProjectionService) {
        this.directoryLinkRepository = directoryLinkRepository;
        this.directoryProjectionService = directoryProjectionService;
    }

    /**
     * {@code GET /api/directory-links} : the accounts this service has learned about from the broker.
     *
     * @param source when present, only that stream's links.
     * @param pageable the pagination information.
     */
    @GetMapping("")
    public ResponseEntity<List<DirectoryLink>> getAllDirectoryLinks(
        @RequestParam(required = false) DirectorySource source,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of DirectoryLinks for source {}", source);
        Page<DirectoryLink> page = source == null
            ? directoryLinkRepository.findAll(pageable)
            : directoryLinkRepository.findBySource(source, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code POST /api/directory-links/reconcile} : re-derive every local record a link says exists.
     *
     * <p>Safe to run at any time and safe to run twice — it is the same idempotent write path a live
     * message takes. See {@code DirectoryProjectionService.reconcile()} for what it does and does not
     * reach.
     */
    @PostMapping("/reconcile")
    public ResponseEntity<DirectoryReconciliationDTO> reconcile() {
        LOG.debug("REST request to reconcile the directory against its links");
        return ResponseEntity.ok(directoryProjectionService.reconcile());
    }
}
