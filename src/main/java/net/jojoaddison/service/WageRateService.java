package net.jojoaddison.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.repository.WageRateRepository;
import net.jojoaddison.service.dto.WageRateDTO;
import net.jojoaddison.service.mapper.WageRateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.WageRate}.
 *
 * <p>The interesting method here is {@link #rateOn(ProfessionalRole, LocalDate)} — everything else
 * is the ordinary CRUD contract.
 */
@Service
public class WageRateService {

    private static final Logger LOG = LoggerFactory.getLogger(WageRateService.class);

    private final WageRateRepository wageRateRepository;

    private final WageRateMapper wageRateMapper;

    public WageRateService(WageRateRepository wageRateRepository, WageRateMapper wageRateMapper) {
        this.wageRateRepository = wageRateRepository;
        this.wageRateMapper = wageRateMapper;
    }

    /**
     * The rate governing a shift worked by {@code role} on {@code date}: the row with the greatest
     * {@code validFrom} that is not after that date.
     *
     * <p>Empty means no rate had been configured yet when the shift was worked. That is deliberately
     * distinct from a rate of zero — the caller decides whether to report it as unpriced or to treat
     * it as nothing owed, and the console has to be able to tell those apart.
     */
    public Optional<WageRate> rateOn(ProfessionalRole role, LocalDate date) {
        return wageRateRepository.findByRoleAndValidFromLessThanEqualOrderByValidFromDesc(role, date).stream().findFirst();
    }

    /**
     * A resolver for valuing many shifts at once. Loads every rate in force on or before
     * {@code upTo} once, then answers from memory — the alternative is a query per shift, and a
     * month of roster for one professional is already tens of shifts.
     */
    public RateTable rateTableUpTo(LocalDate upTo) {
        Map<ProfessionalRole, List<WageRate>> byRole = new EnumMap<>(ProfessionalRole.class);
        for (WageRate rate : wageRateRepository.findByValidFromLessThanEqualOrderByValidFromDesc(upTo)) {
            byRole.computeIfAbsent(rate.getRole(), r -> new ArrayList<>()).add(rate);
        }
        // The derived query orders globally, not within a role, so sort each bucket explicitly
        // rather than trusting the order the driver happened to return.
        byRole.values().forEach(rates -> rates.sort(Comparator.comparing(WageRate::getValidFrom).reversed()));
        return new RateTable(byRole);
    }

    /**
     * Rates in force on or before a date, grouped by role, newest first. Resolution walks the list
     * for a role and takes the first entry not after the date asked about.
     */
    public record RateTable(Map<ProfessionalRole, List<WageRate>> byRole) {
        public Optional<WageRate> rateOn(ProfessionalRole role, LocalDate date) {
            if (role == null || date == null) {
                return Optional.empty();
            }
            return byRole.getOrDefault(role, List.of()).stream().filter(rate -> !rate.getValidFrom().isAfter(date)).findFirst();
        }

        public BigDecimal amountOn(ProfessionalRole role, LocalDate date) {
            return rateOn(role, date).map(WageRate::getAmount).orElse(BigDecimal.ZERO);
        }
    }

    /**
     * Save a wageRate.
     *
     * @param wageRateDTO the entity to save.
     * @return the persisted entity.
     */
    public WageRateDTO save(WageRateDTO wageRateDTO) {
        LOG.debug("Request to save WageRate : {}", wageRateDTO);
        WageRate wageRate = wageRateMapper.toEntity(wageRateDTO);
        wageRate = wageRateRepository.save(wageRate);
        return wageRateMapper.toDto(wageRate);
    }

    /**
     * Update a wageRate.
     *
     * @param wageRateDTO the entity to save.
     * @return the persisted entity.
     */
    public WageRateDTO update(WageRateDTO wageRateDTO) {
        LOG.debug("Request to update WageRate : {}", wageRateDTO);
        WageRate wageRate = wageRateMapper.toEntity(wageRateDTO);
        wageRate = wageRateRepository.save(wageRate);
        return wageRateMapper.toDto(wageRate);
    }

    /**
     * Partially update a wageRate.
     *
     * @param wageRateDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<WageRateDTO> partialUpdate(WageRateDTO wageRateDTO) {
        LOG.debug("Request to partially update WageRate : {}", wageRateDTO);

        return wageRateRepository
            .findById(wageRateDTO.getId())
            .map(existingWageRate -> {
                wageRateMapper.partialUpdate(existingWageRate, wageRateDTO);

                return existingWageRate;
            })
            .map(wageRateRepository::save)
            .map(wageRateMapper::toDto);
    }

    /**
     * Get all the wageRates.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<WageRateDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get a page of WageRates");
        return wageRateRepository.findAll(pageable).map(wageRateMapper::toDto);
    }

    /**
     * The rate currently in force for each role — one row per role, which is what the configuration
     * screen leads with. Roles with no rate configured are absent rather than zero.
     */
    public List<WageRateDTO> currentRates(LocalDate asOf) {
        LOG.debug("Request to get the rates in force on {}", asOf);
        RateTable table = rateTableUpTo(asOf);
        List<WageRateDTO> current = new ArrayList<>();
        for (ProfessionalRole role : ProfessionalRole.values()) {
            table.rateOn(role, asOf).map(wageRateMapper::toDto).ifPresent(current::add);
        }
        return current;
    }

    /**
     * Every rate ever set for a role, newest effective date first.
     */
    public List<WageRateDTO> historyFor(ProfessionalRole role) {
        LOG.debug("Request to get the rate history for {}", role);
        return wageRateMapper.toDto(wageRateRepository.findByRoleOrderByValidFromDesc(role));
    }

    /**
     * Get one wageRate by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<WageRateDTO> findOne(String id) {
        LOG.debug("Request to get WageRate : {}", id);
        return wageRateRepository.findById(id).map(wageRateMapper::toDto);
    }

    /**
     * Delete the wageRate by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete WageRate : {}", id);
        wageRateRepository.deleteById(id);
    }
}
