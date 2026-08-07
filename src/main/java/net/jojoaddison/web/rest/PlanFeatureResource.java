package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.PlanFeature;
import net.jojoaddison.repository.PlanFeatureRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.PlanFeature}.
 */
@RestController
@RequestMapping("/api/plan-features")
public class PlanFeatureResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlanFeatureResource.class);

    private static final String ENTITY_NAME = "cataloguePlanFeature";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final PlanFeatureRepository planFeatureRepository;

    public PlanFeatureResource(PlanFeatureRepository planFeatureRepository) {
        this.planFeatureRepository = planFeatureRepository;
    }

    /**
     * {@code POST  /plan-features} : Create a new planFeature.
     *
     * @param planFeature the planFeature to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new planFeature, or with status {@code 400 (Bad Request)} if the planFeature has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<PlanFeature> createPlanFeature(@Valid @RequestBody PlanFeature planFeature) throws URISyntaxException {
        LOG.debug("REST request to save PlanFeature : {}", planFeature);
        if (planFeature.getId() != null) {
            throw new BadRequestAlertException("A new planFeature cannot already have an ID", ENTITY_NAME, "idexists");
        }
        planFeature = planFeatureRepository.save(planFeature);
        return ResponseEntity
            .created(new URI("/api/plan-features/" + planFeature.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, planFeature.getId()))
            .body(planFeature);
    }

    /**
     * {@code PUT  /plan-features/:id} : Updates an existing planFeature.
     *
     * @param id the id of the planFeature to save.
     * @param planFeature the planFeature to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated planFeature,
     * or with status {@code 400 (Bad Request)} if the planFeature is not valid,
     * or with status {@code 500 (Internal Server Error)} if the planFeature couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlanFeature> updatePlanFeature(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody PlanFeature planFeature
    ) throws URISyntaxException {
        LOG.debug("REST request to update PlanFeature : {}, {}", id, planFeature);
        if (planFeature.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, planFeature.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!planFeatureRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        planFeature = planFeatureRepository.save(planFeature);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, planFeature.getId()))
            .body(planFeature);
    }

    /**
     * {@code PATCH  /plan-features/:id} : Partial updates given fields of an existing planFeature, field will ignore if it is null
     *
     * @param id the id of the planFeature to save.
     * @param planFeature the planFeature to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated planFeature,
     * or with status {@code 400 (Bad Request)} if the planFeature is not valid,
     * or with status {@code 404 (Not Found)} if the planFeature is not found,
     * or with status {@code 500 (Internal Server Error)} if the planFeature couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<PlanFeature> partialUpdatePlanFeature(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody PlanFeature planFeature
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update PlanFeature partially : {}, {}", id, planFeature);
        if (planFeature.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, planFeature.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!planFeatureRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<PlanFeature> result = planFeatureRepository
            .findById(planFeature.getId())
            .map(existingPlanFeature -> {
                updateIfPresent(existingPlanFeature::setLabel, planFeature.getLabel());
                updateIfPresent(existingPlanFeature::setPosition, planFeature.getPosition());

                return existingPlanFeature;
            })
            .map(planFeatureRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, planFeature.getId())
        );
    }

    /**
     * {@code GET  /plan-features} : get all the Plan Features.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Plan Features in body.
     */
    @GetMapping("")
    public List<PlanFeature> getAllPlanFeatures(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all PlanFeatures");
        if (eagerload) {
            return planFeatureRepository.findAllWithEagerRelationships();
        } else {
            return planFeatureRepository.findAll();
        }
    }

    /**
     * {@code GET  /plan-features/:id} : get the "id" planFeature.
     *
     * @param id the id of the planFeature to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the planFeature, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlanFeature> getPlanFeature(@PathVariable("id") String id) {
        LOG.debug("REST request to get PlanFeature : {}", id);
        Optional<PlanFeature> planFeature = planFeatureRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(planFeature);
    }

    /**
     * {@code DELETE  /plan-features/:id} : delete the "id" planFeature.
     *
     * @param id the id of the planFeature to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlanFeature(@PathVariable("id") String id) {
        LOG.debug("REST request to delete PlanFeature : {}", id);
        planFeatureRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
