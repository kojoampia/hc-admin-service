package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.DocumentItemRepository;
import net.jojoaddison.service.DocumentItemService;
import net.jojoaddison.service.dto.DocumentItemDTO;
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
 * REST controller for managing {@link net.jojoaddison.domain.DocumentItem}.
 */
@RestController
@RequestMapping("/api/document-items")
public class DocumentItemResource {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentItemResource.class);

    private static final String ENTITY_NAME = "hcAdminServiceDocumentItem";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DocumentItemService documentItemService;

    private final DocumentItemRepository documentItemRepository;

    public DocumentItemResource(DocumentItemService documentItemService, DocumentItemRepository documentItemRepository) {
        this.documentItemService = documentItemService;
        this.documentItemRepository = documentItemRepository;
    }

    /**
     * {@code POST  /document-items} : Create a new documentItem.
     *
     * @param documentItemDTO the documentItemDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new documentItemDTO, or with status {@code 400 (Bad Request)} if the documentItem has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<DocumentItemDTO> createDocumentItem(@Valid @RequestBody DocumentItemDTO documentItemDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save DocumentItem : {}", documentItemDTO);
        if (documentItemDTO.getId() != null) {
            throw new BadRequestAlertException("A new documentItem cannot already have an ID", ENTITY_NAME, "idexists");
        }
        documentItemDTO = documentItemService.save(documentItemDTO);
        return ResponseEntity
            .created(new URI("/api/document-items/" + documentItemDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, documentItemDTO.getId()))
            .body(documentItemDTO);
    }

    /**
     * {@code PUT  /document-items/:id} : Updates an existing documentItem.
     *
     * @param id the id of the documentItemDTO to save.
     * @param documentItemDTO the documentItemDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated documentItemDTO,
     * or with status {@code 400 (Bad Request)} if the documentItemDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the documentItemDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentItemDTO> updateDocumentItem(
        @PathVariable(value = "id", required = false) final String id,
        @Valid @RequestBody DocumentItemDTO documentItemDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DocumentItem : {}, {}", id, documentItemDTO);
        if (documentItemDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, documentItemDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!documentItemRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        documentItemDTO = documentItemService.update(documentItemDTO);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, documentItemDTO.getId()))
            .body(documentItemDTO);
    }

    /**
     * {@code PATCH  /document-items/:id} : Partial updates given fields of an existing documentItem, field will ignore if it is null
     *
     * @param id the id of the documentItemDTO to save.
     * @param documentItemDTO the documentItemDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated documentItemDTO,
     * or with status {@code 400 (Bad Request)} if the documentItemDTO is not valid,
     * or with status {@code 404 (Not Found)} if the documentItemDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the documentItemDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DocumentItemDTO> partialUpdateDocumentItem(
        @PathVariable(value = "id", required = false) final String id,
        @NotNull @RequestBody DocumentItemDTO documentItemDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DocumentItem partially : {}, {}", id, documentItemDTO);
        if (documentItemDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, documentItemDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!documentItemRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DocumentItemDTO> result = documentItemService.partialUpdate(documentItemDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, documentItemDTO.getId())
        );
    }

    /**
     * {@code GET  /document-items} : get all the documentItems.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of documentItems in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DocumentItemDTO>> getAllDocumentItems(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of DocumentItems");
        Page<DocumentItemDTO> page = documentItemService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /document-items/:id} : get the "id" documentItem.
     *
     * @param id the id of the documentItemDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the documentItemDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentItemDTO> getDocumentItem(@PathVariable("id") String id) {
        LOG.debug("REST request to get DocumentItem : {}", id);
        Optional<DocumentItemDTO> documentItemDTO = documentItemService.findOne(id);
        return ResponseUtil.wrapOrNotFound(documentItemDTO);
    }

    /**
     * {@code DELETE  /document-items/:id} : delete the "id" documentItem.
     *
     * @param id the id of the documentItemDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocumentItem(@PathVariable("id") String id) {
        LOG.debug("REST request to delete DocumentItem : {}", id);
        documentItemService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
