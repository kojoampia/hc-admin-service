package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.UserOption;
import net.jojoaddison.repository.UserOptionRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.UserOption}.
 */
@RestController
@RequestMapping("/api/user-options")
public class UserOptionResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserOptionResource.class);

    private static final String ENTITY_NAME = "platformUserOption";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final UserOptionRepository userOptionRepository;

    public UserOptionResource(UserOptionRepository userOptionRepository) {
        this.userOptionRepository = userOptionRepository;
    }

    /**
     * {@code POST  /user-options} : Create a new userOption.
     *
     * @param userOption the userOption to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new userOption, or with status {@code 400 (Bad Request)} if the userOption has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<UserOption> createUserOption(@Valid @RequestBody UserOption userOption) throws URISyntaxException {
        LOG.debug("REST request to save UserOption : {}", userOption);
        if (userOption.getId() != null) {
            throw new BadRequestAlertException("A new userOption cannot already have an ID", ENTITY_NAME, "idexists");
        }
        userOption = userOptionRepository.save(userOption);
        return ResponseEntity
            .created(new URI("/api/user-options/" + userOption.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, userOption.getId()))
            .body(userOption);
    }

    /**
     * {@code PUT  /user-options/:id} : Updates an existing userOption.
     *
     * @param id the id of the userOption to save.
     * @param userOption the userOption to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated userOption,
     * or with status {@code 400 (Bad Request)} if the userOption is not valid,
     * or with status {@code 500 (Internal Server Error)} if the userOption couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserOption> updateUserOption(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody UserOption userOption
    ) throws URISyntaxException {
        LOG.debug("REST request to update UserOption : {}, {}", id, userOption);
        if (userOption.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, userOption.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!userOptionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        userOption = userOptionRepository.save(userOption);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, userOption.getId()))
            .body(userOption);
    }

    /**
     * {@code PATCH  /user-options/:id} : Partial updates given fields of an existing userOption, field will ignore if it is null
     *
     * @param id the id of the userOption to save.
     * @param userOption the userOption to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated userOption,
     * or with status {@code 400 (Bad Request)} if the userOption is not valid,
     * or with status {@code 404 (Not Found)} if the userOption is not found,
     * or with status {@code 500 (Internal Server Error)} if the userOption couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<UserOption> partialUpdateUserOption(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody UserOption userOption
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update UserOption partially : {}, {}", id, userOption);
        if (userOption.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, userOption.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!userOptionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<UserOption> result = userOptionRepository
            .findById(userOption.getId())
            .map(existingUserOption -> {
                updateIfPresent(existingUserOption::setCategory, userOption.getCategory());
                updateIfPresent(existingUserOption::setUserRef, userOption.getUserRef());
                updateIfPresent(existingUserOption::setMetadata, userOption.getMetadata());

                return existingUserOption;
            })
            .map(userOptionRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, userOption.getId())
        );
    }

    /**
     * {@code GET  /user-options} : get all the User Options.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of User Options in body.
     */
    @GetMapping("")
    public List<UserOption> getAllUserOptions() {
        LOG.debug("REST request to get all UserOptions");
        return userOptionRepository.findAll();
    }

    /**
     * {@code GET  /user-options/:id} : get the "id" userOption.
     *
     * @param id the id of the userOption to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the userOption, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserOption> getUserOption(@PathVariable("id") String id) {
        LOG.debug("REST request to get UserOption : {}", id);
        Optional<UserOption> userOption = userOptionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(userOption);
    }

    /**
     * {@code DELETE  /user-options/:id} : delete the "id" userOption.
     *
     * @param id the id of the userOption to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserOption(@PathVariable("id") String id) {
        LOG.debug("REST request to delete UserOption : {}", id);
        userOptionRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
