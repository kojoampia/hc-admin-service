package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.PricingPlan;
import net.jojoaddison.repository.PricingPlanRepository;
import net.jojoaddison.service.dto.PricingPlanDTO;
import net.jojoaddison.service.mapper.PricingPlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.PricingPlan}.
 */
@Service
public class PricingPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(PricingPlanService.class);

    private final PricingPlanRepository pricingPlanRepository;

    private final PricingPlanMapper pricingPlanMapper;

    public PricingPlanService(PricingPlanRepository pricingPlanRepository, PricingPlanMapper pricingPlanMapper) {
        this.pricingPlanRepository = pricingPlanRepository;
        this.pricingPlanMapper = pricingPlanMapper;
    }

    /**
     * Save a pricingPlan.
     *
     * @param pricingPlanDTO the entity to save.
     * @return the persisted entity.
     */
    public PricingPlanDTO save(PricingPlanDTO pricingPlanDTO) {
        LOG.debug("Request to save PricingPlan : {}", pricingPlanDTO);
        PricingPlan pricingPlan = pricingPlanMapper.toEntity(pricingPlanDTO);
        pricingPlan = pricingPlanRepository.save(pricingPlan);
        return pricingPlanMapper.toDto(pricingPlan);
    }

    /**
     * Update a pricingPlan.
     *
     * @param pricingPlanDTO the entity to save.
     * @return the persisted entity.
     */
    public PricingPlanDTO update(PricingPlanDTO pricingPlanDTO) {
        LOG.debug("Request to update PricingPlan : {}", pricingPlanDTO);
        PricingPlan pricingPlan = pricingPlanMapper.toEntity(pricingPlanDTO);
        pricingPlan = pricingPlanRepository.save(pricingPlan);
        return pricingPlanMapper.toDto(pricingPlan);
    }

    /**
     * Partially update a pricingPlan.
     *
     * @param pricingPlanDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<PricingPlanDTO> partialUpdate(PricingPlanDTO pricingPlanDTO) {
        LOG.debug("Request to partially update PricingPlan : {}", pricingPlanDTO);

        return pricingPlanRepository
            .findById(pricingPlanDTO.getId())
            .map(existingPricingPlan -> {
                pricingPlanMapper.partialUpdate(existingPricingPlan, pricingPlanDTO);

                return existingPricingPlan;
            })
            .map(pricingPlanRepository::save)
            .map(pricingPlanMapper::toDto);
    }

    /**
     * Get all the pricingPlans.
     *
     * @return the list of entities.
     */
    public List<PricingPlanDTO> findAll() {
        LOG.debug("Request to get all PricingPlans");
        return pricingPlanRepository.findAll().stream().map(pricingPlanMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one pricingPlan by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<PricingPlanDTO> findOne(String id) {
        LOG.debug("Request to get PricingPlan : {}", id);
        return pricingPlanRepository.findById(id).map(pricingPlanMapper::toDto);
    }

    /**
     * Delete the pricingPlan by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete PricingPlan : {}", id);
        pricingPlanRepository.deleteById(id);
    }
}
