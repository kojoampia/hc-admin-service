package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.HCSubscription;
import net.jojoaddison.repository.HCSubscriptionRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.HCSubscription}.
 */
@RestController
@RequestMapping("/api/hc-subscriptions")
public class HCSubscriptionResource {

    private final Logger log = LoggerFactory.getLogger(HCSubscriptionResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceHcSubscription";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final HCSubscriptionRepository hCSubscriptionRepository;

    public HCSubscriptionResource(HCSubscriptionRepository hCSubscriptionRepository) {
        this.hCSubscriptionRepository = hCSubscriptionRepository;
    }

    /**
     * {@code POST  /hc-subscriptions} : Create a new hCSubscription.
     *
     * @param hCSubscription the hCSubscription to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new hCSubscription, or with status {@code 400 (Bad Request)}
     *         if the hCSubscription has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<HCSubscription> createHCSubscription(@RequestBody HCSubscription hCSubscription) throws URISyntaxException {
        log.debug("REST request to save HCSubscription : {}", hCSubscription);
        if (hCSubscription.getId() != null) {
            throw new BadRequestAlertException("A new hCSubscription cannot already have an ID", ENTITY_NAME, "idexists");
        }
        HCSubscription result = hCSubscriptionRepository.save(hCSubscription);
        return ResponseEntity
            .created(new URI("/api/hc-subscriptions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /hc-subscriptions/:id} : Updates an existing hCSubscription.
     *
     * @param id             the id of the hCSubscription to save.
     * @param hCSubscription the hCSubscription to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated hCSubscription,
     *         or with status {@code 400 (Bad Request)} if the hCSubscription is not
     *         valid,
     *         or with status {@code 500 (Internal Server Error)} if the
     *         hCSubscription couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<HCSubscription> updateHCSubscription(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody HCSubscription hCSubscription
    ) throws URISyntaxException {
        log.debug("REST request to update HCSubscription : {}, {}", id, hCSubscription);
        if (hCSubscription.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hCSubscription.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hCSubscriptionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        HCSubscription result = hCSubscriptionRepository.save(hCSubscription);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hCSubscription.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /hc-subscriptions/:id} : Partial updates given fields of an
     * existing hCSubscription, field will ignore if it is null
     *
     * @param id             the id of the hCSubscription to save.
     * @param hCSubscription the hCSubscription to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated hCSubscription,
     *         or with status {@code 400 (Bad Request)} if the hCSubscription is not
     *         valid,
     *         or with status {@code 404 (Not Found)} if the hCSubscription is not
     *         found,
     *         or with status {@code 500 (Internal Server Error)} if the
     *         hCSubscription couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<HCSubscription> partialUpdateHCSubscription(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody HCSubscription hCSubscription
    ) throws URISyntaxException {
        log.debug("REST request to partial update HCSubscription partially : {}, {}", id, hCSubscription);
        if (hCSubscription.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, hCSubscription.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!hCSubscriptionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<HCSubscription> result = hCSubscriptionRepository
            .findById(hCSubscription.getId())
            .map(existingHCSubscription -> {
                if (hCSubscription.getServiceId() != null) {
                    existingHCSubscription.setServiceId(hCSubscription.getServiceId());
                }
                if (hCSubscription.getAdminId() != null) {
                    existingHCSubscription.setAdminId(hCSubscription.getAdminId());
                }
                if (hCSubscription.getIsActive() != null) {
                    existingHCSubscription.setIsActive(hCSubscription.getIsActive());
                }
                if (hCSubscription.getCreatedDate() != null) {
                    existingHCSubscription.setCreatedDate(hCSubscription.getCreatedDate());
                }
                if (hCSubscription.getModifiedDate() != null) {
                    existingHCSubscription.setModifiedDate(hCSubscription.getModifiedDate());
                }
                if (hCSubscription.getCreatedBy() != null) {
                    existingHCSubscription.setCreatedBy(hCSubscription.getCreatedBy());
                }
                if (hCSubscription.getModifiedBy() != null) {
                    existingHCSubscription.setModifiedBy(hCSubscription.getModifiedBy());
                }

                return existingHCSubscription;
            })
            .map(hCSubscriptionRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, hCSubscription.getId())
        );
    }

    /**
     * {@code GET  /hc-subscriptions} : get all the hCSubscriptions.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of hCSubscriptions in body.
     */
    @GetMapping("")
    public List<HCSubscription> getAllHCSubscriptions() {
        log.debug("REST request to get all HCSubscriptions");
        return hCSubscriptionRepository.findAll();
    }

    /**
     * {@code GET  /hc-subscriptions/:id} : get the "id" hCSubscription.
     *
     * @param id the id of the hCSubscription to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the hCSubscription, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<HCSubscription> getHCSubscription(@PathVariable("id") String id) {
        log.debug("REST request to get HCSubscription : {}", id);
        Optional<HCSubscription> hCSubscription = hCSubscriptionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(hCSubscription);
    }

    /**
     * {@code DELETE  /hc-subscriptions/:id} : delete the "id" hCSubscription.
     *
     * @param id the id of the hCSubscription to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHCSubscription(@PathVariable("id") String id) {
        log.debug("REST request to delete HCSubscription : {}", id);
        hCSubscriptionRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
