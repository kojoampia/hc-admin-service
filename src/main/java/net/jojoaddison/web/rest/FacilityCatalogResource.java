package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.FacilityCatalog;
import net.jojoaddison.repository.FacilityCatalogRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.FacilityCatalog}.
 */
@RestController
@RequestMapping("/api/facility-catalogs")
public class FacilityCatalogResource {

    private static final Logger LOG = LoggerFactory.getLogger(FacilityCatalogResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceFacilityCatalog";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FacilityCatalogRepository facilityCatalogRepository;

    public FacilityCatalogResource(FacilityCatalogRepository facilityCatalogRepository) {
        this.facilityCatalogRepository = facilityCatalogRepository;
    }

    /**
     * {@code POST  /facility-catalogs} : Create a new facilityCatalog.
     *
     * @param facilityCatalog the facilityCatalog to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new facilityCatalog, or with status {@code 400 (Bad Request)} if the facilityCatalog has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<FacilityCatalog> createFacilityCatalog(@Valid @RequestBody FacilityCatalog facilityCatalog)
        throws URISyntaxException {
        LOG.debug("REST request to save FacilityCatalog : {}", facilityCatalog);
        if (facilityCatalog.getId() != null) {
            throw new BadRequestAlertException("A new facilityCatalog cannot already have an ID", ENTITY_NAME, "idexists");
        }
        facilityCatalog = facilityCatalogRepository.save(facilityCatalog);
        return ResponseEntity
            .created(new URI("/api/facility-catalogs/" + facilityCatalog.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, facilityCatalog.getId()))
            .body(facilityCatalog);
    }

    /**
     * {@code PUT  /facility-catalogs/:id} : Updates an existing facilityCatalog.
     *
     * @param id the id of the facilityCatalog to save.
     * @param facilityCatalog the facilityCatalog to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated facilityCatalog,
     * or with status {@code 400 (Bad Request)} if the facilityCatalog is not valid,
     * or with status {@code 500 (Internal Server Error)} if the facilityCatalog couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FacilityCatalog> updateFacilityCatalog(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody FacilityCatalog facilityCatalog
    ) throws URISyntaxException {
        LOG.debug("REST request to update FacilityCatalog : {}, {}", id, facilityCatalog);
        if (facilityCatalog.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, facilityCatalog.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!facilityCatalogRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        facilityCatalog = facilityCatalogRepository.save(facilityCatalog);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, facilityCatalog.getId()))
            .body(facilityCatalog);
    }

    /**
     * {@code PATCH  /facility-catalogs/:id} : Partial updates given fields of an existing facilityCatalog, field will ignore if it is null
     *
     * @param id the id of the facilityCatalog to save.
     * @param facilityCatalog the facilityCatalog to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated facilityCatalog,
     * or with status {@code 400 (Bad Request)} if the facilityCatalog is not valid,
     * or with status {@code 404 (Not Found)} if the facilityCatalog is not found,
     * or with status {@code 500 (Internal Server Error)} if the facilityCatalog couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<FacilityCatalog> partialUpdateFacilityCatalog(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody FacilityCatalog facilityCatalog
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update FacilityCatalog partially : {}, {}", id, facilityCatalog);
        if (facilityCatalog.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, facilityCatalog.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!facilityCatalogRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<FacilityCatalog> result = facilityCatalogRepository
            .findById(facilityCatalog.getId())
            .map(existingFacilityCatalog -> {
                if (facilityCatalog.getName() != null) {
                    existingFacilityCatalog.setName(facilityCatalog.getName());
                }
                if (facilityCatalog.getDescription() != null) {
                    existingFacilityCatalog.setDescription(facilityCatalog.getDescription());
                }
                if (facilityCatalog.getFacilities() != null) {
                    existingFacilityCatalog.setFacilities(facilityCatalog.getFacilities());
                }

                return existingFacilityCatalog;
            })
            .map(facilityCatalogRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, facilityCatalog.getId())
        );
    }

    /**
     * {@code GET  /facility-catalogs} : get all the facilityCatalogs.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of facilityCatalogs in body.
     */
    @GetMapping("")
    public List<FacilityCatalog> getAllFacilityCatalogs() {
        LOG.debug("REST request to get all FacilityCatalogs");
        return facilityCatalogRepository.findAll();
    }

    /**
     * {@code GET  /facility-catalogs/:id} : get the "id" facilityCatalog.
     *
     * @param id the id of the facilityCatalog to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the facilityCatalog, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FacilityCatalog> getFacilityCatalog(@PathVariable("id") String id) {
        LOG.debug("REST request to get FacilityCatalog : {}", id);
        Optional<FacilityCatalog> facilityCatalog = facilityCatalogRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(facilityCatalog);
    }

    /**
     * {@code DELETE  /facility-catalogs/:id} : delete the "id" facilityCatalog.
     *
     * @param id the id of the facilityCatalog to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFacilityCatalog(@PathVariable("id") String id) {
        LOG.debug("REST request to delete FacilityCatalog : {}", id);
        facilityCatalogRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
