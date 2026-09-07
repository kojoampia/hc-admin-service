package net.jojoaddison.web.rest;

import java.util.List;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.support.NamedFilters;
import net.jojoaddison.service.DirectoryProjectionService;
import net.jojoaddison.service.dto.DirectoryReconciliationDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
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
 *
 * <h2>This endpoint may return the correlation key, and {@code LogPseudonym} exists because a log
 * may not — the two decisions are consistent, not contradictory</h2>
 *
 * <p>A patient's {@code externalKey} and {@code email} are the same value: their address. Backlog
 * item 43 established, by reading the line back out of Loki, that writing it to a <b>log</b> made it
 * an unauthenticated, estate-wide, fourteen-day index of who had registered, shared with five other
 * products — and the answer there is a flat no, enforced by {@link net.jojoaddison.service.LogPseudonym}
 * and two guards. Serving it from <b>this</b> endpoint is a different question with a different
 * answer, and the difference is not a matter of degree:
 *
 * <ul>
 *   <li><b>The reader is named and the read is authorised.</b> This is behind
 *       {@code SecurityConfiguration}'s read/write split — {@code ROLE_ADMIN} or
 *       {@code ROLE_OPERATOR}, on a token this gateway issued. A Loki stream has no such gate:
 *       {@code multitenancy_enabled: false} and unauthenticated on the {@code monitoring} network,
 *       so every product on it reads every other product's lines.</li>
 *   <li><b>Retention is the collection, not a copy of it.</b> A log line is a duplicate of the
 *       address into a store with its own lifetime, which is what made deletion a question nobody
 *       owned. Here the address is already the document, under the retention decision that governs
 *       {@code directory_link} itself (backlog item 28).</li>
 *   <li><b>It is the purpose of the screen rather than a side effect.</b> An administrator's patient
 *       directory is precisely where a patient's contact address belongs — the console already shows
 *       a phone number, an ID document number and a date of birth on the same row's record. A log
 *       line, by contrast, gained nothing from the address that a digest does not also give.</li>
 * </ul>
 *
 * <p>So: <b>identifying, to a reader entitled to it, on purpose. Never into a log, at any level.</b>
 * If that ever stops being true here, it is this endpoint's authorities that change — do not reach
 * for {@code LogPseudonym} to make a screen safer, because a digest on a screen tells an operator
 * nothing they can act on and would put the ObjectId problem back one field along.
 */
@RestController
@RequestMapping("/api/directory-links")
public class DirectoryLinkResource {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryLinkResource.class);

    private static final String ENTITY_NAME = "directoryLink";

    /**
     * The most local ids one {@code localId.in} request may name.
     *
     * <p>Deliberately generous against the caller that exists — the console resolves the rows of one
     * page, so at most {@code size}, which is 20 by default and 100 at the largest the pager offers —
     * and deliberately bounded anyway. Without a ceiling this parameter is a second way to ask for the
     * whole collection in one round trip, which is the shape pagination was added to remove; the
     * request would also be an unindexed {@code $in} over an arbitrarily long array. Refused loudly
     * rather than truncated: a silently shortened filter returns fewer links than rows and the console
     * would render some of them as unresolved, which is this item's own defect wearing a different
     * cause.
     */
    static final int MAX_LOCAL_IDS = 200;

    private final DirectoryLinkRepository directoryLinkRepository;
    private final DirectoryProjectionService directoryProjectionService;

    /** For combining the two optional filters below, which is more than a derived query method can do. */
    private final MongoTemplate mongoTemplate;

    public DirectoryLinkResource(
        DirectoryLinkRepository directoryLinkRepository,
        DirectoryProjectionService directoryProjectionService,
        MongoTemplate mongoTemplate
    ) {
        this.directoryLinkRepository = directoryLinkRepository;
        this.directoryProjectionService = directoryProjectionService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * {@code GET /api/directory-links} : the accounts this service has learned about from the broker.
     *
     * <h2>{@code localId.in}, and why the console needs it</h2>
     *
     * <p>A patient learned from a sibling event has <b>no {@code Profile}</b> and cannot be given one:
     * the streams carry no name, date of birth, phone number or document number, and hc-patient's
     * {@code PatientEventPublisher.assertNothingClinical} refuses at runtime to publish any of them.
     * hc-admin's {@code Profile} requires all four. So the row exists, is correct, and has nothing on
     * it a person can be recognised by — the console fell back to the Mongo {@code ObjectId} and drew
     * its first two hex characters as the initials chip, which an operator reported from production as
     * a corrupted record (backlog item 45).
     *
     * <p>The identity that <em>does</em> exist for that patient is on their {@link DirectoryLink}, and
     * this parameter is how a screen reads it <b>once per page rather than once per row</b>. The
     * console sends the ids of the nameless rows only, so a directory of named patients costs no extra
     * request at all and a page of learned ones costs exactly one.
     *
     * <p>Filtering here rather than nesting the link inside {@code GET /api/patients} is deliberate:
     * {@link DirectoryLink} is beside {@code Patient} and not on it, because how hc-admin models a
     * cross-stack patient identity is still open (backlog item 22), and a field on the domain record
     * would pre-empt that decision by looking like the answer.
     *
     * @param source when present, only that stream's links.
     * @param localIdIn when present, only links naming one of these local records.
     * @param pageable the pagination information.
     */
    @GetMapping("")
    public ResponseEntity<List<DirectoryLink>> getAllDirectoryLinks(
        @RequestParam(required = false) DirectorySource source,
        @RequestParam(name = "localId.in", required = false) List<String> localIdIn,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of DirectoryLinks for source {}", source);
        if (localIdIn != null && localIdIn.size() > MAX_LOCAL_IDS) {
            throw new BadRequestAlertException("Too many local ids: at most " + MAX_LOCAL_IDS, ENTITY_NAME, "localidintoolong");
        }
        // Blanks are dropped, and then a filter that was asked for but named nobody answers with
        // nobody. Both halves matter and neither is theoretical: `?localId.in=` binds to a list of
        // one empty string rather than to an empty list, so without the trim this would query
        // `$in: [""]`, and without the early return NamedFilters would drop the emptied collection —
        // its own javadoc warns at the call site that a filter which vanishes is a query returning
        // everything. Asking for the links of no records is not asking for all of them.
        List<String> localIds = localIdIn == null ? null : localIdIn.stream().filter(id -> id != null && !id.isBlank()).toList();
        if (localIds != null && localIds.isEmpty()) {
            Page<DirectoryLink> none = Page.empty(pageable);
            return ResponseEntity
                .ok()
                .headers(PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), none))
                .body(none.getContent());
        }

        // Stored field names, matching PatientResource — `local_id` is what the document carries, and
        // going through the Java property name works only via the @Field indirection.
        NamedFilters.Builder filters = NamedFilters.builder().equals("source", source).in("local_id", localIds);

        Page<DirectoryLink> page = filters.isEmpty()
            ? directoryLinkRepository.findAll(pageable)
            : NamedFilters.page(mongoTemplate, DirectoryLink.class, filters, pageable);
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
