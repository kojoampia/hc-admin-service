package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.repository.RosterWeekRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.RosterWeek}.
 */
@RestController
@RequestMapping("/api/roster-weeks")
public class RosterWeekResource {

    private static final Logger LOG = LoggerFactory.getLogger(RosterWeekResource.class);

    private static final String ENTITY_NAME = "operationsRosterWeek";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final RosterWeekRepository rosterWeekRepository;

    public RosterWeekResource(RosterWeekRepository rosterWeekRepository) {
        this.rosterWeekRepository = rosterWeekRepository;
    }

    /**
     * {@code POST  /roster-weeks} : Create a new rosterWeek.
     *
     * @param rosterWeek the rosterWeek to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new rosterWeek, or with status {@code 400 (Bad Request)} if the rosterWeek has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<RosterWeek> createRosterWeek(@Valid @RequestBody RosterWeek rosterWeek) throws URISyntaxException {
        LOG.debug("REST request to save RosterWeek : {}", rosterWeek);
        if (rosterWeek.getId() != null) {
            throw new BadRequestAlertException("A new rosterWeek cannot already have an ID", ENTITY_NAME, "idexists");
        }
        rosterWeek = rosterWeekRepository.save(rosterWeek);
        return ResponseEntity
            .created(new URI("/api/roster-weeks/" + rosterWeek.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, rosterWeek.getId()))
            .body(rosterWeek);
    }

    /**
     * {@code PUT  /roster-weeks/:id} : Updates an existing rosterWeek.
     *
     * @param id the id of the rosterWeek to save.
     * @param rosterWeek the rosterWeek to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated rosterWeek,
     * or with status {@code 400 (Bad Request)} if the rosterWeek is not valid,
     * or with status {@code 500 (Internal Server Error)} if the rosterWeek couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<RosterWeek> updateRosterWeek(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody RosterWeek rosterWeek
    ) throws URISyntaxException {
        LOG.debug("REST request to update RosterWeek : {}, {}", id, rosterWeek);
        if (rosterWeek.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, rosterWeek.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!rosterWeekRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        rosterWeek = rosterWeekRepository.save(rosterWeek);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, rosterWeek.getId()))
            .body(rosterWeek);
    }

    /**
     * {@code PATCH  /roster-weeks/:id} : Partial updates given fields of an existing rosterWeek, field will ignore if it is null
     *
     * @param id the id of the rosterWeek to save.
     * @param rosterWeek the rosterWeek to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated rosterWeek,
     * or with status {@code 400 (Bad Request)} if the rosterWeek is not valid,
     * or with status {@code 404 (Not Found)} if the rosterWeek is not found,
     * or with status {@code 500 (Internal Server Error)} if the rosterWeek couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<RosterWeek> partialUpdateRosterWeek(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody RosterWeek rosterWeek
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update RosterWeek partially : {}, {}", id, rosterWeek);
        if (rosterWeek.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, rosterWeek.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!rosterWeekRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<RosterWeek> result = rosterWeekRepository
            .findById(rosterWeek.getId())
            .map(existingRosterWeek -> {
                updateIfPresent(existingRosterWeek::setLabel, rosterWeek.getLabel());
                updateIfPresent(existingRosterWeek::setStartDate, rosterWeek.getStartDate());
                updateIfPresent(existingRosterWeek::setPublished, rosterWeek.getPublished());
                updateIfPresent(existingRosterWeek::setPublishedAt, rosterWeek.getPublishedAt());

                return existingRosterWeek;
            })
            .map(rosterWeekRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, rosterWeek.getId())
        );
    }

    /**
     * {@code GET  /roster-weeks} : get all the Roster Weeks.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Roster Weeks in body.
     */
    @GetMapping("")
    public List<RosterWeek> getAllRosterWeeks() {
        LOG.debug("REST request to get all RosterWeeks");
        return rosterWeekRepository.findAll();
    }

    /**
     * {@code GET  /roster-weeks/:id} : get the "id" rosterWeek.
     *
     * @param id the id of the rosterWeek to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the rosterWeek, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RosterWeek> getRosterWeek(@PathVariable("id") String id) {
        LOG.debug("REST request to get RosterWeek : {}", id);
        Optional<RosterWeek> rosterWeek = rosterWeekRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(rosterWeek);
    }

    /**
     * {@code DELETE  /roster-weeks/:id} : delete the "id" rosterWeek.
     *
     * @param id the id of the rosterWeek to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRosterWeek(@PathVariable("id") String id) {
        LOG.debug("REST request to delete RosterWeek : {}", id);
        rosterWeekRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
