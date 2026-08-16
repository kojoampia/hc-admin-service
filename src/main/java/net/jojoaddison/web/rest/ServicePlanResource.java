package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.repository.ServicePlanRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.ServicePlan}.
 */
@RestController
@RequestMapping("/api/service-plans")
public class ServicePlanResource {

    private static final Logger LOG = LoggerFactory.getLogger(ServicePlanResource.class);

    private static final String ENTITY_NAME = "catalogueServicePlan";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final ServicePlanRepository servicePlanRepository;

    public ServicePlanResource(ServicePlanRepository servicePlanRepository) {
        this.servicePlanRepository = servicePlanRepository;
    }

    /**
     * {@code POST  /service-plans} : Create a new servicePlan.
     *
     * @param servicePlan the servicePlan to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new servicePlan, or with status {@code 400 (Bad Request)} if the servicePlan has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ServicePlan> createServicePlan(@Valid @RequestBody ServicePlan servicePlan) throws URISyntaxException {
        LOG.debug("REST request to save ServicePlan : {}", servicePlan);
        if (servicePlan.getId() != null) {
            throw new BadRequestAlertException("A new servicePlan cannot already have an ID", ENTITY_NAME, "idexists");
        }
        servicePlan = servicePlanRepository.save(servicePlan);
        return ResponseEntity
            .created(new URI("/api/service-plans/" + servicePlan.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, servicePlan.getId()))
            .body(servicePlan);
    }

    /**
     * {@code PUT  /service-plans/:id} : Updates an existing servicePlan.
     *
     * @param id the id of the servicePlan to save.
     * @param servicePlan the servicePlan to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated servicePlan,
     * or with status {@code 400 (Bad Request)} if the servicePlan is not valid,
     * or with status {@code 500 (Internal Server Error)} if the servicePlan couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServicePlan> updateServicePlan(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody ServicePlan servicePlan
    ) throws URISyntaxException {
        LOG.debug("REST request to update ServicePlan : {}, {}", id, servicePlan);
        if (servicePlan.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, servicePlan.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!servicePlanRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        servicePlan = servicePlanRepository.save(servicePlan);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, servicePlan.getId()))
            .body(servicePlan);
    }

    /**
     * {@code PATCH  /service-plans/:id} : Partial updates given fields of an existing servicePlan, field will ignore if it is null
     *
     * @param id the id of the servicePlan to save.
     * @param servicePlan the servicePlan to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated servicePlan,
     * or with status {@code 400 (Bad Request)} if the servicePlan is not valid,
     * or with status {@code 404 (Not Found)} if the servicePlan is not found,
     * or with status {@code 500 (Internal Server Error)} if the servicePlan couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ServicePlan> partialUpdateServicePlan(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody ServicePlan servicePlan
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ServicePlan partially : {}, {}", id, servicePlan);
        if (servicePlan.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, servicePlan.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!servicePlanRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ServicePlan> result = servicePlanRepository
            .findById(servicePlan.getId())
            .map(existingServicePlan -> {
                updateIfPresent(existingServicePlan::setName, servicePlan.getName());
                updateIfPresent(existingServicePlan::setTier, servicePlan.getTier());
                updateIfPresent(existingServicePlan::setTierLabel, servicePlan.getTierLabel());
                updateIfPresent(existingServicePlan::setMonthlyPrice, servicePlan.getMonthlyPrice());
                updateIfPresent(existingServicePlan::setCurrency, servicePlan.getCurrency());
                updateIfPresent(existingServicePlan::setSummary, servicePlan.getSummary());
                updateIfPresent(existingServicePlan::setFeatured, servicePlan.getFeatured());
                updateIfPresent(existingServicePlan::setSubscriberCount, servicePlan.getSubscriberCount());

                return existingServicePlan;
            })
            .map(servicePlanRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, servicePlan.getId())
        );
    }

    /**
     * {@code GET  /service-plans} : get all the Service Plans.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Service Plans in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ServicePlan>> getAllServicePlans(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of ServicePlans");
        Page<ServicePlan> page = servicePlanRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /service-plans/:id} : get the "id" servicePlan.
     *
     * @param id the id of the servicePlan to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the servicePlan, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServicePlan> getServicePlan(@PathVariable("id") String id) {
        LOG.debug("REST request to get ServicePlan : {}", id);
        Optional<ServicePlan> servicePlan = servicePlanRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(servicePlan);
    }

    /**
     * {@code DELETE  /service-plans/:id} : delete the "id" servicePlan.
     *
     * @param id the id of the servicePlan to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServicePlan(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ServicePlan : {}", id);
        servicePlanRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
