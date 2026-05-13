package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Facility;
import net.jojoaddison.repository.FacilityRepository;
import net.jojoaddison.service.dto.FacilityDTO;
import net.jojoaddison.service.mapper.FacilityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Facility}.
 */
@Service
public class FacilityService {

    private static final Logger LOG = LoggerFactory.getLogger(FacilityService.class);

    private final FacilityRepository facilityRepository;

    private final FacilityMapper facilityMapper;

    public FacilityService(FacilityRepository facilityRepository, FacilityMapper facilityMapper) {
        this.facilityRepository = facilityRepository;
        this.facilityMapper = facilityMapper;
    }

    /**
     * Save a facility.
     *
     * @param facilityDTO the entity to save.
     * @return the persisted entity.
     */
    public FacilityDTO save(FacilityDTO facilityDTO) {
        LOG.debug("Request to save Facility : {}", facilityDTO);
        Facility facility = facilityMapper.toEntity(facilityDTO);
        facility = facilityRepository.save(facility);
        return facilityMapper.toDto(facility);
    }

    /**
     * Update a facility.
     *
     * @param facilityDTO the entity to save.
     * @return the persisted entity.
     */
    public FacilityDTO update(FacilityDTO facilityDTO) {
        LOG.debug("Request to update Facility : {}", facilityDTO);
        Facility facility = facilityMapper.toEntity(facilityDTO);
        facility = facilityRepository.save(facility);
        return facilityMapper.toDto(facility);
    }

    /**
     * Partially update a facility.
     *
     * @param facilityDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FacilityDTO> partialUpdate(FacilityDTO facilityDTO) {
        LOG.debug("Request to partially update Facility : {}", facilityDTO);

        return facilityRepository
            .findById(facilityDTO.getId())
            .map(existingFacility -> {
                facilityMapper.partialUpdate(existingFacility, facilityDTO);

                return existingFacility;
            })
            .map(facilityRepository::save)
            .map(facilityMapper::toDto);
    }

    /**
     * Get all the facilities.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<FacilityDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Facilities");
        return facilityRepository.findAll(pageable).map(facilityMapper::toDto);
    }

    /**
     * Get one facility by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<FacilityDTO> findOne(String id) {
        LOG.debug("Request to get Facility : {}", id);
        return facilityRepository.findById(id).map(facilityMapper::toDto);
    }

    /**
     * Delete the facility by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Facility : {}", id);
        facilityRepository.deleteById(id);
    }
}
