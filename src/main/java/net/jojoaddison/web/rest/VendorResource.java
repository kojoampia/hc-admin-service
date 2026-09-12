package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.repository.VendorRepository;
import net.jojoaddison.repository.support.NamedFilters;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.VendorSummaryService;
import net.jojoaddison.service.dto.VendorSummaryDTO;
import net.jojoaddison.web.rest.errors.AmbiguousAccountException;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Vendor}.
 */
@RestController
@RequestMapping("/api/vendors")
public class VendorResource {

    private static final Logger LOG = LoggerFactory.getLogger(VendorResource.class);

    private static final String ENTITY_NAME = "directoryVendor";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final VendorRepository vendorRepository;

    /** For the named filters above, which need more than one optional predicate combined. */
    private final MongoTemplate mongoTemplate;

    private final VendorSummaryService vendorSummaryService;

    public VendorResource(VendorRepository vendorRepository, MongoTemplate mongoTemplate, VendorSummaryService vendorSummaryService) {
        this.vendorRepository = vendorRepository;
        this.mongoTemplate = mongoTemplate;
        this.vendorSummaryService = vendorSummaryService;
    }

    /**
     * {@code POST  /vendors} : Create a new vendor.
     *
     * @param vendor the vendor to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new vendor, or with status {@code 400 (Bad Request)} if the vendor has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Vendor> createVendor(@Valid @RequestBody Vendor vendor) throws URISyntaxException {
        LOG.debug("REST request to save Vendor : {}", vendor);
        if (vendor.getId() != null) {
            throw new BadRequestAlertException("A new vendor cannot already have an ID", ENTITY_NAME, "idexists");
        }
        normaliseAccountId(vendor);
        rejectDuplicateAccountId(vendor.getAccountId(), null);
        vendor = vendorRepository.save(vendor);
        return ResponseEntity.created(new URI("/api/vendors/" + vendor.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, vendor.getId()))
            .body(vendor);
    }

    /**
     * {@code PUT  /vendors/:id} : Updates an existing vendor.
     *
     * @param id the id of the vendor to save.
     * @param vendor the vendor to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated vendor,
     * or with status {@code 400 (Bad Request)} if the vendor is not valid,
     * or with status {@code 500 (Internal Server Error)} if the vendor couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Vendor> updateVendor(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody Vendor vendor
    ) throws URISyntaxException {
        LOG.debug("REST request to update Vendor : {}, {}", id, vendor);
        if (vendor.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, vendor.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!vendorRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        normaliseAccountId(vendor);
        rejectDuplicateAccountId(vendor.getAccountId(), id);
        vendor = vendorRepository.save(vendor);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, vendor.getId()))
            .body(vendor);
    }

    /**
     * {@code PATCH  /vendors/:id} : Partial updates given fields of an existing vendor, field will ignore if it is null
     *
     * @param id the id of the vendor to save.
     * @param vendor the vendor to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated vendor,
     * or with status {@code 400 (Bad Request)} if the vendor is not valid,
     * or with status {@code 404 (Not Found)} if the vendor is not found,
     * or with status {@code 500 (Internal Server Error)} if the vendor couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Vendor> partialUpdateVendor(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody Vendor vendor
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Vendor partially : {}, {}", id, vendor);
        if (vendor.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, vendor.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!vendorRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        normaliseAccountId(vendor);
        rejectDuplicateAccountId(vendor.getAccountId(), id);

        Optional<Vendor> result = vendorRepository
            .findById(vendor.getId())
            .map(existingVendor -> {
                updateIfPresent(existingVendor::setName, vendor.getName());
                updateIfPresent(existingVendor::setCategory, vendor.getCategory());
                updateIfPresent(existingVendor::setServiceSummary, vendor.getServiceSummary());
                updateIfPresent(existingVendor::setContactName, vendor.getContactName());
                updateIfPresent(existingVendor::setPhone, vendor.getPhone());
                updateIfPresent(existingVendor::setEmail, vendor.getEmail());
                updateIfPresent(existingVendor::setCity, vendor.getCity());
                updateIfPresent(existingVendor::setStatus, vendor.getStatus());
                updateIfPresent(existingVendor::setContractNote, vendor.getContractNote());
                updateIfPresent(existingVendor::setContractRenewsOn, vendor.getContractRenewsOn());
                updateIfPresent(existingVendor::setOrderCount, vendor.getOrderCount());
                updateIfPresent(existingVendor::setSpendToDate, vendor.getSpendToDate());
                updateIfPresent(existingVendor::setRating, vendor.getRating());
                updateIfPresent(existingVendor::setIsArchived, vendor.getIsArchived());
                updateIfPresent(existingVendor::setAccountId, vendor.getAccountId());

                return existingVendor;
            })
            .map(vendorRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, vendor.getId()));
    }

    /**
     * {@code GET  /vendors} : get all the Vendors — the console's directory, and a supplier's own row.
     *
     * <h2>Two callers, and the second one is scoped from its token</h2>
     *
     * <p>Since backlog item 31 this endpoint is reachable by {@code ROLE_VENDOR} as well as by the
     * console's admin and operator. <b>The authority and the scoping are one decision.</b> The
     * matcher in {@code SecurityConfiguration} admits a supplier to an <em>unfiltered list</em>, and
     * {@code accountId.equals} is a parameter the caller chooses to send — so a grant with no
     * scoping behind it is every vendor reading every vendor's record, by sending nothing at all.
     *
     * <p>So the scope is taken from the token and never from the query string:
     * {@link #scopeToTheCallersOwnAccount} reads the caller's login out of the JWT's subject, which
     * is the same string {@code Vendor.accountId} holds, and a vendor asking about anybody else is
     * <b>refused</b> rather than quietly answered with its own row. A request that was not honoured
     * must not be reported as a success. An administrator and an operator are unscoped, and a
     * principal holding both authorities is unscoped too — a scope has to be a function of the token
     * and not of the order two authorities appear in, which is the answer hc-vendor's own
     * {@code VendorScopeResolver} gives about the same token.
     *
     * <h2>And two rows on one login are refused, not picked between</h2>
     *
     * <p>{@link net.jojoaddison.config.VendorAccountIndexes} makes that unstorable, and — following
     * the policy every index creator in {@code config/} follows — it reports and carries on when it
     * cannot create the index, so this service can be running without it. The index is the
     * prevention; this refusal is the enforcement, and each is the other's backstop. Answering with
     * the first of two matches would be one supplier holding another's record under a 200, which is
     * precisely the {@code AMBIGUOUS} outcome hc-vendor cited as its reason for not asking this
     * service who its callers are.
     *
     * <p><b>The count is taken on the resolved account alone, before this handler's own filters are
     * applied</b> — see {@link #refuseAnAmbiguousAccount}, which carries the argument and the defect
     * it closes. The first version of this sentence was true of the sentence and not of the code: the
     * refusal read the <em>filtered</em> page, so {@code ?status.equals=} or the archived filter
     * separated the duplicates and served them one at a time under a 200, and the console's default
     * list filter did it without anybody trying.
     *
     * @param pageable the pagination information.
     * @param isArchivedEquals when true, return only archived records; when false, only unarchived.
     * @param isArchivedNotEquals the inverse, sent by the console as {@code isArchived.notEquals=true}.
     * @param accountIdEquals the vendor-gateway login to resolve to a directory record.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Vendors in body.
     */
    @GetMapping("")
    public ResponseEntity<List<Vendor>> getAllVendors(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "isArchived.equals", required = false) Boolean isArchivedEquals,
        @RequestParam(name = "isArchived.notEquals", required = false) Boolean isArchivedNotEquals,
        // The directory tiles filter on these and read their counts from X-Total-Count. Undeclared,
        // Spring drops them and every tile reads the collection total.
        @RequestParam(name = "status.equals", required = false) AccountStatus statusEquals,
        // hc-vendor resolves a portal login to a vendor here. Deliberately this filter rather than a
        // /vendors/account/{login} route: a page is an unambiguous answer where a 404 is not. The
        // vendor portal's own spec records the failure it is avoiding — in the sibling subsystems a
        // login that names no record 404s exactly like a service that is down, and it reads to the
        // user as "you have no vendor record". An empty page cannot be confused with either.
        //
        @RequestParam(name = "accountId.equals", required = false) String accountIdEquals
    ) {
        LOG.debug("REST request to get a page of Vendors");
        // Normalised on the way in as well as on the way out: stored values are trimmed and
        // lower-cased, so an exact-match filter on "Kaneshie " would resolve nothing and the portal
        // would tell its user they have no vendor record. Symmetry is what keeps that from happening.
        String accountId = accountIdEquals == null ? null : accountIdEquals.trim().toLowerCase(java.util.Locale.ROOT);

        // NORMALISE FIRST, THEN TEST FOR EMPTY — the order is the fix, and it is the rule
        // scopeToTheCallersOwnAccount's own javadoc states, which this parameter broke one line above
        // the method that states it. String.isBlank() asks about whitespace while String.trim() strips
        // every code point at or below U+0020, and the two disagree on the C0 controls: this guard ran
        // on the RAW value, so `?accountId.equals=%00` was not blank, passed, trimmed to "", was
        // dropped by NamedFilters as a blank criterion, and left the ambiguity refusal below counting
        // the whole directory — a 409 about an account that had resolved nobody, or a 200 carrying the
        // entire directory as though it were a resolution. Found by the review of c637228.
        //
        // Why it is refused at all: NamedFilters drops blank values as well as nulls, so a
        // present-but-blank filter would silently become no filter and return the whole directory — a
        // caller resolving an empty login would be handed the first vendor in the collection. On a
        // filter that decides which vendor a portal caller is, that is worth an error rather than a
        // note in the caller's documentation. Absent is still "no filter"; empty is a mistake.
        if (accountId != null && accountId.isEmpty()) {
            throw new BadRequestAlertException("accountId.equals was sent but names nobody", ENTITY_NAME, "accountidblank");
        }
        // The operators the console and the vendor portal send, and only those. This is not a
        // criteria framework: every other entity here lists unfiltered, and inventing a general
        // query language would be a much larger surface than the screens that need it.
        Boolean archived = resolveArchivedFilter(isArchivedEquals, isArchivedNotEquals);

        // A vendor reads its own row and nothing else, whatever it sent — including nothing.
        accountId = scopeToTheCallersOwnAccount(accountId);
        // Before the page is built, and counting the account alone. Handing this the filtered page is
        // what let a narrowing filter separate two duplicates and serve them one at a time; see the
        // method's javadoc, which is the corrected version of a claim three documents used to make.
        refuseAnAmbiguousAccount(accountId);

        NamedFilters.Builder filters = NamedFilters.builder().equals("status", statusEquals).equals("account_id", accountId);
        // Archived stays `$ne: true` rather than `is(false)`: a document written before the field
        // existed does not carry it, and `is_archived: false` matches none of them.
        if (archived != null) {
            if (archived) {
                filters.equals("is_archived", true);
            } else {
                filters.notEquals("is_archived", true);
            }
        }

        Page<Vendor> page;
        if (filters.isEmpty()) {
            page = vendorRepository.findAll(pageable);
        } else {
            page = NamedFilters.page(mongoTemplate, Vendor.class, filters, pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /vendors/summary} : the four figures above the console's vendor directory.
     *
     * <p>Declared before {@code /{id}} for readability only — the two cannot collide. Spring's
     * {@code PathPattern} matching prefers a literal segment over a variable one regardless of
     * declaration order, so {@code /api/vendors/summary} reaches this handler and never arrives at
     * {@link #getVendor(String)} as a vendor whose id is the word "summary".
     *
     * <p>Read-only, so there is no {@code POST}/{@code PUT} shape to keep, and no new authorisation
     * rule: the blanket {@code GET /api/**} rule in {@code SecurityConfiguration} is admin-or-
     * operator, which is right for a directory operators are expected to work in.
     *
     * <p>It is also outside {@code PaginationIT}'s sweep by construction — that matches
     * {@code /api/[a-z0-9-]+}, a single segment, so a computed sub-path is not mistaken for a list
     * endpoint that has forgotten to paginate.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the summary in body.
     */
    @GetMapping("/summary")
    public ResponseEntity<VendorSummaryDTO> getVendorSummary() {
        LOG.debug("REST request to get the vendor directory summary");
        return ResponseEntity.ok(vendorSummaryService.summary());
    }

    /**
     * {@code GET  /vendors/:id} : get the "id" vendor — the console's record screen, and a supplier's
     * own row.
     *
     * <h2>Load and compare, because there is nothing here to filter</h2>
     *
     * <p>Backlog item 88, decided 2026-09-12. {@code ROLE_VENDOR} reaches this path as well as the
     * listing since that item, and <b>the scoping could not be copied from
     * {@link #getAllVendors}</b>: that handler resolves the caller's account and narrows a query by
     * it, where an id already identifies exactly one document and there is no criterion to narrow.
     * So this one loads the row and compares its {@code accountId} to the caller's own, which is the
     * same join — {@code Vendor.accountId} holds a vendor-gateway login and the gateway puts the
     * login in the JWT's {@code sub}. An administrator and an operator are unscoped and read any row,
     * exactly as before this item; so is a principal holding {@code ROLE_VENDOR} alongside one of
     * them, for the reason {@link #scopeToTheCallersOwnAccount} gives.
     *
     * <h2>⚠ The refusal is a 404, and it is the SAME 404</h2>
     *
     * <p>A {@code 403} on an id-addressed read confirms the row exists, so a supplier could walk the
     * collection by status code alone and learn how many suppliers this platform has and which ids
     * are theirs. The refusal therefore has to be indistinguishable from the row being absent — and
     * "indistinguishable" is a property of the response, not of the status line, so a hand-rolled
     * {@code 404} differing in body, headers or problem type would be the same disclosure one field
     * along.
     *
     * <p>It is written as a {@code filter} on the {@link Optional} rather than as a branch for
     * exactly that reason: a refused row and a missing row become the same empty {@code Optional}
     * before anything renders, so both leave through {@code ResponseUtil.wrapOrNotFound} and there is
     * no second response to keep in step with the first. {@code VendorScopeIT} asserts the two are
     * byte-identical on one URI.
     *
     * <p>A row linked to no portal login — {@code accountId} null, which most of this directory is —
     * belongs to no supplier, so a supplier is refused it like any other. Reading null as "unclaimed,
     * therefore anybody's" would hand over the bulk of the collection.
     *
     * <p><b>The scope is resolved BEFORE the read</b>, and the line below says why at length: it is
     * the one thing on this path that can answer differently for an id that exists and an id that
     * does not, which is the disclosure this whole section is about.
     *
     * <h2>And deliberately no ambiguity refusal</h2>
     *
     * <p>{@link #refuseAnAmbiguousAccount} is <b>not</b> called here, and its absence is the decision
     * rather than an omission. Two vendors sharing a login is a question about resolving an account
     * <em>to</em> a record; an id arrives having resolved one already, and answers the same single
     * document whatever {@code accountId} it holds. A {@code 409} on this path would be a guard with
     * no failure mode, which is worse than no guard at all because it reads as protection.
     *
     * @param id the id of the vendor to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the vendor, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Vendor> getVendor(@PathVariable("id") String id) {
        LOG.debug("REST request to get Vendor : {}", id);
        // RESOLVED BEFORE THE READ, and the order is the fix rather than a style choice. A supplier
        // whose token carries no login is refused with a 403, and while this lived inside the filter
        // below it was never reached for an id that matched no document — so the refusal was 403 for
        // a row that exists and 404 for one that does not, which is a caller learning whether a row
        // exists from a status: the exact disclosure the 404 above is chosen to prevent, reintroduced
        // by lazy evaluation. Deciding the scope first makes it constant for every id, and
        // VendorScopeIT asserts both ids rather than one. Found by watching that case fail.
        String ownAccount = callerIsASupplier() ? theCallersOwnLogin() : null;
        Optional<Vendor> vendor = vendorRepository.findById(id).filter(row -> ownAccount == null || ownAccount.equals(row.getAccountId()));
        return ResponseUtil.wrapOrNotFound(vendor);
    }

    /**
     * {@code DELETE  /vendors/:id} : delete the "id" vendor.
     *
     * @param id the id of the vendor to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVendor(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Vendor : {}", id);
        vendorRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id))
            .build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Trims and lower-cases {@code accountId}, and turns a blank one into null.
     *
     * <p>The field's only job is to equal a vendor-gateway login, and the gateway lower-cases every
     * login it stores. So {@code "Kaneshie "} and {@code "kaneshie"} name the same account, but the
     * resolution filter is an exact match and would resolve only the second — presenting to the
     * vendor as "you have no vendor record", which is the failure this whole mechanism was shaped to
     * avoid. Normalising on write is what keeps the exact match honest.
     *
     * <p>Blank becomes null because the two are different in MongoDB but the same to the filter:
     * {@code NamedFilters} drops blank values, so a stored {@code ""} is a link that can never
     * resolve while looking linked in the document.
     */
    private static void normaliseAccountId(Vendor vendor) {
        String accountId = vendor.getAccountId();
        if (accountId == null) {
            return;
        }
        String trimmed = accountId.trim().toLowerCase(java.util.Locale.ROOT);
        vendor.setAccountId(trimmed.isEmpty() ? null : trimmed);
    }

    /**
     * The account this caller may read, which for a supplier is its own and nobody else's.
     *
     * <h2>From the token, never from the query string</h2>
     *
     * <p>{@code Vendor.accountId} holds a vendor-gateway login and the gateway puts the login in the
     * JWT's {@code sub}, so {@code SecurityUtils.getCurrentUserLogin()} is the join — no claim is
     * invented here, and none could be: a new claim would be a contract change in another product.
     * ({@code uid} is this estate's <em>account id</em> claim and names a user document in a gateway
     * this service cannot read, so it is not the same thing.)
     *
     * <p><b>A vendor naming somebody else is refused, not silently corrected.</b> Substituting the
     * caller's own login would answer a question nobody asked with a 200 on it, and a portal that
     * sent the wrong login would go on looking correct. It is the same argument the blank filter above
     * settles one parameter along.
     *
     * <p><b>A vendor token with no login is refused too, and that is not the same as "no rows".</b>
     * It is a broken credential, and the dangerous reading is not "answer nothing" but "answer with no
     * filter", which is what an empty string does to {@code NamedFilters} — the whole directory, with
     * a 200. {@link #theCallersOwnLogin} is where that is refused, and carries the normalise-before-
     * testing argument; it is shared with the record read, which has the same question to ask.
     *
     * <p>An administrator or an operator is unscoped and keeps the whole directory, including the
     * resolution filter hc-vendor's reconciliation report calls. So is a principal holding
     * {@code ROLE_VENDOR} <em>and</em> one of those — see {@link #callerIsASupplier}, which is the one
     * definition both scoped reads use.
     *
     * @param requestedAccountId the normalised {@code accountId.equals} the caller sent, or null.
     * @return the account to filter on: the caller's own when it is a supplier, otherwise whatever
     *         was asked for.
     * @throws AccessDeniedException when a supplier's token carries no login, or names another one.
     */
    private static String scopeToTheCallersOwnAccount(String requestedAccountId) {
        if (!callerIsASupplier()) {
            return requestedAccountId;
        }
        String login = theCallersOwnLogin();
        if (requestedAccountId != null && !requestedAccountId.equals(login)) {
            LOG.warn("A vendor asked for a directory record that is not its own; refusing");
            throw new AccessDeniedException("A vendor may only read its own directory record");
        }
        return login;
    }

    /**
     * Whether the caller is a supplier, and therefore scoped to its own record.
     *
     * <p>One definition for both of this controller's scoped reads. A principal holding
     * {@code ROLE_VENDOR} <em>and</em> {@code ROLE_ADMIN} or {@code ROLE_OPERATOR} is not a supplier
     * here — decided rather than inherited, so one token cannot mean two things depending on which
     * authority is read first, and so the listing and the record cannot answer differently about it.
     */
    private static boolean callerIsASupplier() {
        return (
            SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.VENDOR) &&
            SecurityUtils.hasCurrentUserNoneOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR)
        );
    }

    /**
     * The supplier's own login, normalised the way {@code accountId} is stored, or a refusal.
     *
     * <p><b>A vendor token with no login is refused, and that is not the same as "no rows".</b> It is
     * a broken credential — the dangerous reading is not "answer nothing" but "answer without a
     * scope", which on the listing is the whole directory under a 200. Normalise first and test
     * afterwards: {@link String#isBlank()} asks about whitespace while {@link String#trim()} strips
     * every code point at or below {@code U+0020}, so a subject of {@code U+0000} passes a guard
     * placed ahead of the trim and arrives as the empty string the check exists to keep out.
     * hc-vendor's resolver was fixed for exactly this and says so at length.
     *
     * <p><b>On the id-addressed read this 403 is not the disclosure item 88 forbids</b>, and the
     * distinction is worth stating because the two look alike. What a status may not do there is
     * depend on the row: this one is decided entirely by the token, so it is returned identically for
     * an id that exists and an id that does not, and a caller learns nothing about the collection
     * from it. {@code VendorScopeIT} asserts that constancy rather than taking it on trust.
     *
     * @throws AccessDeniedException when a supplier's token carries no login.
     */
    private static String theCallersOwnLogin() {
        String login = SecurityUtils.getCurrentUserLogin()
            .map(subject -> subject.trim().toLowerCase(java.util.Locale.ROOT))
            .filter(subject -> !subject.isEmpty())
            .orElse(null);
        if (login == null) {
            // No login in the message: it identifies nobody, and the rows it could be about are
            // somebody's. See LoginAttempt's javadoc for why an identifier is not put in a log here
            // even when there is one.
            LOG.warn("A vendor token carries no login; refusing rather than answering about somebody's records");
            throw new AccessDeniedException("A vendor token must carry a login for its own record to be identified");
        }
        return login;
    }

    /**
     * Refuses to answer when more than one vendor carries the account that was resolved.
     *
     * <p>The enforcement half of {@link net.jojoaddison.config.VendorAccountIndexes}, whose javadoc
     * carries the argument: that index is the prevention, it reports and continues rather than
     * failing startup when the data already holds a duplicate, and a service running without the
     * index it believes it has needs the read itself to refuse. Returning the first of two would be
     * one supplier reading another's record under a 200 — the {@code AMBIGUOUS} answer hc-vendor
     * named as its reason for resolving callers locally rather than asking this service.
     *
     * <h2>⚠ It counts the account criterion ALONE, and the first version did not</h2>
     *
     * <p>This used to be handed the page the handler had already built — {@code account_id}
     * <em>plus</em> {@code status.equals} <em>plus</em> the archived filter — and to return early
     * whenever that page held one row. <b>So any filter that separated the duplicates walked past the
     * refusal</b>, and the review of {@code c637228} demonstrated it: {@code ?isArchived.equals=true}
     * and {@code ?isArchived.notEquals=true} served the two rows one at a time under a 200, and a
     * supplier could enumerate {@code status.equals} over the five {@code AccountStatus} values to
     * walk every row sharing its login. The administrator's half needed no attacker at all — the
     * console's default list filter <em>is</em> {@code isArchived.notEquals=true}, so hc-vendor's
     * reconciliation slipped past it in ordinary use.
     *
     * <p>The javadoc of the day said the refusal fired "whenever resolving a login finds more than one
     * row", which was true of the sentence and not of the code. What had been reasoned about was
     * <em>paging</em> — a count over the whole match rather than over the returned slice, which was
     * correct — and the hole was one clause along, in <em>filtering</em>. A guard that reads a query
     * somebody else composed is only as narrow as that query.
     *
     * <p>So it now runs its own count, on {@code account_id} alone, before the page is built: the same
     * shape {@link #rejectDuplicateAccountId} uses on the three write handlers. One extra query, and
     * only on a request that resolves an account. Being independent of the filters is the point — a
     * filter added to this endpoint later cannot narrow a count it is not part of.
     *
     * <p>Nothing is refused when no account was resolved, and an empty one counts as none: an
     * unfiltered directory listing is allowed to contain a duplicate and show it, which is how an
     * administrator finds the rows the message tells them to fix. (The empty case is belt and braces —
     * {@code getAllVendors} rejects a blank filter before reaching here — and it is written down
     * because the version that lacked it counted the <em>whole collection</em> and answered 409 to a
     * caller who had resolved nobody.)
     */
    private void refuseAnAmbiguousAccount(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            return;
        }
        long holders = mongoTemplate.count(Query.query(Criteria.where("account_id").is(accountId)), Vendor.class);
        if (holders <= 1) {
            return;
        }
        LOG.error(
            "{} vendors share one portal login, so the account resolves to no single record. The unique index " +
                "vendor_account_id is missing or was created before the duplicates existed; clear account_id from " +
                "every row but the one that really holds the login.",
            holders
        );
        throw new AmbiguousAccountException(
            "That account is held by more than one vendor, so it does not identify a record",
            ENTITY_NAME,
            "accountidambiguous"
        );
    }

    /**
     * Refuses to give one login to two vendors.
     *
     * <p>{@code accountId} decides which vendor a portal caller is, so a duplicate does not read as
     * a data-quality problem — it shows one vendor another vendor's purchase orders and invoices. An
     * admin pasting the wrong login into the console is the realistic way one appears, so the check
     * belongs here, on the three handlers that write.
     *
     * <p><b>Since backlog item 31 the database prevents it as well</b> —
     * {@link net.jojoaddison.config.VendorAccountIndexes} creates a unique sparse index on
     * {@code account_id}, where this javadoc used to say that nothing in MongoDB did. This check is
     * not made redundant by it and is not a substitute for it: this one answers a {@code 400} naming
     * the field, where the index answers a {@code DuplicateKeyException} that reaches the console as
     * a 500 carrying a Mongo error string; the index catches everything that does not come through
     * these three handlers. {@code ServicePlanResource} carries the same pairing for the same reason.
     *
     * @param excludedId the vendor being updated, whose own value must not count as a collision;
     *                   null when creating.
     */
    private void rejectDuplicateAccountId(String accountId, String excludedId) {
        if (accountId == null) {
            return;
        }
        Query query = Query.query(Criteria.where("account_id").is(accountId));
        if (excludedId != null) {
            query.addCriteria(Criteria.where("_id").ne(excludedId));
        }
        if (mongoTemplate.exists(query, Vendor.class)) {
            throw new BadRequestAlertException("That accountId already belongs to another vendor", ENTITY_NAME, "accountidexists");
        }
    }

    /**
     * Collapses the two operators into a single "want archived?" answer, or null for no filter.
     *
     * <p>{@code equals} wins if both are sent. They can only disagree by a caller's mistake, and
     * answering the positive form is less surprising than picking one silently or erroring.
     */
    private static Boolean resolveArchivedFilter(Boolean equals, Boolean notEquals) {
        if (equals != null) {
            return equals;
        }
        if (notEquals != null) {
            return !notEquals;
        }
        return null;
    }
}
