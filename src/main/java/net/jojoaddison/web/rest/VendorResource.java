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
        // NamedFilters drops blank values as well as nulls, so a present-but-blank
        // `?accountId.equals=` would silently become no filter and return the whole directory — a
        // caller resolving an empty login would be handed the first vendor in the collection. On a
        // filter that decides which vendor a portal caller is, that is worth an error rather than a
        // note in the caller's documentation. Absent is still "no filter"; blank is now a mistake.
        if (accountIdEquals != null && accountIdEquals.isBlank()) {
            throw new BadRequestAlertException("accountId.equals was sent but is blank", ENTITY_NAME, "accountidblank");
        }
        // The operators the console and the vendor portal send, and only those. This is not a
        // criteria framework: every other entity here lists unfiltered, and inventing a general
        // query language would be a much larger surface than the screens that need it.
        Boolean archived = resolveArchivedFilter(isArchivedEquals, isArchivedNotEquals);

        // Normalised on the way in as well as on the way out: stored values are trimmed and
        // lower-cased, so an exact-match filter on "Kaneshie " would resolve nothing and the portal
        // would tell its user they have no vendor record. Symmetry is what keeps that from happening.
        String accountId = accountIdEquals == null ? null : accountIdEquals.trim().toLowerCase(java.util.Locale.ROOT);
        // A vendor reads its own row and nothing else, whatever it sent — including nothing.
        accountId = scopeToTheCallersOwnAccount(accountId);

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
        refuseAnAmbiguousAccount(accountId, page);
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
     * {@code GET  /vendors/:id} : get the "id" vendor.
     *
     * @param id the id of the vendor to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the vendor, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Vendor> getVendor(@PathVariable("id") String id) {
        LOG.debug("REST request to get Vendor : {}", id);
        Optional<Vendor> vendor = vendorRepository.findById(id);
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
     * It is a broken credential. The dangerous reading is not "answer nothing" but "answer with no
     * filter", which is what an empty string does to {@code NamedFilters} — the whole directory, with
     * a 200. Normalise first and test afterwards: {@link String#isBlank()} asks about whitespace while
     * {@link String#trim()} strips every code point at or below {@code U+0020}, so a subject of
     * {@code U+0000} passes a guard placed ahead of the trim and arrives as the empty string the check
     * exists to keep out. hc-vendor's resolver was fixed for exactly this and says so at length.
     *
     * <p>An administrator or an operator is unscoped and keeps the whole directory, including the
     * resolution filter hc-vendor's reconciliation report calls. A principal holding
     * {@code ROLE_VENDOR} <em>and</em> one of those is unscoped as well — decided rather than
     * inherited, so that one token cannot mean two things depending on which authority is read first.
     *
     * @param requestedAccountId the normalised {@code accountId.equals} the caller sent, or null.
     * @return the account to filter on: the caller's own when it is a supplier, otherwise whatever
     *         was asked for.
     * @throws AccessDeniedException when a supplier's token carries no login, or names another one.
     */
    private static String scopeToTheCallersOwnAccount(String requestedAccountId) {
        boolean isSupplier =
            SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.VENDOR) &&
            SecurityUtils.hasCurrentUserNoneOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR);
        if (!isSupplier) {
            return requestedAccountId;
        }
        String login = SecurityUtils.getCurrentUserLogin()
            .map(subject -> subject.trim().toLowerCase(java.util.Locale.ROOT))
            .filter(subject -> !subject.isEmpty())
            .orElse(null);
        if (login == null) {
            // No login in the message: it identifies nobody, and the rows it could be about are
            // somebody's. See LoginAttempt's javadoc for why an identifier is not put in a log here
            // even when there is one.
            LOG.warn("A vendor token carries no login; refusing rather than scoping the directory to nothing");
            throw new AccessDeniedException("A vendor token must carry a login for its own record to be identified");
        }
        if (requestedAccountId != null && !requestedAccountId.equals(login)) {
            LOG.warn("A vendor asked for a directory record that is not its own; refusing");
            throw new AccessDeniedException("A vendor may only read its own directory record");
        }
        return login;
    }

    /**
     * Refuses to answer when two vendors carry the account that was resolved.
     *
     * <p>The enforcement half of {@link net.jojoaddison.config.VendorAccountIndexes}, whose javadoc
     * carries the argument: that index is the prevention, it reports and continues rather than
     * failing startup when the data already holds a duplicate, and a service running without the
     * index it believes it has needs the read itself to refuse. Returning the first of two would be
     * one supplier reading another's record under a 200 — the {@code AMBIGUOUS} answer hc-vendor
     * named as its reason for resolving callers locally rather than asking this service.
     *
     * <p>It reads the page's total rather than counting the content, so a caller asking for one row
     * at a time cannot page past the conflict — the count is over the whole match, which is what
     * {@code NamedFilters.page} computes.
     *
     * <p>Nothing is refused when no account was resolved: an unfiltered directory listing is allowed
     * to contain a duplicate and show it, which is how an administrator finds the rows the message
     * tells them to fix.
     */
    private static void refuseAnAmbiguousAccount(String accountId, Page<Vendor> page) {
        if (accountId == null || page.getTotalElements() <= 1) {
            return;
        }
        LOG.error(
            "{} vendors share one portal login, so the account resolves to no single record. The unique index " +
                "vendor_account_id is missing or was created before the duplicates existed; clear account_id from " +
                "every row but the one that really holds the login.",
            page.getTotalElements()
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
