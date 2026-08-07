package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import net.jojoaddison.domain.Angel;
import net.jojoaddison.repository.AngelRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Angel}.
 */
@RestController
@RequestMapping("/api/angels")
public class AngelResource {

    private static final Logger LOG = LoggerFactory.getLogger(AngelResource.class);

    private static final String ENTITY_NAME = "directoryAngel";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final AngelRepository angelRepository;

    public AngelResource(AngelRepository angelRepository) {
        this.angelRepository = angelRepository;
    }

    /**
     * {@code POST  /angels} : Create a new angel.
     *
     * @param angel the angel to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new angel, or with status {@code 400 (Bad Request)} if the angel has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Angel> createAngel(@Valid @RequestBody Angel angel) throws URISyntaxException {
        LOG.debug("REST request to save Angel : {}", angel);
        if (angel.getId() != null) {
            throw new BadRequestAlertException("A new angel cannot already have an ID", ENTITY_NAME, "idexists");
        }
        angel = angelRepository.save(angel);
        return ResponseEntity
            .created(new URI("/api/angels/" + angel.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, angel.getId()))
            .body(angel);
    }

    /**
     * {@code PUT  /angels/:id} : Updates an existing angel.
     *
     * @param id the id of the angel to save.
     * @param angel the angel to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated angel,
     * or with status {@code 400 (Bad Request)} if the angel is not valid,
     * or with status {@code 500 (Internal Server Error)} if the angel couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Angel> updateAngel(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody Angel angel
    ) throws URISyntaxException {
        LOG.debug("REST request to update Angel : {}, {}", id, angel);
        if (angel.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, angel.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!angelRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        angel = angelRepository.save(angel);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, angel.getId()))
            .body(angel);
    }

    /**
     * {@code PATCH  /angels/:id} : Partial updates given fields of an existing angel, field will ignore if it is null
     *
     * @param id the id of the angel to save.
     * @param angel the angel to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated angel,
     * or with status {@code 400 (Bad Request)} if the angel is not valid,
     * or with status {@code 404 (Not Found)} if the angel is not found,
     * or with status {@code 500 (Internal Server Error)} if the angel couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Angel> partialUpdateAngel(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody Angel angel
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Angel partially : {}, {}", id, angel);
        if (angel.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, angel.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!angelRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Angel> result = angelRepository
            .findById(angel.getId())
            .map(existingAngel -> {
                updateIfPresent(existingAngel::setName, angel.getName());
                updateIfPresent(existingAngel::setRelationship, angel.getRelationship());
                updateIfPresent(existingAngel::setPhone, angel.getPhone());
                updateIfPresent(existingAngel::setEmail, angel.getEmail());
                updateIfPresent(existingAngel::setCountry, angel.getCountry());

                return existingAngel;
            })
            .map(angelRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, angel.getId()));
    }

    /**
     * {@code GET  /angels} : get all the Angels.
     *
     * @param filter the filter of the request.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Angels in body.
     */
    @GetMapping("")
    public List<Angel> getAllAngels(@RequestParam(name = "filter", required = false) String filter) {
        if ("patient-is-null".equals(filter)) {
            LOG.debug("REST request to get all Angels where patient is null");
            return StreamSupport
                .stream(angelRepository.findAll().spliterator(), false)
                .filter(angel -> angel.getPatient() == null)
                .toList();
        }
        LOG.debug("REST request to get all Angels");
        return angelRepository.findAll();
    }

    /**
     * {@code GET  /angels/:id} : get the "id" angel.
     *
     * @param id the id of the angel to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the angel, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Angel> getAngel(@PathVariable("id") String id) {
        LOG.debug("REST request to get Angel : {}", id);
        Optional<Angel> angel = angelRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(angel);
    }

    /**
     * {@code DELETE  /angels/:id} : delete the "id" angel.
     *
     * @param id the id of the angel to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAngel(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Angel : {}", id);
        angelRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
