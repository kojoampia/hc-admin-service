package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.PricingPlanRepository;
import net.jojoaddison.service.PricingPlanService;
import net.jojoaddison.service.dto.PricingPlanDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.PricingPlan}.
 */
@RestController
@RequestMapping("/api/pricing-plans")
public class PricingPlanResource {

    private static final Logger LOG = LoggerFactory.getLogger(PricingPlanResource.class);

    private static final String ENTITY_NAME = "hcAdminServicePricingPlan";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PricingPlanService pricingPlanService;

    private final PricingPlanRepository pricingPlanRepository;

    public PricingPlanResource(PricingPlanService pricingPlanService, PricingPlanRepository pricingPlanRepository) {
        this.pricingPlanService = pricingPlanService;
        this.pricingPlanRepository = pricingPlanRepository;
    }

    /**
     * {@code POST  /pricing-plans} : Create a new pricingPlan.
     *
     * @param pricingPlanDTO the pricingPlanDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new pricingPlanDTO, or with status {@code 400 (Bad Request)} if the pricingPlan has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<PricingPlanDTO> createPricingPlan(@Valid @RequestBody PricingPlanDTO pricingPlanDTO) throws URISyntaxException {
        LOG.debug("REST request to save PricingPlan : {}", pricingPlanDTO);
        if (pricingPlanDTO.getId() != null) {
            throw new BadRequestAlertException("A new pricingPlan cannot already have an ID", ENTITY_NAME, "idexists");
        }
        pricingPlanDTO = pricingPlanService.save(pricingPlanDTO);
        return ResponseEntity
            .created(new URI("/api/pricing-plans/" + pricingPlanDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, pricingPlanDTO.getId()))
            .body(pricingPlanDTO);
    }

    /**
     * {@code PUT  /pricing-plans/:id} : Updates an existing pricingPlan.
     *
     * @param id the id of the pricingPlanDTO to save.
     * @param pricingPlanDTO the pricingPlanDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated pricingPlanDTO,
     * or with status {@code 400 (Bad Request)} if the pricingPlanDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the pricingPlanDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PricingPlanDTO> updatePricingPlan(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody PricingPlanDTO pricingPlanDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update PricingPlan : {}, {}", id, pricingPlanDTO);
        if (pricingPlanDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, pricingPlanDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!pricingPlanRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        pricingPlanDTO = pricingPlanService.update(pricingPlanDTO);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, pricingPlanDTO.getId()))
            .body(pricingPlanDTO);
    }

    /**
     * {@code PATCH  /pricing-plans/:id} : Partial updates given fields of an existing pricingPlan, field will ignore if it is null
     *
     * @param id the id of the pricingPlanDTO to save.
     * @param pricingPlanDTO the pricingPlanDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated pricingPlanDTO,
     * or with status {@code 400 (Bad Request)} if the pricingPlanDTO is not valid,
     * or with status {@code 404 (Not Found)} if the pricingPlanDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the pricingPlanDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<PricingPlanDTO> partialUpdatePricingPlan(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody PricingPlanDTO pricingPlanDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update PricingPlan partially : {}, {}", id, pricingPlanDTO);
        if (pricingPlanDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, pricingPlanDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!pricingPlanRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<PricingPlanDTO> result = pricingPlanService.partialUpdate(pricingPlanDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, pricingPlanDTO.getId())
        );
    }

    /**
     * {@code GET  /pricing-plans} : get all the pricingPlans.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of pricingPlans in body.
     */
    @GetMapping("")
    public List<PricingPlanDTO> getAllPricingPlans() {
        LOG.debug("REST request to get all PricingPlans");
        return pricingPlanService.findAll();
    }

    /**
     * {@code GET  /pricing-plans/:id} : get the "id" pricingPlan.
     *
     * @param id the id of the pricingPlanDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the pricingPlanDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PricingPlanDTO> getPricingPlan(@PathVariable("id") String id) {
        LOG.debug("REST request to get PricingPlan : {}", id);
        Optional<PricingPlanDTO> pricingPlanDTO = pricingPlanService.findOne(id);
        return ResponseUtil.wrapOrNotFound(pricingPlanDTO);
    }

    /**
     * {@code DELETE  /pricing-plans/:id} : delete the "id" pricingPlan.
     *
     * @param id the id of the pricingPlanDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePricingPlan(@PathVariable("id") String id) {
        LOG.debug("REST request to delete PricingPlan : {}", id);
        pricingPlanService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
