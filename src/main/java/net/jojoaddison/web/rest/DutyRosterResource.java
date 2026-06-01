package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.service.DutyRosterService;
import net.jojoaddison.service.dto.DutyRosterDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.DutyRoster}.
 */
@RestController
@RequestMapping("/api/duty-rosters")
public class DutyRosterResource {

    private static final Logger LOG = LoggerFactory.getLogger(DutyRosterResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceDutyRoster";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DutyRosterService dutyRosterService;

    private final DutyRosterRepository dutyRosterRepository;

    public DutyRosterResource(DutyRosterService dutyRosterService, DutyRosterRepository dutyRosterRepository) {
        this.dutyRosterService = dutyRosterService;
        this.dutyRosterRepository = dutyRosterRepository;
    }

    /**
     * {@code POST  /duty-rosters} : Create a new dutyRoster.
     *
     * @param dutyRosterDTO the dutyRosterDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dutyRosterDTO, or with status {@code 400 (Bad Request)} if the dutyRoster has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<DutyRosterDTO> createDutyRoster(@Valid @RequestBody DutyRosterDTO dutyRosterDTO) throws URISyntaxException {
        LOG.debug("REST request to save DutyRoster : {}", dutyRosterDTO);
        if (dutyRosterDTO.getId() != null) {
            throw new BadRequestAlertException("A new dutyRoster cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dutyRosterDTO = dutyRosterService.save(dutyRosterDTO);
        return ResponseEntity
            .created(new URI("/api/duty-rosters/" + dutyRosterDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, dutyRosterDTO.getId()))
            .body(dutyRosterDTO);
    }

    /**
     * {@code PUT  /duty-rosters/:id} : Updates an existing dutyRoster.
     *
     * @param id the id of the dutyRosterDTO to save.
     * @param dutyRosterDTO the dutyRosterDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dutyRosterDTO,
     * or with status {@code 400 (Bad Request)} if the dutyRosterDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dutyRosterDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DutyRosterDTO> updateDutyRoster(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody DutyRosterDTO dutyRosterDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DutyRoster : {}, {}", id, dutyRosterDTO);
        if (dutyRosterDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dutyRosterDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dutyRosterRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        dutyRosterDTO = dutyRosterService.update(dutyRosterDTO);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, dutyRosterDTO.getId()))
            .body(dutyRosterDTO);
    }

    /**
     * {@code PATCH  /duty-rosters/:id} : Partial updates given fields of an existing dutyRoster, field will ignore if it is null
     *
     * @param id the id of the dutyRosterDTO to save.
     * @param dutyRosterDTO the dutyRosterDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dutyRosterDTO,
     * or with status {@code 400 (Bad Request)} if the dutyRosterDTO is not valid,
     * or with status {@code 404 (Not Found)} if the dutyRosterDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the dutyRosterDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DutyRosterDTO> partialUpdateDutyRoster(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody DutyRosterDTO dutyRosterDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DutyRoster partially : {}, {}", id, dutyRosterDTO);
        if (dutyRosterDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dutyRosterDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dutyRosterRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DutyRosterDTO> result = dutyRosterService.partialUpdate(dutyRosterDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, dutyRosterDTO.getId())
        );
    }

    /**
     * {@code GET  /duty-rosters} : get all the dutyRosters.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dutyRosters in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DutyRosterDTO>> getAllDutyRosters(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of DutyRosters");
        Page<DutyRosterDTO> page = dutyRosterService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /duty-rosters/:id} : get the "id" dutyRoster.
     *
     * @param id the id of the dutyRosterDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dutyRosterDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DutyRosterDTO> getDutyRoster(@PathVariable("id") String id) {
        LOG.debug("REST request to get DutyRoster : {}", id);
        Optional<DutyRosterDTO> dutyRosterDTO = dutyRosterService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dutyRosterDTO);
    }

    /**
     * {@code DELETE  /duty-rosters/:id} : delete the "id" dutyRoster.
     *
     * @param id the id of the dutyRosterDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDutyRoster(@PathVariable("id") String id) {
        LOG.debug("REST request to delete DutyRoster : {}", id);
        dutyRosterService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST  /duty-rosters/auto-schedule} : auto-assign professionals to unassigned shifts for a given date.
     *
     * @param date the date to auto-schedule shifts for (ISO date format, e.g. 2025-07-14).
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PostMapping("/auto-schedule")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> autoSchedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LOG.debug("REST request to auto-schedule duty roster for date : {}", date);
        dutyRosterService.autoScheduleShifts(date);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET  /duty-rosters/patient/:patientId} : get the daily service plan for a patient on a given date.
     *
     * @param patientId the patient's profile id.
     * @param date      the target date (ISO date format, e.g. 2025-07-14).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of duty rosters in body.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PATIENT')")
    public ResponseEntity<List<DutyRosterDTO>> getPatientDailyPlan(
        @PathVariable String patientId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LOG.debug("REST request to get daily plan for patient {} on date {}", patientId, date);
        List<DutyRosterDTO> plan = dutyRosterService.findByPatientAndDate(patientId, date);
        return ResponseEntity.ok(plan);
    }
}
