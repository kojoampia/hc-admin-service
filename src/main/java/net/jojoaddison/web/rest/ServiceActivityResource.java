package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.ServiceActivity;
import net.jojoaddison.repository.ServiceActivityRepository;
import net.jojoaddison.repository.support.NamedFilters;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.ServiceActivity}.
 */
@RestController
@RequestMapping("/api/service-activities")
public class ServiceActivityResource {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceActivityResource.class);

    private static final String ENTITY_NAME = "catalogueServiceActivity";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final ServiceActivityRepository serviceActivityRepository;

    private final MongoTemplate mongoTemplate;

    public ServiceActivityResource(ServiceActivityRepository serviceActivityRepository, MongoTemplate mongoTemplate) {
        this.serviceActivityRepository = serviceActivityRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * {@code POST  /service-activities} : Create a new serviceActivity.
     *
     * @param serviceActivity the serviceActivity to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new serviceActivity, or with status {@code 400 (Bad Request)} if the serviceActivity has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ServiceActivity> createServiceActivity(@Valid @RequestBody ServiceActivity serviceActivity)
        throws URISyntaxException {
        LOG.debug("REST request to save ServiceActivity : {}", serviceActivity);
        if (serviceActivity.getId() != null) {
            throw new BadRequestAlertException("A new serviceActivity cannot already have an ID", ENTITY_NAME, "idexists");
        }
        serviceActivity = serviceActivityRepository.save(serviceActivity);
        return ResponseEntity
            .created(new URI("/api/service-activities/" + serviceActivity.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, serviceActivity.getId()))
            .body(serviceActivity);
    }

    /**
     * {@code PUT  /service-activities/:id} : Updates an existing serviceActivity.
     *
     * @param id the id of the serviceActivity to save.
     * @param serviceActivity the serviceActivity to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated serviceActivity,
     * or with status {@code 400 (Bad Request)} if the serviceActivity is not valid,
     * or with status {@code 500 (Internal Server Error)} if the serviceActivity couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceActivity> updateServiceActivity(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody ServiceActivity serviceActivity
    ) throws URISyntaxException {
        LOG.debug("REST request to update ServiceActivity : {}, {}", id, serviceActivity);
        if (serviceActivity.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, serviceActivity.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!serviceActivityRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        serviceActivity = serviceActivityRepository.save(serviceActivity);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, serviceActivity.getId()))
            .body(serviceActivity);
    }

    /**
     * {@code PATCH  /service-activities/:id} : Partial updates given fields of an existing serviceActivity, field will ignore if it is null
     *
     * @param id the id of the serviceActivity to save.
     * @param serviceActivity the serviceActivity to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated serviceActivity,
     * or with status {@code 400 (Bad Request)} if the serviceActivity is not valid,
     * or with status {@code 404 (Not Found)} if the serviceActivity is not found,
     * or with status {@code 500 (Internal Server Error)} if the serviceActivity couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ServiceActivity> partialUpdateServiceActivity(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody ServiceActivity serviceActivity
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ServiceActivity partially : {}, {}", id, serviceActivity);
        if (serviceActivity.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, serviceActivity.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!serviceActivityRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ServiceActivity> result = serviceActivityRepository
            .findById(serviceActivity.getId())
            .map(existingServiceActivity -> {
                updateIfPresent(existingServiceActivity::setName, serviceActivity.getName());
                updateIfPresent(existingServiceActivity::setUnit, serviceActivity.getUnit());
                updateIfPresent(existingServiceActivity::setUnitPrice, serviceActivity.getUnitPrice());
                updateIfPresent(existingServiceActivity::setDuration, serviceActivity.getDuration());
                updateIfPresent(existingServiceActivity::setPublished, serviceActivity.getPublished());

                return existingServiceActivity;
            })
            .map(serviceActivityRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, serviceActivity.getId())
        );
    }

    /**
     * {@code GET  /service-activities} : get all the Service Activities.
     *
     * @param pageable the pagination information.
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @param categoryIdEquals restrict to one category; the catalogue screen's activities table.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Service Activities in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ServiceActivity>> getAllServiceActivities(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload,
        // The catalogue screen opens one category at a time. Without this the table would have to
        // read every activity in the catalogue and discard the ones belonging to other categories —
        // the client-side filtering that breaks silently the moment the collection exceeds one page,
        // which is the failure `CLAUDE.md` already documents for pagination.
        //
        // The path is `category.id`, not `category.$id`. A DBRef is stored as { $ref, $id }, so `$id`
        // is the right *field* — but writing it literally bypasses Spring Data's query mapper, which
        // is also what converts the id to the type actually stored. Written that way it matches
        // nothing, and an empty category is indistinguishable from one nobody has filled in.
        @RequestParam(name = "categoryId.equals", required = false) String categoryIdEquals
    ) {
        LOG.debug("REST request to get a page of ServiceActivities");
        Page<ServiceActivity> page;
        NamedFilters.Builder filters = NamedFilters.builder().equals("category.id", categoryIdEquals);
        if (!filters.isEmpty()) {
            page = NamedFilters.page(mongoTemplate, ServiceActivity.class, filters, pageable);
        } else if (eagerload) {
            page = serviceActivityRepository.findAllWithEagerRelationships(pageable);
        } else {
            page = serviceActivityRepository.findAll(pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /service-activities/:id} : get the "id" serviceActivity.
     *
     * @param id the id of the serviceActivity to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the serviceActivity, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceActivity> getServiceActivity(@PathVariable("id") String id) {
        LOG.debug("REST request to get ServiceActivity : {}", id);
        Optional<ServiceActivity> serviceActivity = serviceActivityRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(serviceActivity);
    }

    /**
     * {@code DELETE  /service-activities/:id} : delete the "id" serviceActivity.
     *
     * @param id the id of the serviceActivity to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServiceActivity(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ServiceActivity : {}", id);
        serviceActivityRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
