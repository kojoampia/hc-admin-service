package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.repository.ShiftAssignmentRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.ShiftAssignment}.
 */
@RestController
@RequestMapping("/api/shift-assignments")
public class ShiftAssignmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftAssignmentResource.class);

    private static final String ENTITY_NAME = "operationsShiftAssignment";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final ShiftAssignmentRepository shiftAssignmentRepository;

    /** For the named filters below, which need more than one optional predicate combined. */
    private final MongoTemplate mongoTemplate;

    public ShiftAssignmentResource(ShiftAssignmentRepository shiftAssignmentRepository, MongoTemplate mongoTemplate) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * {@code POST  /shift-assignments} : Create a new shiftAssignment.
     *
     * @param shiftAssignment the shiftAssignment to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new shiftAssignment, or with status {@code 400 (Bad Request)} if the shiftAssignment has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ShiftAssignment> createShiftAssignment(@Valid @RequestBody ShiftAssignment shiftAssignment)
        throws URISyntaxException {
        LOG.debug("REST request to save ShiftAssignment : {}", shiftAssignment);
        if (shiftAssignment.getId() != null) {
            throw new BadRequestAlertException("A new shiftAssignment cannot already have an ID", ENTITY_NAME, "idexists");
        }
        shiftAssignment = shiftAssignmentRepository.save(shiftAssignment);
        return ResponseEntity
            .created(new URI("/api/shift-assignments/" + shiftAssignment.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, shiftAssignment.getId()))
            .body(shiftAssignment);
    }

    /**
     * {@code PUT  /shift-assignments/:id} : Updates an existing shiftAssignment.
     *
     * @param id the id of the shiftAssignment to save.
     * @param shiftAssignment the shiftAssignment to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated shiftAssignment,
     * or with status {@code 400 (Bad Request)} if the shiftAssignment is not valid,
     * or with status {@code 500 (Internal Server Error)} if the shiftAssignment couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ShiftAssignment> updateShiftAssignment(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody ShiftAssignment shiftAssignment
    ) throws URISyntaxException {
        LOG.debug("REST request to update ShiftAssignment : {}, {}", id, shiftAssignment);
        if (shiftAssignment.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, shiftAssignment.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!shiftAssignmentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        shiftAssignment = shiftAssignmentRepository.save(shiftAssignment);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, shiftAssignment.getId()))
            .body(shiftAssignment);
    }

    /**
     * {@code PATCH  /shift-assignments/:id} : Partial updates given fields of an existing shiftAssignment, field will ignore if it is null
     *
     * @param id the id of the shiftAssignment to save.
     * @param shiftAssignment the shiftAssignment to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated shiftAssignment,
     * or with status {@code 400 (Bad Request)} if the shiftAssignment is not valid,
     * or with status {@code 404 (Not Found)} if the shiftAssignment is not found,
     * or with status {@code 500 (Internal Server Error)} if the shiftAssignment couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ShiftAssignment> partialUpdateShiftAssignment(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody ShiftAssignment shiftAssignment
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ShiftAssignment partially : {}, {}", id, shiftAssignment);
        if (shiftAssignment.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, shiftAssignment.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!shiftAssignmentRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ShiftAssignment> result = shiftAssignmentRepository
            .findById(shiftAssignment.getId())
            .map(existingShiftAssignment -> {
                updateIfPresent(existingShiftAssignment::setDayIndex, shiftAssignment.getDayIndex());
                updateIfPresent(existingShiftAssignment::setShiftDate, shiftAssignment.getShiftDate());
                updateIfPresent(existingShiftAssignment::setShift, shiftAssignment.getShift());

                return existingShiftAssignment;
            })
            .map(shiftAssignmentRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, shiftAssignment.getId())
        );
    }

    /**
     * {@code GET  /shift-assignments} : get all the Shift Assignments.
     *
     * @param pageable the pagination information.
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Shift Assignments in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ShiftAssignment>> getAllShiftAssignments(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload,
        // The roster asks for one week, the professional record for one professional. Both were
        // dropped: the roster reads 500 rows to render seven columns, and the record filtered the
        // whole collection in the browser.
        //
        // The paths below are `week.id` and not `week.$id`. A DBRef is stored as { $ref, $id }, so
        // `$id` is the right *field* — but writing it literally bypasses Spring Data's query mapper,
        // which is also what converts the String id to the ObjectId actually stored. Written that way
        // it matches nothing, and an empty roster is indistinguishable from a quiet week. Given the
        // property path, the mapper rewrites the field to `week.$id` and converts the value with it.
        @RequestParam(name = "weekId.equals", required = false) String weekIdEquals,
        @RequestParam(name = "professionalId.equals", required = false) String professionalIdEquals
    ) {
        LOG.debug("REST request to get a page of ShiftAssignments");
        Page<ShiftAssignment> page;
        NamedFilters.Builder filters = NamedFilters
            .builder()
            .equals("week.id", weekIdEquals)
            .equals("professional.id", professionalIdEquals);
        if (!filters.isEmpty()) {
            page = NamedFilters.page(mongoTemplate, ShiftAssignment.class, filters, pageable);
        } else if (eagerload) {
            page = shiftAssignmentRepository.findAllWithEagerRelationships(pageable);
        } else {
            page = shiftAssignmentRepository.findAll(pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /shift-assignments/:id} : get the "id" shiftAssignment.
     *
     * @param id the id of the shiftAssignment to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the shiftAssignment, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShiftAssignment> getShiftAssignment(@PathVariable("id") String id) {
        LOG.debug("REST request to get ShiftAssignment : {}", id);
        Optional<ShiftAssignment> shiftAssignment = shiftAssignmentRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(shiftAssignment);
    }

    /**
     * {@code DELETE  /shift-assignments/:id} : delete the "id" shiftAssignment.
     *
     * @param id the id of the shiftAssignment to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShiftAssignment(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ShiftAssignment : {}", id);
        shiftAssignmentRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
