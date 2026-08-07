package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.CareActivity;
import net.jojoaddison.repository.CareActivityRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.CareActivity}.
 */
@RestController
@RequestMapping("/api/care-activities")
public class CareActivityResource {

    private static final Logger LOG = LoggerFactory.getLogger(CareActivityResource.class);

    private static final String ENTITY_NAME = "platformCareActivity";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final CareActivityRepository careActivityRepository;

    public CareActivityResource(CareActivityRepository careActivityRepository) {
        this.careActivityRepository = careActivityRepository;
    }

    /**
     * {@code POST  /care-activities} : Create a new careActivity.
     *
     * @param careActivity the careActivity to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new careActivity, or with status {@code 400 (Bad Request)} if the careActivity has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<CareActivity> createCareActivity(@Valid @RequestBody CareActivity careActivity) throws URISyntaxException {
        LOG.debug("REST request to save CareActivity : {}", careActivity);
        if (careActivity.getId() != null) {
            throw new BadRequestAlertException("A new careActivity cannot already have an ID", ENTITY_NAME, "idexists");
        }
        careActivity = careActivityRepository.save(careActivity);
        return ResponseEntity
            .created(new URI("/api/care-activities/" + careActivity.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, careActivity.getId()))
            .body(careActivity);
    }

    /**
     * {@code PUT  /care-activities/:id} : Updates an existing careActivity.
     *
     * @param id the id of the careActivity to save.
     * @param careActivity the careActivity to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated careActivity,
     * or with status {@code 400 (Bad Request)} if the careActivity is not valid,
     * or with status {@code 500 (Internal Server Error)} if the careActivity couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CareActivity> updateCareActivity(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody CareActivity careActivity
    ) throws URISyntaxException {
        LOG.debug("REST request to update CareActivity : {}, {}", id, careActivity);
        if (careActivity.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, careActivity.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!careActivityRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        careActivity = careActivityRepository.save(careActivity);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, careActivity.getId()))
            .body(careActivity);
    }

    /**
     * {@code PATCH  /care-activities/:id} : Partial updates given fields of an existing careActivity, field will ignore if it is null
     *
     * @param id the id of the careActivity to save.
     * @param careActivity the careActivity to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated careActivity,
     * or with status {@code 400 (Bad Request)} if the careActivity is not valid,
     * or with status {@code 404 (Not Found)} if the careActivity is not found,
     * or with status {@code 500 (Internal Server Error)} if the careActivity couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<CareActivity> partialUpdateCareActivity(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody CareActivity careActivity
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update CareActivity partially : {}, {}", id, careActivity);
        if (careActivity.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, careActivity.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!careActivityRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<CareActivity> result = careActivityRepository
            .findById(careActivity.getId())
            .map(existingCareActivity -> {
                updateIfPresent(existingCareActivity::setName, careActivity.getName());
                updateIfPresent(existingCareActivity::setDescription, careActivity.getDescription());
                updateIfPresent(existingCareActivity::setOccurredOn, careActivity.getOccurredOn());

                return existingCareActivity;
            })
            .map(careActivityRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, careActivity.getId())
        );
    }

    /**
     * {@code GET  /care-activities} : get all the Care Activities.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Care Activities in body.
     */
    @GetMapping("")
    public ResponseEntity<List<CareActivity>> getAllCareActivities(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of CareActivities");
        Page<CareActivity> page = careActivityRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /care-activities/:id} : get the "id" careActivity.
     *
     * @param id the id of the careActivity to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the careActivity, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CareActivity> getCareActivity(@PathVariable("id") String id) {
        LOG.debug("REST request to get CareActivity : {}", id);
        Optional<CareActivity> careActivity = careActivityRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(careActivity);
    }

    /**
     * {@code DELETE  /care-activities/:id} : delete the "id" careActivity.
     *
     * @param id the id of the careActivity to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCareActivity(@PathVariable("id") String id) {
        LOG.debug("REST request to delete CareActivity : {}", id);
        careActivityRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
