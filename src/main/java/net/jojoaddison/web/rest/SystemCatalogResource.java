package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.SystemCatalogRepository;
import net.jojoaddison.service.SystemCatalogService;
import net.jojoaddison.service.dto.SystemCatalogDTO;
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
 * REST controller for managing {@link net.jojoaddison.domain.SystemCatalog}.
 */
@RestController
@RequestMapping("/api/system-catalogs")
public class SystemCatalogResource {

    private static final Logger LOG = LoggerFactory.getLogger(SystemCatalogResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceSystemCatalog";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SystemCatalogService systemCatalogService;

    private final SystemCatalogRepository systemCatalogRepository;

    public SystemCatalogResource(SystemCatalogService systemCatalogService, SystemCatalogRepository systemCatalogRepository) {
        this.systemCatalogService = systemCatalogService;
        this.systemCatalogRepository = systemCatalogRepository;
    }

    /**
     * {@code POST  /system-catalogs} : Create a new systemCatalog.
     *
     * @param systemCatalogDTO the systemCatalogDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new systemCatalogDTO, or with status {@code 400 (Bad Request)} if the systemCatalog has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<SystemCatalogDTO> createSystemCatalog(@Valid @RequestBody SystemCatalogDTO systemCatalogDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save SystemCatalog : {}", systemCatalogDTO);
        if (systemCatalogDTO.getId() != null) {
            throw new BadRequestAlertException("A new systemCatalog cannot already have an ID", ENTITY_NAME, "idexists");
        }
        systemCatalogDTO = systemCatalogService.save(systemCatalogDTO);
        return ResponseEntity
            .created(new URI("/api/system-catalogs/" + systemCatalogDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, systemCatalogDTO.getId()))
            .body(systemCatalogDTO);
    }

    /**
     * {@code PUT  /system-catalogs/:id} : Updates an existing systemCatalog.
     *
     * @param id the id of the systemCatalogDTO to save.
     * @param systemCatalogDTO the systemCatalogDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated systemCatalogDTO,
     * or with status {@code 400 (Bad Request)} if the systemCatalogDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the systemCatalogDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<SystemCatalogDTO> updateSystemCatalog(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody SystemCatalogDTO systemCatalogDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update SystemCatalog : {}, {}", id, systemCatalogDTO);
        if (systemCatalogDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, systemCatalogDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!systemCatalogRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        systemCatalogDTO = systemCatalogService.update(systemCatalogDTO);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, systemCatalogDTO.getId()))
            .body(systemCatalogDTO);
    }

    /**
     * {@code PATCH  /system-catalogs/:id} : Partial updates given fields of an existing systemCatalog, field will ignore if it is null
     *
     * @param id the id of the systemCatalogDTO to save.
     * @param systemCatalogDTO the systemCatalogDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated systemCatalogDTO,
     * or with status {@code 400 (Bad Request)} if the systemCatalogDTO is not valid,
     * or with status {@code 404 (Not Found)} if the systemCatalogDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the systemCatalogDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<SystemCatalogDTO> partialUpdateSystemCatalog(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody SystemCatalogDTO systemCatalogDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update SystemCatalog partially : {}, {}", id, systemCatalogDTO);
        if (systemCatalogDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, systemCatalogDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!systemCatalogRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<SystemCatalogDTO> result = systemCatalogService.partialUpdate(systemCatalogDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, systemCatalogDTO.getId())
        );
    }

    /**
     * {@code GET  /system-catalogs} : get all the systemCatalogs.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of systemCatalogs in body.
     */
    @GetMapping("")
    public ResponseEntity<List<SystemCatalogDTO>> getAllSystemCatalogs(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of SystemCatalogs");
        Page<SystemCatalogDTO> page = systemCatalogService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /system-catalogs/:id} : get the "id" systemCatalog.
     *
     * @param id the id of the systemCatalogDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the systemCatalogDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SystemCatalogDTO> getSystemCatalog(@PathVariable("id") String id) {
        LOG.debug("REST request to get SystemCatalog : {}", id);
        Optional<SystemCatalogDTO> systemCatalogDTO = systemCatalogService.findOne(id);
        return ResponseUtil.wrapOrNotFound(systemCatalogDTO);
    }

    /**
     * {@code DELETE  /system-catalogs/:id} : delete the "id" systemCatalog.
     *
     * @param id the id of the systemCatalogDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSystemCatalog(@PathVariable("id") String id) {
        LOG.debug("REST request to delete SystemCatalog : {}", id);
        systemCatalogService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
