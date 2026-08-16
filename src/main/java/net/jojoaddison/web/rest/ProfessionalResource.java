package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.repository.ProfessionalRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.Professional}.
 */
@RestController
@RequestMapping("/api/professionals")
public class ProfessionalResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalResource.class);

    private static final String ENTITY_NAME = "directoryProfessional";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final ProfessionalRepository professionalRepository;

    /** For the named filters above, which need more than one optional predicate combined. */
    private final MongoTemplate mongoTemplate;

    public ProfessionalResource(ProfessionalRepository professionalRepository, MongoTemplate mongoTemplate) {
        this.professionalRepository = professionalRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * {@code POST  /professionals} : Create a new professional.
     *
     * @param professional the professional to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new professional, or with status {@code 400 (Bad Request)} if the professional has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Professional> createProfessional(@Valid @RequestBody Professional professional) throws URISyntaxException {
        LOG.debug("REST request to save Professional : {}", professional);
        if (professional.getId() != null) {
            throw new BadRequestAlertException("A new professional cannot already have an ID", ENTITY_NAME, "idexists");
        }
        professional = professionalRepository.save(professional);
        return ResponseEntity
            .created(new URI("/api/professionals/" + professional.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, professional.getId()))
            .body(professional);
    }

    /**
     * {@code PUT  /professionals/:id} : Updates an existing professional.
     *
     * @param id the id of the professional to save.
     * @param professional the professional to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated professional,
     * or with status {@code 400 (Bad Request)} if the professional is not valid,
     * or with status {@code 500 (Internal Server Error)} if the professional couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Professional> updateProfessional(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody Professional professional
    ) throws URISyntaxException {
        LOG.debug("REST request to update Professional : {}, {}", id, professional);
        if (professional.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, professional.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!professionalRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        professional = professionalRepository.save(professional);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, professional.getId()))
            .body(professional);
    }

    /**
     * {@code PATCH  /professionals/:id} : Partial updates given fields of an existing professional, field will ignore if it is null
     *
     * @param id the id of the professional to save.
     * @param professional the professional to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated professional,
     * or with status {@code 400 (Bad Request)} if the professional is not valid,
     * or with status {@code 404 (Not Found)} if the professional is not found,
     * or with status {@code 500 (Internal Server Error)} if the professional couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Professional> partialUpdateProfessional(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody Professional professional
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Professional partially : {}, {}", id, professional);
        if (professional.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, professional.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!professionalRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Professional> result = professionalRepository
            .findById(professional.getId())
            .map(existingProfessional -> {
                updateIfPresent(existingProfessional::setRole, professional.getRole());
                updateIfPresent(existingProfessional::setSpeciality, professional.getSpeciality());
                updateIfPresent(existingProfessional::setLicenceNumber, professional.getLicenceNumber());
                updateIfPresent(existingProfessional::setVerification, professional.getVerification());
                updateIfPresent(existingProfessional::setStatus, professional.getStatus());
                updateIfPresent(existingProfessional::setPatientCount, professional.getPatientCount());
                updateIfPresent(existingProfessional::setCaseCount, professional.getCaseCount());
                updateIfPresent(existingProfessional::setVisitCount, professional.getVisitCount());
                updateIfPresent(existingProfessional::setRating, professional.getRating());
                updateIfPresent(existingProfessional::setJoinedOn, professional.getJoinedOn());
                updateIfPresent(existingProfessional::setIsArchived, professional.getIsArchived());

                return existingProfessional;
            })
            .map(professionalRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, professional.getId())
        );
    }

    /**
     * {@code GET  /professionals} : get all the Professionals.
     *
     * @param pageable the pagination information.
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @param isArchivedEquals when true, return only archived records; when false, only unarchived.
     * @param isArchivedNotEquals the inverse, sent by the console as {@code isArchived.notEquals=true}.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Professionals in body.
     */
    @GetMapping("")
    public ResponseEntity<List<Professional>> getAllProfessionals(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload,
        @RequestParam(name = "isArchived.equals", required = false) Boolean isArchivedEquals,
        @RequestParam(name = "isArchived.notEquals", required = false) Boolean isArchivedNotEquals,
        // The directory tiles filter on these and read their counts from X-Total-Count. Undeclared,
        // Spring drops them and every tile reads the collection total.
        @RequestParam(name = "status.equals", required = false) AccountStatus statusEquals,
        @RequestParam(name = "role.equals", required = false) ProfessionalRole roleEquals
    ) {
        LOG.debug("REST request to get a page of Professionals");
        // The two operators the console sends, and only those. This is not a criteria framework:
        // every other entity here lists unfiltered, and inventing a general query language for one
        // boolean would be a much larger surface than the screen that needs it.
        Boolean archived = resolveArchivedFilter(isArchivedEquals, isArchivedNotEquals);

        // eagerload is not a distinction MongoDB makes here — findAllWithEagerRelationships is
        // literally @Query("{}") — so the filtered queries serve both branches.
        NamedFilters.Builder filters = NamedFilters.builder().equals("status", statusEquals).equals("role", roleEquals);
        // Archived stays `$ne: true` rather than `is(false)`: a document written before the field
        // existed does not carry it, and `is_archived: false` matches none of them.
        if (archived != null) {
            if (archived) {
                filters.equals("is_archived", true);
            } else {
                filters.notEquals("is_archived", true);
            }
        }

        Page<Professional> page;
        if (filters.isEmpty()) {
            page = eagerload ? professionalRepository.findAllWithEagerRelationships(pageable) : professionalRepository.findAll(pageable);
        } else {
            page = NamedFilters.page(mongoTemplate, Professional.class, filters, pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /professionals/:id} : get the "id" professional.
     *
     * @param id the id of the professional to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the professional, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Professional> getProfessional(@PathVariable("id") String id) {
        LOG.debug("REST request to get Professional : {}", id);
        Optional<Professional> professional = professionalRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(professional);
    }

    /**
     * {@code DELETE  /professionals/:id} : delete the "id" professional.
     *
     * @param id the id of the professional to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfessional(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Professional : {}", id);
        professionalRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Collapses the two operators into a single "want archived?" answer, or null for no filter.
     *
     * <p>{@code equals} wins if both are sent. They can only disagree by a caller's mistake, and
     * answering the positive form is less surprising than picking one silently or erroring.
     */
    private static Boolean resolveArchivedFilter(Boolean equals, Boolean notEquals) {
        if (equals != null) {
            return equals;
        }
        if (notEquals != null) {
            return !notEquals;
        }
        return null;
    }
}
