package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.repository.PlatformServiceRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.PlatformService}.
 */
@RestController
@RequestMapping("/api/platform-services")
public class PlatformServiceResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformServiceResource.class);

    private static final String ENTITY_NAME = "platformService";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PlatformServiceRepository platformServiceRepository;

    public PlatformServiceResource(PlatformServiceRepository platformServiceRepository) {
        this.platformServiceRepository = platformServiceRepository;
    }

    /**
     * {@code GET  /platform-services} : get all the Platform Services.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Platform Services in body.
     */
    /**
     * {@code POST  /platform-services} : create a new platformService.
     *
     * <p>This resource was read-only until 2026-08-12, which meant the console's platform-health map
     * could only ever show what a seed profile had put there — and production seeds nothing, so it
     * showed an empty grid. The records are now written by
     * {@code prod-server/sync-platform-services.sh} in hc-admin-ci, which reads the observability
     * stack every six hours and upserts what it finds.
     *
     * <p>Ordinary JHipster CRUD shape rather than a bespoke bulk-upsert endpoint: the sync script
     * does a GET, matches on host and port, and chooses between this and {@code PUT}. That keeps the
     * contract the same as every other resource here, and keeps the "which record is this" decision
     * in the caller, where the natural key is known.
     *
     * @param platformService the platformService to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)}.
     */
    @PostMapping("")
    public ResponseEntity<PlatformService> createPlatformService(@Valid @RequestBody PlatformService platformService)
        throws URISyntaxException {
        LOG.debug("REST request to save PlatformService : {}", platformService);
        if (platformService.getId() != null) {
            throw new BadRequestAlertException("A new platformService cannot already have an ID", ENTITY_NAME, "idexists");
        }
        PlatformService result = platformServiceRepository.save(platformService);
        return ResponseEntity
            .created(new URI("/api/platform-services/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /platform-services/:id} : update an existing platformService.
     *
     * @param id the id of the platformService to save.
     * @param platformService the platformService to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlatformService> updatePlatformService(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody PlatformService platformService
    ) {
        LOG.debug("REST request to update PlatformService : {}, {}", id, platformService);
        if (platformService.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, platformService.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!platformServiceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        PlatformService result = platformServiceRepository.save(platformService);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, platformService.getId()))
            .body(result);
    }

    @GetMapping("")
    public List<PlatformService> getAllPlatformServices() {
        LOG.debug("REST request to get all PlatformServices");
        return platformServiceRepository.findAll();
    }

    /**
     * {@code GET  /platform-services/:id} : get the "id" platformService.
     *
     * @param id the id of the platformService to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the platformService, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlatformService> getPlatformService(@PathVariable("id") String id) {
        LOG.debug("REST request to get PlatformService : {}", id);
        Optional<PlatformService> platformService = platformServiceRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(platformService);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
