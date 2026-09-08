package net.jojoaddison.web.rest;

import jakarta.servlet.http.HttpServletRequest;
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
     * <h2>{@code unlinked}, and why the clinician directory needs it</h2>
     *
     * <p>A patient learned from an event is a row with no name; <b>a clinician learned from one is no
     * row at all.</b> Both types on {@code hc.professional.registration} are {@code LINK_ONLY} — a
     * {@code Professional} requires a {@code role} and a {@code licenceNumber}, neither of which is on
     * that topic in any event, in any version (backlog items 33 and 46) — so the registration produces
     * a link with {@code local_id: null} and nothing else. {@code GET /api/professionals} therefore
     * cannot show them however it is filtered, and the console asked no other question, so a clinician
     * who registered on production reached this service, was stored, and was invisible.
     *
     * <p>{@code unlinked=true} is that question: the accounts this service knows about and holds no
     * record for. The professional directory lists them under a heading that says exactly that, and
     * the dashboard counts them in {@code professionalsAwaitingRecord} — <b>the same rule, read twice,
     * so the two are asserted against each other</b> in {@code DashboardMetricsResourceIT}.
     *
     * <p>{@code unlinked=false} is its complement rather than a convenience, and the pair is why this
     * is a tri-state {@code Boolean} and not a flag: absent means "do not ask", which is what every
     * other caller of this endpoint wants.
     *
     * <h2>A blank {@code source} or {@code unlinked} is refused, not read as absent</h2>
     *
     * <p>Both are typed parameters, and Spring's converters answer {@code null} for the empty string
     * — measured on this classpath, {@code DefaultConversionService.convert("", Boolean.class)} and
     * the same call for an enum both return {@code null}, with no exception. So
     * {@code ?source=HC_PROFESSIONAL&unlinked=} bound {@code unlinked} to null, {@code isNull} added
     * no criterion, and the request answered with <b>every</b> link of that source. That is item 45's
     * blank-parameter finding one parameter along: a filter that vanishes is a query that returns
     * everything.
     *
     * <p><b>It was not a live defect and it is still worth an error.</b> The console always sends
     * {@code true}, and every {@code HC_PROFESSIONAL} link is unlinked today, so the two answers
     * coincide exactly. They stop coinciding the day backlog item 35 fills clinician records in, and
     * the wrong answer then is the panel listing clinicians who <em>do</em> have a record under a
     * heading reading "Registered, no record here yet" — the fabrication this whole item exists to
     * refuse, arrived at by a query that returned more than it was asked for.
     *
     * <p><b>Refused rather than defaulted, and that is the difference from {@code localId.in}.</b>
     * An empty {@code localId.in} has a safe reading — the links of no records are nobody — so it
     * answers with an empty page. A blank {@code unlinked} has none: there is no third value of the
     * tri-state meaning "match nothing", and picking either {@code true} or {@code false} would
     * invent an answer the caller did not ask for. Same for a blank {@code source}. 400 is the only
     * honest response, and it follows {@code VendorResource}'s {@code accountId.equals}.
     *
     * <p><b>The related trap this cannot close: a rolling deploy in the wrong order.</b> A console
     * that sends {@code unlinked=true} to an api built before this parameter existed gets every
     * {@code HC_PROFESSIONAL} link, because Spring drops an <em>undeclared</em> request parameter
     * silently — the failure the class javadoc opens with, and one no check on either side can see.
     * Deploy the api before the console.
     *
     * <h2>{@code planStatus}, and why a plan choice needs a filter of its own</h2>
     *
     * <p>Backlog item 48: hc-patient publishes {@code PlanChosen} when a patient picks a membership
     * tier, and the four fields it carries land on the link. A pending choice is <b>an item awaiting
     * action</b> — the whole reason that event exists is to prompt the back office — and an
     * administrator has to be able to see that there is anything to act on <em>without</em> paging the
     * directory. That question cannot be asked of {@code GET /api/patients}: the plan choice is on
     * {@code directory_link}, beside {@code Patient} rather than on it (backlog item 22), so nothing
     * in the patient collection can be filtered or sorted by it. It is asked here.
     *
     * <p><b>An exact, case-sensitive match on hc-patient's own vocabulary</b>, which is what they
     * publish: {@code MembershipStatus.name()}, one of
     * {@code PENDING, ACTIVE, CANCELLED, EXPIRED, SUSPENDED}. A {@code String} and not an enum here
     * for the reason {@code DirectoryLink.planStatus} gives — the set is theirs to extend, and a value
     * this service could not name is a value it should still be able to store, show and filter on
     * rather than refuse.
     *
     * <p>Blank is refused like {@code source} and {@code unlinked}, and here the failure it prevents
     * is the same one with the opposite sign. {@code NamedFilters.equals} <em>drops</em> a blank
     * string, so {@code ?planStatus=} would add no criterion and answer with every link in the
     * collection — links with no plan choice at all, under a heading saying these are choices awaiting
     * a decision. Its own javadoc warns of exactly that: "a filter that vanishes is a query that
     * returns everything".
     *
     * @param source when present, only that stream's links.
     * @param localIdIn when present, only links naming one of these local records.
     * @param unlinked when present, only the links that have no local record ({@code true}) or only
     *                 the ones that have ({@code false}).
     * @param planStatus when present, only the links whose stored plan choice was reported in this
     *                   status. Absent means "do not ask", which is what every other caller wants.
     * @param pageable the pagination information.
     */
    @GetMapping("")
    public ResponseEntity<List<DirectoryLink>> getAllDirectoryLinks(
        @RequestParam(required = false) DirectorySource source,
        @RequestParam(name = "localId.in", required = false) List<String> localIdIn,
        @RequestParam(required = false) Boolean unlinked,
        @RequestParam(required = false) String planStatus,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        // Not a parameter of this API: the raw request is the only thing left that can tell a blank
        // typed parameter from an absent one, because the converter has already turned both into
        // null by the time the arguments above are bound. springdoc ignores it.
        HttpServletRequest request
    ) {
        LOG.debug("REST request to get a page of DirectoryLinks for source {}, unlinked {}, planStatus {}", source, unlinked, planStatus);
        rejectBlank(request, "source");
        rejectBlank(request, "unlinked");
        rejectBlank(request, "planStatus");
        if (localIdIn != null && localIdIn.size() > MAX_LOCAL_IDS) {
            throw new BadRequestAlertException("Too many local ids: at most " + MAX_LOCAL_IDS, ENTITY_NAME, "localidintoolong");
        }
        // A filter that was asked for but named nobody answers with nobody. Asking for the links of
        // no records is not asking for all of them — NamedFilters drops an emptied collection, and
        // its own javadoc warns at the call site that a filter which vanishes is a query returning
        // everything.
        //
        // How a blank parameter actually binds was got WRONG here until 2026-09-07, in this comment,
        // in the IT's javadoc and in the commit message: all three said `?localId.in=` produces a
        // list holding one empty string. It does not. Measured against the versions on this
        // classpath, with a probe controller under standalone MockMvc:
        //
        //   ?localId.in=                 -> []          an EMPTY list
        //   ?localId.in=,                -> ["", ""]
        //   ?localId.in=&localId.in=     -> ["", ""]
        //
        // A single value reaches the type converter as a String and StringToCollectionConverter runs
        // it through commaDelimitedListToStringArray, which yields nothing at all for "". Two values
        // reach it as a String[] and every element survives.
        //
        // So the EMPTINESS CHECK is the guard, and it is load-bearing: removing it makes both blank
        // cases in DirectoryLinkResourceIT fail with `X-Total-Count expected:<0> but was:<1>`.
        // The BLANK-STRIP is a normalisation and nothing more — measured, removing it changes no
        // outcome at all, because `NamedFilters.in` passes blank elements straight through (its
        // javadoc says so) and no stored `local_id` is ever the empty string, so `$in: ["", ""]`
        // matches exactly as little as no query does. It is kept so that all three forms above take
        // one code path rather than two that happen to agree, and so that a `local_id: ""` arriving
        // some day cannot turn a blank into a match. Do not describe it as the guard.
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
        NamedFilters.Builder filters = NamedFilters
            .builder()
            .equals("source", source)
            .in("local_id", localIds)
            // `local_id: null` matches a missing field as well as a null one, which is what a link
            // with no record actually looks like — the projection never writes the field until there
            // is a record to name. Same match as DirectoryProjectionService.createAndClaim.
            .isNull("local_id", unlinked)
            // Stored field name again, and hc-patient's own value: `equals` on a String drops a
            // blank, which is why the handler refused one above rather than letting the filter
            // vanish into a query for the whole collection.
            .equals("plan_status", planStatus);

        Page<DirectoryLink> page = filters.isEmpty()
            ? directoryLinkRepository.findAll(pageable)
            : NamedFilters.page(mongoTemplate, DirectoryLink.class, filters, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * Refuses a typed filter that was sent and left blank.
     *
     * <p>Reads the <b>raw</b> value rather than testing the bound argument, and that is the whole
     * point: a {@code Boolean} or an enum {@code @RequestParam} sent as {@code ?unlinked=} has
     * already been converted to {@code null}, so by the time the handler runs, "blank" and "absent"
     * are the same value. Only {@code getParameter} still knows which it was.
     *
     * <p>Deliberately <b>not</b> applied to {@code localId.in}. That one is a collection with a safe
     * empty reading, it is answered with an empty page a few lines below, and two
     * {@code DirectoryLinkResourceIT} cases pin that answer — turning it into a 400 would be a
     * behaviour change nobody asked for on the parameter that is already right.
     */
    private static void rejectBlank(HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        if (raw != null && raw.isBlank()) {
            throw new BadRequestAlertException(name + " was sent but is blank", ENTITY_NAME, "blankfilter");
        }
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
