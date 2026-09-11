package net.jojoaddison.web.rest;

import java.util.List;
import net.jojoaddison.service.RoundCustomerService;
import net.jojoaddison.service.dto.RoundCustomerDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * The patients a round can be planned for, named for a person and addressed by hc-patient's id.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Backlog item 22. {@code RosterPlanResource}'s {@code visits[].customerId} is a
 * {@code patientservice} {@code Profile.patientId}, and the console had no way to produce one — so it
 * asked an administrator to type them in, comma separated. This endpoint is the list that replaces
 * that box: every patient in this directory whose {@code DirectoryLink} carries hc-patient's own id
 * for them, with a name to choose by.
 *
 * <p>{@link RoundCustomerService} is where the rule lives and where the argument is written out,
 * including the one a reader of item 22 will arrive expecting: <b>the entry forbids a dropdown that
 * sends {@code Patient.id}, and this one sends {@code DirectoryLink.externalId}</b>, which is the id
 * hc-professional actually keys on.
 *
 * <h2>A narrow purpose-built read, like {@code GeographicSpaceReferenceResource}</h2>
 *
 * <p>Not a widening of {@code PatientResource}, which serves the directory screen and returns whole
 * {@code Patient} documents with their profiles, plans, hubs and clinical leads on them. This
 * projection is two strings, so the surface cannot widen by accident: a field added to
 * {@code Patient} or to {@code DirectoryLink} later does not appear here.
 *
 * <p><b>Read-only and structurally so.</b> There is no write mapping in this class and nothing here
 * is anybody's record to edit — a link is a fact about another system's account, and a {@code
 * Patient} is edited on its own screen.
 *
 * <h2>Authorization: the blanket rule, deliberately, and no matcher of its own</h2>
 *
 * <p>{@code SecurityConfiguration}'s {@code GET /api/**} rule makes this {@code ROLE_ADMIN} or
 * {@code ROLE_OPERATOR} — the same tier as the patient directory screen, which already shows these
 * people's names, addresses and dates of birth to the same readers. Nothing here is narrower or
 * wider than that screen, so a matcher of its own would be a second copy of one rule to drift.
 *
 * <p><b>Deliberately not {@code ROLE_ADMIN} alone, although the planning write is.</b>
 * {@code POST /api/roster-plans} carries an {@code @PreAuthorize} because it spends the caller's own
 * token on another stack; reading which patients <em>could</em> be planned for spends nothing and
 * leaves nothing. It is also not the export's tier: {@code GET /api/patients/export} is admin-only
 * because a CSV is a copy with a lifetime of its own, and a dropdown is not.
 */
@RestController
@RequestMapping("/api/round-customers")
public class RoundCustomerResource {

    private static final Logger LOG = LoggerFactory.getLogger(RoundCustomerResource.class);

    private final RoundCustomerService roundCustomerService;

    public RoundCustomerResource(RoundCustomerService roundCustomerService) {
        this.roundCustomerService = roundCustomerService;
    }

    /**
     * {@code GET /api/round-customers} : a page of patients a visit can be planned against.
     *
     * <p>Paginated like every list in this service, and covered by {@code PaginationIT}'s sweep the
     * moment it exists — the sweep discovers single-segment {@code /api} paths from the handler
     * mapping, so this needs no entry anywhere.
     *
     * <p>The console asks for one large page rather than walking a pager: a {@code <select>} that
     * only offers the first twenty patients, with nothing on screen saying so, is the failure this
     * repository's own pagination note describes. If this list ever outgrows one page, the picker
     * needs a search rather than a bigger number.
     *
     * @param pageable the pagination information.
     * @return {@code 200 (OK)} with the page, {@code X-Total-Count} and {@code Link}.
     */
    @GetMapping("")
    public ResponseEntity<List<RoundCustomerDTO>> getPlannableCustomers(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        // A count and nothing else. The rows carry patients' names and hc-patient's ids for them,
        // and item 43's rule is that neither reaches a log at any level.
        LOG.debug("REST request to get a page of plannable round customers");
        Page<RoundCustomerDTO> page = roundCustomerService.plannableCustomers(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
