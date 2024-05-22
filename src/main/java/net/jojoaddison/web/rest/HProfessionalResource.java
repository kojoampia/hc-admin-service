package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.HProfessional;
import net.jojoaddison.repository.HProfessionalRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.HProfessional}.
 */
@RestController
@RequestMapping("/api/h-professionals")
public class HProfessionalResource {

    private final Logger log = LoggerFactory.getLogger(HProfessionalResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceHProfessional";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final HProfessionalRepository hProfessionalRepository;

    public HProfessionalResource(HProfessionalRepository hProfessionalRepository) {
        this.hProfessionalRepository = hProfessionalRepository;
    }

    /**
     * {@code POST  /h-professionals} : Create a new hProfessional.
     *
     * @param hProfessional the hProfessional to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new hProfessional, or with status {@code 400 (Bad Request)} if the hProfessional has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<HProfessional> createHProfessional(@RequestBody HProfessional hProfessional) throws URISyntaxException {
        log.debug("REST request to save HProfessional : {}", hProfessional);
        if (hProfessional.getId() != null) {
            throw new BadRequestAlertException("A new hProfessional cannot already have an ID", ENTITY_NAME, "idexists");
        }
        HProfessional result = hProfessionalRepository.save(hProfessional);
        return ResponseEntity
            .created(new URI("/api/h-professionals/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /h-professionals/:id} : Updates an existing hProfessional.
     *
     * @param id the id of the hProfessional to save.
     * @param hProfessional the hProfessional to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated hProfessional,
     * or with status {@code 400 (Bad Request)} if the hProfessional is not valid,
     * or with status {@code 500 (Internal Server Error)} if the hProfessional couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<HProfessional> updateHProfessional(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody HProfessional hProfessional
    ) throws URISyntaxException {
        log.debug("REST request to update HProfessional : {}, {}", id, hProfessional);
        if (hProfessional.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hProfessional.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hProfessionalRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        HProfessional result = hProfessionalRepository.save(hProfessional);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hProfessional.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /h-professionals/:id} : Partial updates given fields of an existing hProfessional, field will ignore if it is null
     *
     * @param id the id of the hProfessional to save.
     * @param hProfessional the hProfessional to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated hProfessional,
     * or with status {@code 400 (Bad Request)} if the hProfessional is not valid,
     * or with status {@code 404 (Not Found)} if the hProfessional is not found,
     * or with status {@code 500 (Internal Server Error)} if the hProfessional couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<HProfessional> partialUpdateHProfessional(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody HProfessional hProfessional
    ) throws URISyntaxException {
        log.debug("REST request to partial update HProfessional partially : {}, {}", id, hProfessional);
        if (hProfessional.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hProfessional.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hProfessionalRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<HProfessional> result = hProfessionalRepository
            .findById(hProfessional.getId())
            .map(existingHProfessional -> {
                if (hProfessional.getName() != null) {
                    existingHProfessional.setName(hProfessional.getName());
                }
                if (hProfessional.getOrganisation() != null) {
                    existingHProfessional.setOrganisation(hProfessional.getOrganisation());
                }
                if (hProfessional.getRoster() != null) {
                    existingHProfessional.setRoster(hProfessional.getRoster());
                }
                if (hProfessional.getCreatedDate() != null) {
                    existingHProfessional.setCreatedDate(hProfessional.getCreatedDate());
                }
                if (hProfessional.getCreatedBy() != null) {
                    existingHProfessional.setCreatedBy(hProfessional.getCreatedBy());
                }
                if (hProfessional.getModifiedDate() != null) {
                    existingHProfessional.setModifiedDate(hProfessional.getModifiedDate());
                }
                if (hProfessional.getModifiedBy() != null) {
                    existingHProfessional.setModifiedBy(hProfessional.getModifiedBy());
                }
                if (hProfessional.getProfile() != null) {
                    existingHProfessional.setProfile(hProfessional.getProfile());
                }

                return existingHProfessional;
            })
            .map(hProfessionalRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hProfessional.getId())
        );
    }

    /**
     * {@code GET  /h-professionals} : get all the hProfessionals.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of hProfessionals in body.
     */
    @GetMapping("")
    public List<HProfessional> getAllHProfessionals() {
        log.debug("REST request to get all HProfessionals");
        return hProfessionalRepository.findAll();
    }

    /**
     * {@code GET  /h-professionals/:id} : get the "id" hProfessional.
     *
     * @param id the id of the hProfessional to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the hProfessional, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<HProfessional> getHProfessional(@PathVariable("id") String id) {
        log.debug("REST request to get HProfessional : {}", id);
        Optional<HProfessional> hProfessional = hProfessionalRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(hProfessional);
    }

    /**
     * {@code DELETE  /h-professionals/:id} : delete the "id" hProfessional.
     *
     * @param id the id of the hProfessional to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHProfessional(@PathVariable("id") String id) {
        log.debug("REST request to delete HProfessional : {}", id);
        hProfessionalRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
