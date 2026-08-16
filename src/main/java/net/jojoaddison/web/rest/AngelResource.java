package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Angel;
import net.jojoaddison.repository.AngelRepository;
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
    public ResponseEntity<List<Angel>> getAllAngels(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "filter", required = false) String filter
    ) {
        LOG.debug("REST request to get a page of Angels");
        // `patient-is-null` is the relationship picker asking for the angels not already attached to
        // a patient. It used to read the whole collection and drop the attached ones in memory,
        // which cannot be paged at all — the page would be a slice of an already-materialised list.
        // As a query the database does the narrowing and the count is the count of the match.
        Page<Angel> page = "patient-is-null".equals(filter)
            ? angelRepository.findByPatientIsNull(pageable)
            : angelRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
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
