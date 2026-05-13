package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.SystemCatalog;
import net.jojoaddison.repository.SystemCatalogRepository;
import net.jojoaddison.service.dto.SystemCatalogDTO;
import net.jojoaddison.service.mapper.SystemCatalogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.SystemCatalog}.
 */
@Service
public class SystemCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(SystemCatalogService.class);

    private final SystemCatalogRepository systemCatalogRepository;

    private final SystemCatalogMapper systemCatalogMapper;

    public SystemCatalogService(SystemCatalogRepository systemCatalogRepository, SystemCatalogMapper systemCatalogMapper) {
        this.systemCatalogRepository = systemCatalogRepository;
        this.systemCatalogMapper = systemCatalogMapper;
    }

    /**
     * Save a systemCatalog.
     *
     * @param systemCatalogDTO the entity to save.
     * @return the persisted entity.
     */
    public SystemCatalogDTO save(SystemCatalogDTO systemCatalogDTO) {
        LOG.debug("Request to save SystemCatalog : {}", systemCatalogDTO);
        SystemCatalog systemCatalog = systemCatalogMapper.toEntity(systemCatalogDTO);
        systemCatalog = systemCatalogRepository.save(systemCatalog);
        return systemCatalogMapper.toDto(systemCatalog);
    }

    /**
     * Update a systemCatalog.
     *
     * @param systemCatalogDTO the entity to save.
     * @return the persisted entity.
     */
    public SystemCatalogDTO update(SystemCatalogDTO systemCatalogDTO) {
        LOG.debug("Request to update SystemCatalog : {}", systemCatalogDTO);
        SystemCatalog systemCatalog = systemCatalogMapper.toEntity(systemCatalogDTO);
        systemCatalog = systemCatalogRepository.save(systemCatalog);
        return systemCatalogMapper.toDto(systemCatalog);
    }

    /**
     * Partially update a systemCatalog.
     *
     * @param systemCatalogDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SystemCatalogDTO> partialUpdate(SystemCatalogDTO systemCatalogDTO) {
        LOG.debug("Request to partially update SystemCatalog : {}", systemCatalogDTO);

        return systemCatalogRepository
            .findById(systemCatalogDTO.getId())
            .map(existingSystemCatalog -> {
                systemCatalogMapper.partialUpdate(existingSystemCatalog, systemCatalogDTO);

                return existingSystemCatalog;
            })
            .map(systemCatalogRepository::save)
            .map(systemCatalogMapper::toDto);
    }

    /**
     * Get all the systemCatalogs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<SystemCatalogDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all SystemCatalogs");
        return systemCatalogRepository.findAll(pageable).map(systemCatalogMapper::toDto);
    }

    /**
     * Get one systemCatalog by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<SystemCatalogDTO> findOne(String id) {
        LOG.debug("Request to get SystemCatalog : {}", id);
        return systemCatalogRepository.findById(id).map(systemCatalogMapper::toDto);
    }

    /**
     * Delete the systemCatalog by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete SystemCatalog : {}", id);
        systemCatalogRepository.deleteById(id);
    }
}
