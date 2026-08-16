package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.repository.PatientRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.Patient}.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientResource {

    private static final Logger LOG = LoggerFactory.getLogger(PatientResource.class);

    private static final String ENTITY_NAME = "directoryPatient";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final PatientRepository patientRepository;

    /** For the named filters above, which need more than one optional predicate combined. */
    private final MongoTemplate mongoTemplate;

    public PatientResource(PatientRepository patientRepository, MongoTemplate mongoTemplate) {
        this.patientRepository = patientRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * {@code POST  /patients} : Create a new patient.
     *
     * @param patient the patient to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new patient, or with status {@code 400 (Bad Request)} if the patient has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Patient> createPatient(@Valid @RequestBody Patient patient) throws URISyntaxException {
        LOG.debug("REST request to save Patient : {}", patient);
        if (patient.getId() != null) {
            throw new BadRequestAlertException("A new patient cannot already have an ID", ENTITY_NAME, "idexists");
        }
        patient = patientRepository.save(patient);
        return ResponseEntity
            .created(new URI("/api/patients/" + patient.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, patient.getId()))
            .body(patient);
    }

    /**
     * {@code PUT  /patients/:id} : Updates an existing patient.
     *
     * @param id the id of the patient to save.
     * @param patient the patient to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated patient,
     * or with status {@code 400 (Bad Request)} if the patient is not valid,
     * or with status {@code 500 (Internal Server Error)} if the patient couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatient(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody Patient patient
    ) throws URISyntaxException {
        LOG.debug("REST request to update Patient : {}, {}", id, patient);
        if (patient.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, patient.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!patientRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        patient = patientRepository.save(patient);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, patient.getId()))
            .body(patient);
    }

    /**
     * {@code PATCH  /patients/:id} : Partial updates given fields of an existing patient, field will ignore if it is null
     *
     * @param id the id of the patient to save.
     * @param patient the patient to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated patient,
     * or with status {@code 400 (Bad Request)} if the patient is not valid,
     * or with status {@code 404 (Not Found)} if the patient is not found,
     * or with status {@code 500 (Internal Server Error)} if the patient couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Patient> partialUpdatePatient(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody Patient patient
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Patient partially : {}, {}", id, patient);
        if (patient.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, patient.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!patientRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Patient> result = patientRepository
            .findById(patient.getId())
            .map(existingPatient -> {
                updateIfPresent(existingPatient::setStatus, patient.getStatus());
                updateIfPresent(existingPatient::setJoinedOn, patient.getJoinedOn());
                updateIfPresent(existingPatient::setLastActiveOn, patient.getLastActiveOn());
                updateIfPresent(existingPatient::setCaseCount, patient.getCaseCount());
                updateIfPresent(existingPatient::setIsArchived, patient.getIsArchived());

                return existingPatient;
            })
            .map(patientRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, patient.getId()));
    }

    /**
     * {@code GET  /patients} : get all the Patients.
     *
     * @param pageable the pagination information.
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @param isArchivedEquals when true, return only archived records; when false, only unarchived.
     * @param isArchivedNotEquals the inverse, sent by the console as {@code isArchived.notEquals=true}.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Patients in body.
     */
    @GetMapping("")
    public ResponseEntity<List<Patient>> getAllPatients(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload,
        @RequestParam(name = "isArchived.equals", required = false) Boolean isArchivedEquals,
        @RequestParam(name = "isArchived.notEquals", required = false) Boolean isArchivedNotEquals,
        // The directory tiles filter on these and read their counts from X-Total-Count. Undeclared,
        // Spring drops them and every tile reads the collection total.
        @RequestParam(name = "status.equals", required = false) AccountStatus statusEquals
    ) {
        LOG.debug("REST request to get a page of Patients");
        // The two operators the console sends, and only those. This is not a criteria framework:
        // every other entity here lists unfiltered, and inventing a general query language for one
        // boolean would be a much larger surface than the screen that needs it.
        Boolean archived = resolveArchivedFilter(isArchivedEquals, isArchivedNotEquals);

        // eagerload is not a distinction MongoDB makes here — findAllWithEagerRelationships is
        // literally @Query("{}") — so the filtered queries serve both branches.
        NamedFilters.Builder filters = NamedFilters.builder().equals("status", statusEquals);
        // Archived stays `$ne: true` rather than `is(false)`: a document written before the field
        // existed does not carry it, and `is_archived: false` matches none of them.
        if (archived != null) {
            if (archived) {
                filters.equals("is_archived", true);
            } else {
                filters.notEquals("is_archived", true);
            }
        }

        Page<Patient> page;
        if (filters.isEmpty()) {
            page = eagerload ? patientRepository.findAllWithEagerRelationships(pageable) : patientRepository.findAll(pageable);
        } else {
            page = NamedFilters.page(mongoTemplate, Patient.class, filters, pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /patients/:id} : get the "id" patient.
     *
     * @param id the id of the patient to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the patient, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatient(@PathVariable("id") String id) {
        LOG.debug("REST request to get Patient : {}", id);
        Optional<Patient> patient = patientRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(patient);
    }

    /**
     * {@code DELETE  /patients/:id} : delete the "id" patient.
     *
     * @param id the id of the patient to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePatient(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Patient : {}", id);
        patientRepository.deleteById(id);
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
