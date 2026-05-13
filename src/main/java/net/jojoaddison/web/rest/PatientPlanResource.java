package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.PatientPlanRepository;
import net.jojoaddison.service.PatientPlanService;
import net.jojoaddison.service.dto.PatientPlanDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.PatientPlan}.
 */
@RestController
@RequestMapping("/api/patient-plans")
public class PatientPlanResource {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPlanResource.class);

    private static final String ENTITY_NAME = "hcAdminServicePatientPlan";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PatientPlanService patientPlanService;

    private final PatientPlanRepository patientPlanRepository;

    public PatientPlanResource(PatientPlanService patientPlanService, PatientPlanRepository patientPlanRepository) {
        this.patientPlanService = patientPlanService;
        this.patientPlanRepository = patientPlanRepository;
    }

    /**
     * {@code POST  /patient-plans} : Create a new patientPlan.
     *
     * @param patientPlanDTO the patientPlanDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new patientPlanDTO, or with status {@code 400 (Bad Request)} if the patientPlan has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<PatientPlanDTO> createPatientPlan(@Valid @RequestBody PatientPlanDTO patientPlanDTO) throws URISyntaxException {
        LOG.debug("REST request to save PatientPlan : {}", patientPlanDTO);
        if (patientPlanDTO.getId() != null) {
            throw new BadRequestAlertException("A new patientPlan cannot already have an ID", ENTITY_NAME, "idexists");
        }
        patientPlanDTO = patientPlanService.save(patientPlanDTO);
        return ResponseEntity
            .created(new URI("/api/patient-plans/" + patientPlanDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, patientPlanDTO.getId()))
            .body(patientPlanDTO);
    }

    /**
     * {@code PUT  /patient-plans/:id} : Updates an existing patientPlan.
     *
     * @param id the id of the patientPlanDTO to save.
     * @param patientPlanDTO the patientPlanDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated patientPlanDTO,
     * or with status {@code 400 (Bad Request)} if the patientPlanDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the patientPlanDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PatientPlanDTO> updatePatientPlan(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody PatientPlanDTO patientPlanDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update PatientPlan : {}, {}", id, patientPlanDTO);
        if (patientPlanDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, patientPlanDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!patientPlanRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        patientPlanDTO = patientPlanService.update(patientPlanDTO);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, patientPlanDTO.getId()))
            .body(patientPlanDTO);
    }

    /**
     * {@code PATCH  /patient-plans/:id} : Partial updates given fields of an existing patientPlan, field will ignore if it is null
     *
     * @param id the id of the patientPlanDTO to save.
     * @param patientPlanDTO the patientPlanDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated patientPlanDTO,
     * or with status {@code 400 (Bad Request)} if the patientPlanDTO is not valid,
     * or with status {@code 404 (Not Found)} if the patientPlanDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the patientPlanDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<PatientPlanDTO> partialUpdatePatientPlan(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody PatientPlanDTO patientPlanDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update PatientPlan partially : {}, {}", id, patientPlanDTO);
        if (patientPlanDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, patientPlanDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!patientPlanRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<PatientPlanDTO> result = patientPlanService.partialUpdate(patientPlanDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, patientPlanDTO.getId())
        );
    }

    /**
     * {@code GET  /patient-plans} : get all the patientPlans.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of patientPlans in body.
     */
    @GetMapping("")
    public ResponseEntity<List<PatientPlanDTO>> getAllPatientPlans(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of PatientPlans");
        Page<PatientPlanDTO> page = patientPlanService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /patient-plans/:id} : get the "id" patientPlan.
     *
     * @param id the id of the patientPlanDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the patientPlanDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PatientPlanDTO> getPatientPlan(@PathVariable("id") String id) {
        LOG.debug("REST request to get PatientPlan : {}", id);
        Optional<PatientPlanDTO> patientPlanDTO = patientPlanService.findOne(id);
        return ResponseUtil.wrapOrNotFound(patientPlanDTO);
    }

    /**
     * {@code DELETE  /patient-plans/:id} : delete the "id" patientPlan.
     *
     * @param id the id of the patientPlanDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePatientPlan(@PathVariable("id") String id) {
        LOG.debug("REST request to delete PatientPlan : {}", id);
        patientPlanService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
