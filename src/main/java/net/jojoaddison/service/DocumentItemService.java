package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.DocumentItem;
import net.jojoaddison.repository.DocumentItemRepository;
import net.jojoaddison.service.dto.DocumentItemDTO;
import net.jojoaddison.service.mapper.DocumentItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.DocumentItem}.
 */
@Service
public class DocumentItemService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentItemService.class);

    private final DocumentItemRepository documentItemRepository;

    private final DocumentItemMapper documentItemMapper;

    public DocumentItemService(DocumentItemRepository documentItemRepository, DocumentItemMapper documentItemMapper) {
        this.documentItemRepository = documentItemRepository;
        this.documentItemMapper = documentItemMapper;
    }

    /**
     * Save a documentItem.
     *
     * @param documentItemDTO the entity to save.
     * @return the persisted entity.
     */
    public DocumentItemDTO save(DocumentItemDTO documentItemDTO) {
        LOG.debug("Request to save DocumentItem : {}", documentItemDTO);
        DocumentItem documentItem = documentItemMapper.toEntity(documentItemDTO);
        documentItem = documentItemRepository.save(documentItem);
        return documentItemMapper.toDto(documentItem);
    }

    /**
     * Update a documentItem.
     *
     * @param documentItemDTO the entity to save.
     * @return the persisted entity.
     */
    public DocumentItemDTO update(DocumentItemDTO documentItemDTO) {
        LOG.debug("Request to update DocumentItem : {}", documentItemDTO);
        DocumentItem documentItem = documentItemMapper.toEntity(documentItemDTO);
        documentItem = documentItemRepository.save(documentItem);
        return documentItemMapper.toDto(documentItem);
    }

    /**
     * Partially update a documentItem.
     *
     * @param documentItemDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DocumentItemDTO> partialUpdate(DocumentItemDTO documentItemDTO) {
        LOG.debug("Request to partially update DocumentItem : {}", documentItemDTO);

        return documentItemRepository
            .findById(documentItemDTO.getId())
            .map(existingDocumentItem -> {
                documentItemMapper.partialUpdate(existingDocumentItem, documentItemDTO);

                return existingDocumentItem;
            })
            .map(documentItemRepository::save)
            .map(documentItemMapper::toDto);
    }

    /**
     * Get all the documentItems.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<DocumentItemDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DocumentItems");
        return documentItemRepository.findAll(pageable).map(documentItemMapper::toDto);
    }

    /**
     * Get one documentItem by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<DocumentItemDTO> findOne(String id) {
        LOG.debug("Request to get DocumentItem : {}", id);
        return documentItemRepository.findById(id).map(documentItemMapper::toDto);
    }

    /**
     * Delete the documentItem by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DocumentItem : {}", id);
        documentItemRepository.deleteById(id);
    }
}
