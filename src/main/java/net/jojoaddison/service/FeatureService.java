package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Feature;
import net.jojoaddison.repository.FeatureRepository;
import net.jojoaddison.service.dto.FeatureDTO;
import net.jojoaddison.service.mapper.FeatureMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Feature}.
 */
@Service
public class FeatureService {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureService.class);

    private final FeatureRepository featureRepository;

    private final FeatureMapper featureMapper;

    public FeatureService(FeatureRepository featureRepository, FeatureMapper featureMapper) {
        this.featureRepository = featureRepository;
        this.featureMapper = featureMapper;
    }

    /**
     * Save a feature.
     *
     * @param featureDTO the entity to save.
     * @return the persisted entity.
     */
    public FeatureDTO save(FeatureDTO featureDTO) {
        LOG.debug("Request to save Feature : {}", featureDTO);
        Feature feature = featureMapper.toEntity(featureDTO);
        feature = featureRepository.save(feature);
        return featureMapper.toDto(feature);
    }

    /**
     * Update a feature.
     *
     * @param featureDTO the entity to save.
     * @return the persisted entity.
     */
    public FeatureDTO update(FeatureDTO featureDTO) {
        LOG.debug("Request to update Feature : {}", featureDTO);
        Feature feature = featureMapper.toEntity(featureDTO);
        feature = featureRepository.save(feature);
        return featureMapper.toDto(feature);
    }

    /**
     * Partially update a feature.
     *
     * @param featureDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FeatureDTO> partialUpdate(FeatureDTO featureDTO) {
        LOG.debug("Request to partially update Feature : {}", featureDTO);

        return featureRepository
            .findById(featureDTO.getId())
            .map(existingFeature -> {
                featureMapper.partialUpdate(existingFeature, featureDTO);

                return existingFeature;
            })
            .map(featureRepository::save)
            .map(featureMapper::toDto);
    }

    /**
     * Get all the features.
     *
     * @return the list of entities.
     */
    public List<FeatureDTO> findAll() {
        LOG.debug("Request to get all Features");
        return featureRepository.findAll().stream().map(featureMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one feature by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<FeatureDTO> findOne(String id) {
        LOG.debug("Request to get Feature : {}", id);
        return featureRepository.findById(id).map(featureMapper::toDto);
    }

    /**
     * Delete the feature by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Feature : {}", id);
        featureRepository.deleteById(id);
    }
}
