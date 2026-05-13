package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.service.dto.DutyRosterDTO;
import net.jojoaddison.service.mapper.DutyRosterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.DutyRoster}.
 */
@Service
public class DutyRosterService {

    private static final Logger LOG = LoggerFactory.getLogger(DutyRosterService.class);

    private final DutyRosterRepository dutyRosterRepository;

    private final DutyRosterMapper dutyRosterMapper;

    public DutyRosterService(DutyRosterRepository dutyRosterRepository, DutyRosterMapper dutyRosterMapper) {
        this.dutyRosterRepository = dutyRosterRepository;
        this.dutyRosterMapper = dutyRosterMapper;
    }

    /**
     * Save a dutyRoster.
     *
     * @param dutyRosterDTO the entity to save.
     * @return the persisted entity.
     */
    public DutyRosterDTO save(DutyRosterDTO dutyRosterDTO) {
        LOG.debug("Request to save DutyRoster : {}", dutyRosterDTO);
        DutyRoster dutyRoster = dutyRosterMapper.toEntity(dutyRosterDTO);
        dutyRoster = dutyRosterRepository.save(dutyRoster);
        return dutyRosterMapper.toDto(dutyRoster);
    }

    /**
     * Update a dutyRoster.
     *
     * @param dutyRosterDTO the entity to save.
     * @return the persisted entity.
     */
    public DutyRosterDTO update(DutyRosterDTO dutyRosterDTO) {
        LOG.debug("Request to update DutyRoster : {}", dutyRosterDTO);
        DutyRoster dutyRoster = dutyRosterMapper.toEntity(dutyRosterDTO);
        dutyRoster = dutyRosterRepository.save(dutyRoster);
        return dutyRosterMapper.toDto(dutyRoster);
    }

    /**
     * Partially update a dutyRoster.
     *
     * @param dutyRosterDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DutyRosterDTO> partialUpdate(DutyRosterDTO dutyRosterDTO) {
        LOG.debug("Request to partially update DutyRoster : {}", dutyRosterDTO);

        return dutyRosterRepository
            .findById(dutyRosterDTO.getId())
            .map(existingDutyRoster -> {
                dutyRosterMapper.partialUpdate(existingDutyRoster, dutyRosterDTO);

                return existingDutyRoster;
            })
            .map(dutyRosterRepository::save)
            .map(dutyRosterMapper::toDto);
    }

    /**
     * Get all the dutyRosters.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<DutyRosterDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DutyRosters");
        return dutyRosterRepository.findAll(pageable).map(dutyRosterMapper::toDto);
    }

    /**
     * Get one dutyRoster by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<DutyRosterDTO> findOne(String id) {
        LOG.debug("Request to get DutyRoster : {}", id);
        return dutyRosterRepository.findById(id).map(dutyRosterMapper::toDto);
    }

    /**
     * Delete the dutyRoster by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DutyRoster : {}", id);
        dutyRosterRepository.deleteById(id);
    }
}
