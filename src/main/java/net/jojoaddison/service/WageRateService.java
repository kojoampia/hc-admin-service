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
import net.jojoaddison.domain.enumeration.ShiftType;
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
 * <p>The interesting method here is {@link #rateOn(ProfessionalRole, ShiftType, LocalDate)} —
 * everything else is the ordinary CRUD contract.
 *
 * <p>That signature took {@code (role, date)} until 2026-09-04, when {@code shiftType} became part
 * of the key. The change is deliberately a <b>compile break</b> rather than an overload: a call site
 * left on the old two-argument form would have gone on resolving a rate that ignored the shift, and
 * been right about it for as long as every role had exactly one rate.
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
     * The rate governing a shift of type {@code shiftType} worked by {@code role} on {@code date}:
     * the row with the greatest {@code validFrom} that is not after that date.
     *
     * <p>Empty means no rate had been configured for that combination when the shift was worked.
     * That is deliberately distinct from a rate of zero — the caller decides whether to report it as
     * unpriced or to treat it as nothing owed, and the console has to be able to tell those apart.
     *
     * <p><b>The match on {@code shiftType} is exact and does not fall back to a role-only rate</b>,
     * because there is no such row: {@code WageRate.shiftType} is required. A fallback would need a
     * rule for whether a general row dated later beats a specific row dated earlier, and every
     * answer to that surprises somebody. See {@link net.jojoaddison.domain.WageRate}.
     */
    public Optional<WageRate> rateOn(ProfessionalRole role, ShiftType shiftType, LocalDate date) {
        if (role == null || shiftType == null || date == null) {
            return Optional.empty();
        }
        return wageRateRepository
            .findByRoleAndShiftTypeAndValidFromLessThanEqualOrderByValidFromDesc(role, shiftType, date)
            .stream()
            .findFirst();
    }

    /**
     * A resolver for valuing many shifts at once. Loads every rate in force on or before
     * {@code upTo} once, then answers from memory — the alternative is a query per shift, and a
     * month of roster for one professional is already tens of shifts.
     *
     * <p><b>A stored row missing {@code role} or {@code shiftType} fails here, by name.</b> Both are
     * {@code @NotNull} on the document, so such a row is corrupt rather than merely old — but an
     * {@code EnumMap} rejects a null key with a bare {@code NullPointerException}, and this method is
     * behind {@code earningsFor}, {@code currentCurrencyFor} and {@code GET /api/wage-rates/current}.
     * One row therefore turned the wage-rates screen and every earnings screen into a 500 whose stack
     * trace named an {@code EnumMap} and nothing else — a data problem reported as a collections
     * problem. {@code ShiftTypeMigration} backfills the rows that the 2026-09-04 dimension left
     * behind; this is what the next one looks like if anything survives it.
     */
    public RateTable rateTableUpTo(LocalDate upTo) {
        Map<ProfessionalRole, Map<ShiftType, List<WageRate>>> byRole = new EnumMap<>(ProfessionalRole.class);
        for (WageRate rate : wageRateRepository.findByValidFromLessThanEqualOrderByValidFromDesc(upTo)) {
            requireKey(rate);
            byRole
                .computeIfAbsent(rate.getRole(), r -> new EnumMap<>(ShiftType.class))
                .computeIfAbsent(rate.getShiftType(), s -> new ArrayList<>())
                .add(rate);
        }
        // The derived query orders globally, not within a cell, so sort each bucket explicitly
        // rather than trusting the order the driver happened to return.
        byRole
            .values()
            .forEach(byShift -> byShift.values().forEach(rates -> rates.sort(Comparator.comparing(WageRate::getValidFrom).reversed())));
        return new RateTable(byRole);
    }

    /**
     * Refuses a rate whose lookup key is incomplete, naming the row and what to do about it.
     *
     * <p>The message has to carry the id: "a wage rate has no shift type" sends a reader to the
     * screen, and the row is not on the screen — it is the reason the screen is not there.
     */
    private static void requireKey(WageRate rate) {
        if (rate.getRole() == null || rate.getShiftType() == null) {
            throw new IllegalStateException(
                (
                    "Stored wage_rate %s has role=%s and shift_type=%s; both are required to price a shift. " +
                    "A row written before the shift-type dimension of 2026-09-04 is backfilled by " +
                    "ShiftTypeMigration on startup — run it, or set the field on this row."
                ).formatted(rate.getId(), rate.getRole(), rate.getShiftType())
            );
        }
    }

    /**
     * Rates in force on or before a date, grouped by {@code (role, shiftType)}, newest first.
     * Resolution walks one cell's list and takes the first entry not after the date asked about.
     *
     * <p>Two levels of map rather than a composite key, because the outer level is what
     * {@link #currentRates} iterates and what the console's grid is shaped like. Both are
     * {@code EnumMap}s: the number of cells is fixed by two enums and will not grow with the data.
     */
    public record RateTable(Map<ProfessionalRole, Map<ShiftType, List<WageRate>>> byRole) {
        public Optional<WageRate> rateOn(ProfessionalRole role, ShiftType shiftType, LocalDate date) {
            if (role == null || shiftType == null || date == null) {
                return Optional.empty();
            }
            return byRole
                .getOrDefault(role, Map.of())
                .getOrDefault(shiftType, List.of())
                .stream()
                .filter(rate -> !rate.getValidFrom().isAfter(date))
                .findFirst();
        }

        public BigDecimal amountOn(ProfessionalRole role, ShiftType shiftType, LocalDate date) {
            return rateOn(role, shiftType, date).map(WageRate::getAmount).orElse(BigDecimal.ZERO);
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
     * The rate currently in force for each {@code (role, shiftType)} cell — the grid the
     * configuration screen leads with. Cells with no rate configured are <b>absent rather than
     * zero</b>, which is what lets the screen show an unpriced cell as unpriced.
     *
     * <p>Bounded by two enums, so it is a list rather than a page: five roles by five shift types is
     * twenty-five rows at most, and the screen wants all of them at once. It returned one row per
     * role until 2026-09-04 and the shape of the response is unchanged — a flat list of rates, each
     * naming its own role and shift type — so a caller that groups by role still works and simply
     * finds several.
     *
     * <p>Every shift type is offered, including {@code OFF}. The pricing grid is asked for once and
     * in full; {@code ShiftValuationService} then never resolves a rate for an {@code OFF} shift,
     * because such a shift is not payable in the first place.
     */
    public List<WageRateDTO> currentRates(LocalDate asOf) {
        LOG.debug("Request to get the rates in force on {}", asOf);
        RateTable table = rateTableUpTo(asOf);
        List<WageRateDTO> current = new ArrayList<>();
        for (ProfessionalRole role : ProfessionalRole.values()) {
            for (ShiftType shiftType : ShiftType.values()) {
                table.rateOn(role, shiftType, asOf).map(wageRateMapper::toDto).ifPresent(current::add);
            }
        }
        return current;
    }

    /**
     * Every rate ever set for a role, newest effective date first — across all shift types when
     * {@code shiftType} is null, and for one cell when it is not.
     *
     * <p>The narrowed form is what the console's per-cell history panel reads. The wide form is kept
     * because "what has this role ever been paid" is a question somebody asks, and because dropping
     * it would have silently changed the meaning of the existing endpoint rather than extending it.
     */
    public List<WageRateDTO> historyFor(ProfessionalRole role, ShiftType shiftType) {
        LOG.debug("Request to get the rate history for {} {}", role, shiftType);
        return wageRateMapper.toDto(
            shiftType == null
                ? wageRateRepository.findByRoleOrderByValidFromDesc(role)
                : wageRateRepository.findByRoleAndShiftTypeOrderByValidFromDesc(role, shiftType)
        );
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
