package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Hub;
import net.jojoaddison.repository.HubRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Hub}.
 */
@RestController
@RequestMapping("/api/hubs")
public class HubResource {

    private static final Logger LOG = LoggerFactory.getLogger(HubResource.class);

    private static final String ENTITY_NAME = "platformHub";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final HubRepository hubRepository;

    public HubResource(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    /**
     * {@code POST  /hubs} : Create a new hub.
     *
     * @param hub the hub to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new hub, or with status {@code 400 (Bad Request)} if the hub has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Hub> createHub(@Valid @RequestBody Hub hub) throws URISyntaxException {
        LOG.debug("REST request to save Hub : {}", hub);
        if (hub.getId() != null) {
            throw new BadRequestAlertException("A new hub cannot already have an ID", ENTITY_NAME, "idexists");
        }
        hub = hubRepository.save(hub);
        return ResponseEntity
            .created(new URI("/api/hubs/" + hub.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, hub.getId()))
            .body(hub);
    }

    /**
     * {@code PUT  /hubs/:id} : Updates an existing hub.
     *
     * @param id the id of the hub to save.
     * @param hub the hub to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated hub,
     * or with status {@code 400 (Bad Request)} if the hub is not valid,
     * or with status {@code 500 (Internal Server Error)} if the hub couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Hub> updateHub(@PathVariable(value = "id", required = false) final String id, @Valid @RequestBody Hub hub)
        throws URISyntaxException {
        LOG.debug("REST request to update Hub : {}, {}", id, hub);
        if (hub.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hub.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hubRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        hub = hubRepository.save(hub);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hub.getId())).body(hub);
    }

    /**
     * {@code PATCH  /hubs/:id} : Partial updates given fields of an existing hub, field will ignore if it is null
     *
     * @param id the id of the hub to save.
     * @param hub the hub to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated hub,
     * or with status {@code 400 (Bad Request)} if the hub is not valid,
     * or with status {@code 404 (Not Found)} if the hub is not found,
     * or with status {@code 500 (Internal Server Error)} if the hub couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Hub> partialUpdateHub(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody Hub hub
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Hub partially : {}, {}", id, hub);
        if (hub.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hub.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hubRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Hub> result = hubRepository
            .findById(hub.getId())
            .map(existingHub -> {
                updateIfPresent(existingHub::setName, hub.getName());
                updateIfPresent(existingHub::setStaffCount, hub.getStaffCount());

                return existingHub;
            })
            .map(hubRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hub.getId()));
    }

    /**
     * {@code GET  /hubs} : get all the Hubs.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Hubs in body.
     */
    @GetMapping("")
    public List<Hub> getAllHubs() {
        LOG.debug("REST request to get all Hubs");
        return hubRepository.findAll();
    }

    /**
     * {@code GET  /hubs/:id} : get the "id" hub.
     *
     * @param id the id of the hub to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the hub, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Hub> getHub(@PathVariable("id") String id) {
        LOG.debug("REST request to get Hub : {}", id);
        Optional<Hub> hub = hubRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(hub);
    }

    /**
     * {@code DELETE  /hubs/:id} : delete the "id" hub.
     *
     * @param id the id of the hub to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHub(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Hub : {}", id);
        hubRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
