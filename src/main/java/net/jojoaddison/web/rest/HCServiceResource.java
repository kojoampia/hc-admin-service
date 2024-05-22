package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.HCService;
import net.jojoaddison.repository.HCServiceRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.HCService}.
 */
@RestController
@RequestMapping("/api/hc-services")
public class HCServiceResource {

    private final Logger log = LoggerFactory.getLogger(HCServiceResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceHcService";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final HCServiceRepository hCServiceRepository;

    public HCServiceResource(HCServiceRepository hCServiceRepository) {
        this.hCServiceRepository = hCServiceRepository;
    }

    /**
     * {@code POST  /hc-services} : Create a new hCService.
     *
     * @param hCService the hCService to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new hCService, or with status {@code 400 (Bad Request)} if the hCService has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<HCService> createHCService(@RequestBody HCService hCService) throws URISyntaxException {
        log.debug("REST request to save HCService : {}", hCService);
        if (hCService.getId() != null) {
            throw new BadRequestAlertException("A new hCService cannot already have an ID", ENTITY_NAME, "idexists");
        }
        HCService result = hCServiceRepository.save(hCService);
        return ResponseEntity
            .created(new URI("/api/hc-services/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /hc-services/:id} : Updates an existing hCService.
     *
     * @param id the id of the hCService to save.
     * @param hCService the hCService to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated hCService,
     * or with status {@code 400 (Bad Request)} if the hCService is not valid,
     * or with status {@code 500 (Internal Server Error)} if the hCService couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<HCService> updateHCService(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody HCService hCService
    ) throws URISyntaxException {
        log.debug("REST request to update HCService : {}, {}", id, hCService);
        if (hCService.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hCService.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hCServiceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        HCService result = hCServiceRepository.save(hCService);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hCService.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /hc-services/:id} : Partial updates given fields of an existing hCService, field will ignore if it is null
     *
     * @param id the id of the hCService to save.
     * @param hCService the hCService to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated hCService,
     * or with status {@code 400 (Bad Request)} if the hCService is not valid,
     * or with status {@code 404 (Not Found)} if the hCService is not found,
     * or with status {@code 500 (Internal Server Error)} if the hCService couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<HCService> partialUpdateHCService(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody HCService hCService
    ) throws URISyntaxException {
        log.debug("REST request to partial update HCService partially : {}, {}", id, hCService);
        if (hCService.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hCService.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hCServiceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<HCService> result = hCServiceRepository
            .findById(hCService.getId())
            .map(existingHCService -> {
                if (hCService.getName() != null) {
                    existingHCService.setName(hCService.getName());
                }
                if (hCService.getDescription() != null) {
                    existingHCService.setDescription(hCService.getDescription());
                }
                if (hCService.getServiceItems() != null) {
                    existingHCService.setServiceItems(hCService.getServiceItems());
                }
                if (hCService.getAmount() != null) {
                    existingHCService.setAmount(hCService.getAmount());
                }
                if (hCService.getCreatedDate() != null) {
                    existingHCService.setCreatedDate(hCService.getCreatedDate());
                }
                if (hCService.getCreatedBy() != null) {
                    existingHCService.setCreatedBy(hCService.getCreatedBy());
                }
                if (hCService.getModifiedDate() != null) {
                    existingHCService.setModifiedDate(hCService.getModifiedDate());
                }
                if (hCService.getModifiedBy() != null) {
                    existingHCService.setModifiedBy(hCService.getModifiedBy());
                }

                return existingHCService;
            })
            .map(hCServiceRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hCService.getId())
        );
    }

    /**
     * {@code GET  /hc-services} : get all the hCServices.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of hCServices in body.
     */
    @GetMapping("")
    public List<HCService> getAllHCServices() {
        log.debug("REST request to get all HCServices");
        return hCServiceRepository.findAll();
    }

    /**
     * {@code GET  /hc-services/:id} : get the "id" hCService.
     *
     * @param id the id of the hCService to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the hCService, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<HCService> getHCService(@PathVariable("id") String id) {
        log.debug("REST request to get HCService : {}", id);
        Optional<HCService> hCService = hCServiceRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(hCService);
    }

    /**
     * {@code DELETE  /hc-services/:id} : delete the "id" hCService.
     *
     * @param id the id of the hCService to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHCService(@PathVariable("id") String id) {
        log.debug("REST request to delete HCService : {}", id);
        hCServiceRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
