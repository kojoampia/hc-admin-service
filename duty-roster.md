# **Patient-Centric Duty Roster Auto-Schedule Implementation**

This document outlines the complete backend implementation for the heuristic Auto-Schedule feature. It has been refactored to be **Patient-Centric**, meaning shifts represent a patient's personalized "Daily Service Plan". It also rigorously enforces geographic, team-based, and date-range availability constraints.

We introduce the Unavailability which dictates the specific dates a professional is unavailable due to holidays, sick leave, or pending verification. The scheduling algorithm now ensures that professionals are not assigned to shifts on days they are unavailable, and it prevents double-booking by checking existing assignments for the target date.

## **1. Backend: Document Models (MongoDB)**

We introduce the PatientProfile which dictates the geographicSpaceId. The Shift model is updated to act as an entry in the patient's daily service plan.

// GeographicSpace.java & Team.java  
@Document(collection = "geographic_spaces")  
@Data  
public class GeographicSpace {  
 @Id private String id;  
 private String name; // e.g., "Downtown", "Uptown", "Suburb"
private String type; // e.g., "NEIGHBORHOOD", "DISTRICT", "CITY"
}

@Document(collection = "teams")  
@Data  
public class Team {  
 @Id private String id;  
 private String name; // e.g., "Team A", "Team B"
private List<String> geographicSpaceIds; // List of GeographicSpace.id that this team covers
}

// Unavailability Models  
public enum UnavailabilityReason { NOT_VERIFIED, HOLIDAY, SICK_LEAVE, ABSENCE }

@Data  
public class UnavailabilityPeriod {  
 private UnavailabilityReason reason;  
 private LocalDate fromDate;  
 private LocalDate toDate;
}

// Profile.java (Read-only projection hydrated via Kafka)  
@Document(collection = "profiles")  
@Data  
public class Profile {  
 @Id private String id;  
 private String firstName;  
 private String lastName;  
 private RoleType roleType; // PATIENT, PROFESSIONAL, VENDOR, ADMIN  
 private boolean isActive;

    // Professional specific fields
    private String teamId;
    private List<UnavailabilityPeriod> unavailabilityPeriods;

    public boolean isAvailable(LocalDate date) {
        if (!isActive) return false;
        if (unavailabilityPeriods == null || unavailabilityPeriods.isEmpty()) return true;

        for (UnavailabilityPeriod period : unavailabilityPeriods) {
            if (period.getFromDate() != null && !date.isBefore(period.getFromDate())) {
                if (period.getToDate() == null || !date.isAfter(period.getToDate())) {
                    return false;
                }
            }
        }
        return true;
    }

}

// Shift.java (Represents a visit in a Patient's Daily Service Plan)  
@Document(collection = "shifts")  
@Data  
public class Shift {  
 @Id private String id;  
 private String patientId; // NEW: Ties the shift to a specific patient  
 private LocalDate date;  
 private String shiftName; // e.g., "Morning Checkup", "Lunch Prep"  
 private RoleType requiredRole; // e.g., DOCTOR, CAREGIVER  
 private String geographicSpaceId; // Inherited from PatientProfile  
 private String assigneeId; // References Professional Profile.id  
 private ShiftStatus status; // UNASSIGNED, ASSIGNED  
}

## **2. Backend: Heuristic Scheduling Algorithm (DutyRosterService.java)**

The algorithm now schedules the daily service plans for patients, ensuring the assigned professional meets all geographic, team, and dynamic availability constraints.

import lombok.RequiredArgsConstructor;  
import org.springframework.kafka.core.KafkaTemplate;  
import org.springframework.stereotype.Service;  
import java.time.DayOfWeek;  
import java.time.LocalDate;  
import java.util.Comparator;  
import java.util.List;  
import java.util.stream.Collectors;

@Service  
@RequiredArgsConstructor  
public class DutyRosterService {

    private final ShiftRepository shiftRepository;
    private final ProfileRepository profileRepository;
    private final TeamRepository teamRepository;
    private final KafkaTemplate<String, RosterEvent> kafkaTemplate;

    public void autoScheduleShifts(LocalDate date) {
        // Fetch all unassigned patient care shifts for the target date
        List<Shift> unassignedShifts = shiftRepository.findByDateAndStatus(date, ShiftStatus.UNASSIGNED);

        LocalDate startOfWeek = date.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = date.with(DayOfWeek.SUNDAY);

        for (Shift shift : unassignedShifts) {
            // 1. HARD CONSTRAINTS: Geography & Team
            List<Team> coveringTeams = teamRepository.findByGeographicSpaceIdsContaining(shift.getGeographicSpaceId());
            List<String> validTeamIds = coveringTeams.stream().map(Team::getId).collect(Collectors.toList());

            if (validTeamIds.isEmpty()) continue; // No teams cover this patient's location

            // 2. HARD CONSTRAINTS: Role match and Team membership
            List<Profile> availableProfessionals = profileRepository.findByRoleTypeAndTeamIdInAndIsActiveTrue(
                shift.getRequiredRole(), validTeamIds
            );

            // 3. HARD CONSTRAINTS: Date-range availability & Double-booking prevention
            List<Profile> eligibleProfessionals = availableProfessionals.stream()
                .filter(p -> p.isAvailable(date)) // Dynamic check against holidays, sick leave, verification
                .filter(p -> !shiftRepository.existsByAssigneeIdAndDate(p.getId(), date))
                .collect(Collectors.toList());

            if (eligibleProfessionals.isEmpty()) continue; // No eligible professionals available

            // 4. SOFT CONSTRAINTS: Sort by fewest shifts assigned this week (Fairness / Burnout prevention)
            eligibleProfessionals.sort(Comparator.comparingInt(p ->
                shiftRepository.countByAssigneeIdAndDateBetween(p.getId(), startOfWeek, endOfWeek)
            ));

            // 5. Assign the optimal professional to the Patient's service plan
            Profile selectedProfessional = eligibleProfessionals.get(0);
            shift.setAssigneeId(selectedProfessional.getId());
            shift.setStatus(ShiftStatus.ASSIGNED);

            shiftRepository.save(shift);

            // 6. Emit Event to update Patient & Professional mobile apps in real-time
            RosterEvent event = new RosterEvent(shift.getId(), "PATIENT_SHIFT_ASSIGNED", shift.getPatientId(), selectedProfessional.getId());
            kafkaTemplate.send("roster-events", event);
        }
    }

}

## **3. Backend: REST Controller (DutyRosterController.java)**

We expose a new endpoint specifically to fetch a patient's daily service plan.

import lombok.RequiredArgsConstructor;  
import org.springframework.format.annotation.DateTimeFormat;  
import org.springframework.http.ResponseEntity;  
import org.springframework.security.access.prepost.PreAuthorize;  
import org.springframework.web.bind.annotation.\*;  
import java.time.LocalDate;  
import java.util.List;

@RestController  
@RequestMapping("/api/v1/roster")  
@RequiredArgsConstructor  
public class DutyRosterController {

    private final DutyRosterService dutyRosterService;
    private final ShiftRepository shiftRepository;

    @PostMapping("/auto-schedule")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> autoSchedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        dutyRosterService.autoScheduleShifts(date);
        return ResponseEntity.ok().build();
    }

    // NEW: Fetch the daily service plan for a specific patient
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_PATIENT')")
    public ResponseEntity<List<Shift>> getPatientDailyPlan(
            @PathVariable String patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Shift> patientShifts = shiftRepository.findByPatientIdAndDateOrderByShiftNameAsc(patientId, date);
        return ResponseEntity.ok(patientShifts);
    }

}
