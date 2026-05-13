package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.PatientPlan;
import net.jojoaddison.repository.PatientPlanRepository;
import net.jojoaddison.service.dto.PatientPlanDTO;
import net.jojoaddison.service.mapper.PatientPlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.PatientPlan}.
 */
@Service
public class PatientPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPlanService.class);

    private final PatientPlanRepository patientPlanRepository;

    private final PatientPlanMapper patientPlanMapper;

    public PatientPlanService(PatientPlanRepository patientPlanRepository, PatientPlanMapper patientPlanMapper) {
        this.patientPlanRepository = patientPlanRepository;
        this.patientPlanMapper = patientPlanMapper;
    }

    /**
     * Save a patientPlan.
     *
     * @param patientPlanDTO the entity to save.
     * @return the persisted entity.
     */
    public PatientPlanDTO save(PatientPlanDTO patientPlanDTO) {
        LOG.debug("Request to save PatientPlan : {}", patientPlanDTO);
        PatientPlan patientPlan = patientPlanMapper.toEntity(patientPlanDTO);
        patientPlan = patientPlanRepository.save(patientPlan);
        return patientPlanMapper.toDto(patientPlan);
    }

    /**
     * Update a patientPlan.
     *
     * @param patientPlanDTO the entity to save.
     * @return the persisted entity.
     */
    public PatientPlanDTO update(PatientPlanDTO patientPlanDTO) {
        LOG.debug("Request to update PatientPlan : {}", patientPlanDTO);
        PatientPlan patientPlan = patientPlanMapper.toEntity(patientPlanDTO);
        patientPlan = patientPlanRepository.save(patientPlan);
        return patientPlanMapper.toDto(patientPlan);
    }

    /**
     * Partially update a patientPlan.
     *
     * @param patientPlanDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<PatientPlanDTO> partialUpdate(PatientPlanDTO patientPlanDTO) {
        LOG.debug("Request to partially update PatientPlan : {}", patientPlanDTO);

        return patientPlanRepository
            .findById(patientPlanDTO.getId())
            .map(existingPatientPlan -> {
                patientPlanMapper.partialUpdate(existingPatientPlan, patientPlanDTO);

                return existingPatientPlan;
            })
            .map(patientPlanRepository::save)
            .map(patientPlanMapper::toDto);
    }

    /**
     * Get all the patientPlans.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<PatientPlanDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all PatientPlans");
        return patientPlanRepository.findAll(pageable).map(patientPlanMapper::toDto);
    }

    /**
     * Get one patientPlan by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<PatientPlanDTO> findOne(String id) {
        LOG.debug("Request to get PatientPlan : {}", id);
        return patientPlanRepository.findById(id).map(patientPlanMapper::toDto);
    }

    /**
     * Delete the patientPlan by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete PatientPlan : {}", id);
        patientPlanRepository.deleteById(id);
    }
}
