package net.jojoaddison.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.broker.RosterEvent;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.service.dto.DutyRosterDTO;
import net.jojoaddison.service.mapper.DutyRosterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
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
    private final ProfileRepository profileRepository;
    private final TeamRepository teamRepository;
    private final StreamBridge streamBridge;

    public DutyRosterService(
        DutyRosterRepository dutyRosterRepository,
        DutyRosterMapper dutyRosterMapper,
        ProfileRepository profileRepository,
        TeamRepository teamRepository,
        StreamBridge streamBridge
    ) {
        this.dutyRosterRepository = dutyRosterRepository;
        this.dutyRosterMapper = dutyRosterMapper;
        this.profileRepository = profileRepository;
        this.teamRepository = teamRepository;
        this.streamBridge = streamBridge;
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

    /**
     * Auto-schedule unassigned shifts for a given date using heuristic algorithm.
     * Enforces geographic, team-based, and date-range availability constraints.
     * Prevents double-booking and applies workload fairness (fewest shifts this week).
     *
     * @param date the target date to schedule shifts for
     */
    public void autoScheduleShifts(LocalDate date) {
        LOG.debug("Request to auto-schedule shifts for date : {}", date);

        List<DutyRoster> unassignedShifts = dutyRosterRepository.findByDateAndStatus(date, ShiftStatus.UNASSIGNED);

        LocalDate startOfWeek = date.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = date.with(DayOfWeek.SUNDAY);

        for (DutyRoster shift : unassignedShifts) {
            // 1. HARD CONSTRAINTS: Geography & Team
            List<Team> coveringTeams = teamRepository.findByGeographicSpaceIdsContaining(shift.getGeographicSpaceId());
            List<String> validTeamIds = coveringTeams.stream().map(Team::getId).collect(Collectors.toList());

            if (validTeamIds.isEmpty()) {
                LOG.debug("No teams cover geographic space {} for shift {}", shift.getGeographicSpaceId(), shift.getId());
                continue;
            }

            // 2. HARD CONSTRAINTS: Role match and team membership
            List<Profile> availableProfessionals = profileRepository.findByRoleTypeAndTeamIdInAndStatusTrue(
                shift.getDuty().name().equals("DOCTOR")
                    ? net.jojoaddison.domain.enumeration.RoleType.PROFESSIONAL
                    : net.jojoaddison.domain.enumeration.RoleType.PROFESSIONAL,
                validTeamIds
            );

            // 3. HARD CONSTRAINTS: Date-range availability & double-booking prevention
            List<Profile> eligibleProfessionals = availableProfessionals
                .stream()
                .filter(p -> p.isAvailable(date))
                .filter(p -> !dutyRosterRepository.existsByProfessionalIdAndDate(p.getId(), date))
                .collect(Collectors.toList());

            if (eligibleProfessionals.isEmpty()) {
                LOG.debug("No eligible professionals available for shift {} on {}", shift.getId(), date);
                continue;
            }

            // 4. SOFT CONSTRAINTS: Sort by fewest shifts this week (fairness / burnout prevention)
            eligibleProfessionals.sort(
                Comparator.comparingInt(p -> dutyRosterRepository.countByProfessionalIdAndDateBetween(p.getId(), startOfWeek, endOfWeek))
            );

            // 5. Assign the optimal professional
            Profile selectedProfessional = eligibleProfessionals.get(0);
            shift.setProfessionalId(selectedProfessional.getId());
            shift.setStatus(ShiftStatus.ASSIGNED);

            dutyRosterRepository.save(shift);

            // 6. Emit event to update patient & professional mobile apps in real-time
            RosterEvent event = new RosterEvent(
                shift.getId(),
                "PATIENT_SHIFT_ASSIGNED",
                shift.getPatientId(),
                selectedProfessional.getId()
            );
            streamBridge.send("roster-events", event);

            LOG.debug("Assigned professional {} to shift {}", selectedProfessional.getId(), shift.getId());
        }
    }

    /**
     * Get all shifts (duty rosters) for a specific patient on a given date, ordered by name.
     *
     * @param patientId the patient's profile id
     * @param date      the date to retrieve the service plan for
     * @return list of duty rosters representing the patient's daily service plan
     */
    public List<DutyRosterDTO> findByPatientAndDate(String patientId, LocalDate date) {
        LOG.debug("Request to get daily plan for patient {} on date {}", patientId, date);
        return dutyRosterRepository
            .findByPatientIdAndDateOrderByNameAsc(patientId, date)
            .stream()
            .map(dutyRosterMapper::toDto)
            .collect(Collectors.toList());
    }
}
