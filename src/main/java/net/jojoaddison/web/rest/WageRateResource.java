package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.WageRateRepository;
import net.jojoaddison.service.WageRateService;
import net.jojoaddison.service.dto.WageRateDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.WageRate}.
 *
 * <p>Writes here are {@code ROLE_ADMIN} and reads are {@code ROLE_OPERATOR} or better, inherited
 * from the blanket {@code /api/**} read/write split in {@code SecurityConfiguration} — this resource
 * declares no matchers of its own.
 */
@RestController
@RequestMapping("/api/wage-rates")
public class WageRateResource {

    private static final Logger LOG = LoggerFactory.getLogger(WageRateResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceWageRate";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final WageRateService wageRateService;

    private final WageRateRepository wageRateRepository;

    public WageRateResource(WageRateService wageRateService, WageRateRepository wageRateRepository) {
        this.wageRateService = wageRateService;
        this.wageRateRepository = wageRateRepository;
    }

    /**
     * {@code POST  /wage-rates} : Create a new wageRate.
     *
     * <p>This is also how a price <em>change</em> is recorded: a new row with a later
     * {@code validFrom}, leaving the superseded rate in place as history.
     *
     * @param wageRateDTO the wageRateDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new wageRateDTO, or with status {@code 400 (Bad Request)} if the wageRate has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<WageRateDTO> createWageRate(@Valid @RequestBody WageRateDTO wageRateDTO) throws URISyntaxException {
        LOG.debug("REST request to save WageRate : {}", wageRateDTO);
        if (wageRateDTO.getId() != null) {
            throw new BadRequestAlertException("A new wageRate cannot already have an ID", ENTITY_NAME, "idexists");
        }
        wageRateDTO = wageRateService.save(wageRateDTO);
        return ResponseEntity
            .created(new URI("/api/wage-rates/" + wageRateDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, wageRateDTO.getId()))
            .body(wageRateDTO);
    }

    /**
     * {@code PUT  /wage-rates/:id} : Updates an existing wageRate.
     *
     * <p>Correcting a rate that was entered wrongly. Moving a price for the future is a {@code POST}
     * of a new dated row, not this.
     *
     * @param id the id of the wageRateDTO to save.
     * @param wageRateDTO the wageRateDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated wageRateDTO,
     * or with status {@code 400 (Bad Request)} if the wageRateDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the wageRateDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<WageRateDTO> updateWageRate(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody WageRateDTO wageRateDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update WageRate : {}, {}", id, wageRateDTO);
        if (wageRateDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, wageRateDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!wageRateRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        wageRateDTO = wageRateService.update(wageRateDTO);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, wageRateDTO.getId()))
            .body(wageRateDTO);
    }

    /**
     * {@code PATCH  /wage-rates/:id} : Partial updates given fields of an existing wageRate, field will ignore if it is null
     *
     * @param id the id of the wageRateDTO to save.
     * @param wageRateDTO the wageRateDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated wageRateDTO,
     * or with status {@code 400 (Bad Request)} if the wageRateDTO is not valid,
     * or with status {@code 404 (Not Found)} if the wageRateDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the wageRateDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<WageRateDTO> partialUpdateWageRate(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody WageRateDTO wageRateDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update WageRate partially : {}, {}", id, wageRateDTO);
        if (wageRateDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, wageRateDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!wageRateRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<WageRateDTO> result = wageRateService.partialUpdate(wageRateDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, wageRateDTO.getId())
        );
    }

    /**
     * {@code GET  /wage-rates} : get all the wageRates.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of wageRates in body.
     */
    @GetMapping("")
    public ResponseEntity<List<WageRateDTO>> getAllWageRates(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of WageRates");
        Page<WageRateDTO> page = wageRateService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /wage-rates/current} : the rate in force for each {@code (role, shiftType)} cell.
     *
     * <p>One row per priced cell, not a page — the result is bounded by two enums (five roles by
     * five shift types), and the configuration screen wants all of it at once.
     *
     * <p><b>This returned one row per role until 2026-09-04.</b> The response shape did not change —
     * still a flat list of rates, each naming its own role and shift type — so a client grouping by
     * role still works and now finds several rows per group instead of one. A client that assumed
     * the list was one-per-role and indexed it that way sees the last shift type to be priced;
     * {@code app/}'s wage-rates screen was moved to the grid in the same change.
     *
     * @param asOf the date to resolve rates for; defaults to today.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the current rates in body.
     */
    @GetMapping("/current")
    public ResponseEntity<List<WageRateDTO>> getCurrentWageRates(
        @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        LOG.debug("REST request to get the current WageRates as of {}", asOf);
        return ResponseEntity.ok(wageRateService.currentRates(asOf == null ? LocalDate.now() : asOf));
    }

    /**
     * {@code GET  /wage-rates/history/:role} : every rate ever set for a role, newest first.
     *
     * <p>Narrowed to one cell with {@code ?shiftType=NIGHT}, which is what the console's per-cell
     * history panel asks for. Without it the answer is the whole role across every shift type — kept
     * deliberately, because "what has this role ever been paid" is a real question and because
     * quietly redefining an existing path to mean something narrower is worse than extending it.
     *
     * @param role the role to read the history of.
     * @param shiftType the shift type to narrow to, or absent for every shift type.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the history in body.
     */
    @GetMapping("/history/{role}")
    public ResponseEntity<List<WageRateDTO>> getWageRateHistory(
        @PathVariable("role") ProfessionalRole role,
        @RequestParam(name = "shiftType", required = false) ShiftType shiftType
    ) {
        LOG.debug("REST request to get the WageRate history for {} {}", role, shiftType);
        return ResponseEntity.ok(wageRateService.historyFor(role, shiftType));
    }

    /**
     * {@code GET  /wage-rates/:id} : get the "id" wageRate.
     *
     * @param id the id of the wageRateDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the wageRateDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WageRateDTO> getWageRate(@PathVariable("id") String id) {
        LOG.debug("REST request to get WageRate : {}", id);
        Optional<WageRateDTO> wageRateDTO = wageRateService.findOne(id);
        return ResponseUtil.wrapOrNotFound(wageRateDTO);
    }

    /**
     * {@code DELETE  /wage-rates/:id} : delete the "id" wageRate.
     *
     * @param id the id of the wageRateDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWageRate(@PathVariable("id") String id) {
        LOG.debug("REST request to delete WageRate : {}", id);
        wageRateService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
