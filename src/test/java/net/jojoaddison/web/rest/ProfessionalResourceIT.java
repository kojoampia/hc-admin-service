package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ProfessionalAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static net.jojoaddison.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.UnavailabilityPeriod;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.UnavailabilityReason;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ProfessionalResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ProfessionalResourceIT {

    private static final ProfessionalRole DEFAULT_ROLE = ProfessionalRole.CAREGIVER;
    private static final ProfessionalRole UPDATED_ROLE = ProfessionalRole.PARAMEDIC;

    private static final String DEFAULT_SPECIALITY = "AAAAAAAAAA";
    private static final String UPDATED_SPECIALITY = "BBBBBBBBBB";

    private static final String DEFAULT_LICENCE_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_LICENCE_NUMBER = "BBBBBBBBBB";

    private static final VerificationStatus DEFAULT_VERIFICATION = VerificationStatus.VERIFIED;
    private static final VerificationStatus UPDATED_VERIFICATION = VerificationStatus.PENDING;

    private static final AccountStatus DEFAULT_STATUS = AccountStatus.ACTIVE;
    private static final AccountStatus UPDATED_STATUS = AccountStatus.PENDING;

    private static final Integer DEFAULT_PATIENT_COUNT = 0;
    private static final Integer UPDATED_PATIENT_COUNT = 1;

    private static final Integer DEFAULT_CASE_COUNT = 0;
    private static final Integer UPDATED_CASE_COUNT = 1;

    private static final Integer DEFAULT_VISIT_COUNT = 0;
    private static final Integer UPDATED_VISIT_COUNT = 1;

    private static final BigDecimal DEFAULT_RATING = new BigDecimal(0);
    private static final BigDecimal UPDATED_RATING = new BigDecimal(1);

    private static final LocalDate DEFAULT_JOINED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_JOINED_ON = LocalDate.parse("2024-03-26");

    private static final Boolean DEFAULT_IS_ARCHIVED = false;
    private static final Boolean UPDATED_IS_ARCHIVED = true;

    private static final String DEFAULT_HOME_SPACE_ID = "AAAAAAAAAA";
    private static final String UPDATED_HOME_SPACE_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/professionals";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Mock
    private ProfessionalRepository professionalRepositoryMock;

    @Autowired
    private MockMvc restProfessionalMockMvc;

    private Professional professional;

    private Professional insertedProfessional;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Professional createEntity() {
        Professional professional = new Professional()
            .role(DEFAULT_ROLE)
            .speciality(DEFAULT_SPECIALITY)
            .licenceNumber(DEFAULT_LICENCE_NUMBER)
            .verification(DEFAULT_VERIFICATION)
            .status(DEFAULT_STATUS)
            .patientCount(DEFAULT_PATIENT_COUNT)
            .caseCount(DEFAULT_CASE_COUNT)
            .visitCount(DEFAULT_VISIT_COUNT)
            .rating(DEFAULT_RATING)
            .joinedOn(DEFAULT_JOINED_ON)
            .isArchived(DEFAULT_IS_ARCHIVED)
            .homeSpaceId(DEFAULT_HOME_SPACE_ID);
        // Add required entity
        Profile profile;
        profile = ProfileResourceIT.createEntity();
        profile.setId("fixed-id-for-tests");
        professional.setProfile(profile);
        return professional;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Professional createUpdatedEntity() {
        Professional updatedProfessional = new Professional()
            .role(UPDATED_ROLE)
            .speciality(UPDATED_SPECIALITY)
            .licenceNumber(UPDATED_LICENCE_NUMBER)
            .verification(UPDATED_VERIFICATION)
            .status(UPDATED_STATUS)
            .patientCount(UPDATED_PATIENT_COUNT)
            .caseCount(UPDATED_CASE_COUNT)
            .visitCount(UPDATED_VISIT_COUNT)
            .rating(UPDATED_RATING)
            .joinedOn(UPDATED_JOINED_ON)
            .isArchived(UPDATED_IS_ARCHIVED)
            .homeSpaceId(UPDATED_HOME_SPACE_ID);
        // Add required entity
        Profile profile;
        profile = ProfileResourceIT.createUpdatedEntity();
        profile.setId("fixed-id-for-tests");
        updatedProfessional.setProfile(profile);
        return updatedProfessional;
    }

    @BeforeEach
    void initTest() {
        professional = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedProfessional != null) {
            professionalRepository.delete(insertedProfessional);
            insertedProfessional = null;
        }
    }

    @Test
    void createProfessional() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Professional
        var returnedProfessional = om.readValue(
            restProfessionalMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Professional.class
        );

        // Validate the Professional in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertProfessionalUpdatableFieldsEquals(returnedProfessional, getPersistedProfessional(returnedProfessional));

        insertedProfessional = returnedProfessional;
    }

    @Test
    void createProfessionalWithExistingId() throws Exception {
        // Create the Professional with an existing ID
        professional.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkRoleIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        professional.setRole(null);

        // Create the Professional, which fails.

        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkLicenceNumberIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        professional.setLicenceNumber(null);

        // Create the Professional, which fails.

        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkVerificationIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        professional.setVerification(null);

        // Create the Professional, which fails.

        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        professional.setStatus(null);

        // Create the Professional, which fails.

        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkJoinedOnIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        professional.setJoinedOn(null);

        // Create the Professional, which fails.

        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllProfessionals() throws Exception {
        // Initialize the database
        insertedProfessional = professionalRepository.save(professional);

        // Get all the professionalList
        restProfessionalMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(professional.getId())))
            .andExpect(jsonPath("$.[*].role").value(hasItem(DEFAULT_ROLE.toString())))
            .andExpect(jsonPath("$.[*].speciality").value(hasItem(DEFAULT_SPECIALITY)))
            .andExpect(jsonPath("$.[*].licenceNumber").value(hasItem(DEFAULT_LICENCE_NUMBER)))
            .andExpect(jsonPath("$.[*].verification").value(hasItem(DEFAULT_VERIFICATION.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].patientCount").value(hasItem(DEFAULT_PATIENT_COUNT)))
            .andExpect(jsonPath("$.[*].caseCount").value(hasItem(DEFAULT_CASE_COUNT)))
            .andExpect(jsonPath("$.[*].visitCount").value(hasItem(DEFAULT_VISIT_COUNT)))
            .andExpect(jsonPath("$.[*].rating").value(hasItem(sameNumber(DEFAULT_RATING))))
            .andExpect(jsonPath("$.[*].joinedOn").value(hasItem(DEFAULT_JOINED_ON.toString())))
            .andExpect(jsonPath("$.[*].isArchived").value(hasItem(DEFAULT_IS_ARCHIVED)))
            .andExpect(jsonPath("$.[*].homeSpaceId").value(hasItem(DEFAULT_HOME_SPACE_ID)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllProfessionalsWithEagerRelationshipsIsEnabled() throws Exception {
        when(professionalRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restProfessionalMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(professionalRepositoryMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllProfessionalsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(professionalRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restProfessionalMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(professionalRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getProfessional() throws Exception {
        // Initialize the database
        insertedProfessional = professionalRepository.save(professional);

        // Get the professional
        restProfessionalMockMvc
            .perform(get(ENTITY_API_URL_ID, professional.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(professional.getId()))
            .andExpect(jsonPath("$.role").value(DEFAULT_ROLE.toString()))
            .andExpect(jsonPath("$.speciality").value(DEFAULT_SPECIALITY))
            .andExpect(jsonPath("$.licenceNumber").value(DEFAULT_LICENCE_NUMBER))
            .andExpect(jsonPath("$.verification").value(DEFAULT_VERIFICATION.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.patientCount").value(DEFAULT_PATIENT_COUNT))
            .andExpect(jsonPath("$.caseCount").value(DEFAULT_CASE_COUNT))
            .andExpect(jsonPath("$.visitCount").value(DEFAULT_VISIT_COUNT))
            .andExpect(jsonPath("$.rating").value(sameNumber(DEFAULT_RATING)))
            .andExpect(jsonPath("$.joinedOn").value(DEFAULT_JOINED_ON.toString()))
            .andExpect(jsonPath("$.isArchived").value(DEFAULT_IS_ARCHIVED))
            .andExpect(jsonPath("$.homeSpaceId").value(DEFAULT_HOME_SPACE_ID));
    }

    @Test
    void getNonExistingProfessional() throws Exception {
        // Get the professional
        restProfessionalMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingProfessional() throws Exception {
        // Initialize the database
        insertedProfessional = professionalRepository.save(professional);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the professional
        Professional updatedProfessional = professionalRepository.findById(professional.getId()).orElseThrow();
        updatedProfessional
            .role(UPDATED_ROLE)
            .speciality(UPDATED_SPECIALITY)
            .licenceNumber(UPDATED_LICENCE_NUMBER)
            .verification(UPDATED_VERIFICATION)
            .status(UPDATED_STATUS)
            .patientCount(UPDATED_PATIENT_COUNT)
            .caseCount(UPDATED_CASE_COUNT)
            .visitCount(UPDATED_VISIT_COUNT)
            .rating(UPDATED_RATING)
            .joinedOn(UPDATED_JOINED_ON)
            .isArchived(UPDATED_IS_ARCHIVED)
            .homeSpaceId(UPDATED_HOME_SPACE_ID);

        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedProfessional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedProfessional))
            )
            .andExpect(status().isOk());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedProfessionalToMatchAllProperties(updatedProfessional);
    }

    /**
     * <b>A PUT that says nothing about the home space must not erase it.</b>
     *
     * <p>{@code homeSpaceId} is not in the console model, so the generated edit form does not send
     * it — and PUT sends a whole document. Without the restore in {@code ProfessionalResource}, the
     * first time anybody edits a professional their home space is gone: the record saves, the screen
     * shows exactly what it asked for, and proximity ranking loses the origin it measures from with
     * nothing anywhere reporting a change. {@code TeamService.restoreGeographicSpaceIds} exists
     * because this already happened one collection over, to the same field, from the same client.
     */
    @Test
    void putWithoutAHomeSpaceKeepsTheStoredOne() throws Exception {
        insertedProfessional = professionalRepository.save(professional.homeSpaceId("gs-osu"));

        Professional withoutHomeSpace = professionalRepository.findById(professional.getId()).orElseThrow();
        withoutHomeSpace.setHomeSpaceId(null);
        withoutHomeSpace.setSpeciality(UPDATED_SPECIALITY);

        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, withoutHomeSpace.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(withoutHomeSpace))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeSpaceId").value("gs-osu"));

        assertThat(getPersistedProfessional(professional).getHomeSpaceId()).isEqualTo("gs-osu");
        // The rest of the payload still lands — the restore is one field, not a rejection.
        assertThat(getPersistedProfessional(professional).getSpeciality()).isEqualTo(UPDATED_SPECIALITY);
    }

    /** And a PUT that does name one moves it, so the restore is not a freeze. */
    @Test
    void putWithAHomeSpaceMovesIt() throws Exception {
        insertedProfessional = professionalRepository.save(professional.homeSpaceId("gs-osu"));

        Professional moved = professionalRepository.findById(professional.getId()).orElseThrow();
        moved.setHomeSpaceId("gs-madina");

        restProfessionalMockMvc
            .perform(put(ENTITY_API_URL_ID, moved.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(moved)))
            .andExpect(status().isOk());

        assertThat(getPersistedProfessional(professional).getHomeSpaceId()).isEqualTo("gs-madina");
    }

    /**
     * <b>And a PUT that says nothing about leave must not end it.</b>
     *
     * <p>The mirror of {@link #putWithoutAHomeSpaceKeepsTheStoredOne}, for the field beside it.
     * {@code unavailabilityPeriods} is not in the console model either, so the generated edit form
     * does not send it and a PUT arrives with the list null. Without the restore in
     * {@code ProfessionalResource}, editing anybody's speciality returns them to the candidate pool:
     * the planner reads leave through {@link Professional#isAvailable(LocalDate)}, so the next
     * planning round rosters somebody who is on holiday, and nothing between the edit and the roster
     * says anything happened.
     *
     * <p>Worth its own case rather than trusting the neighbour's: the two fields take
     * <em>different</em> rules, and only one of them can be checked by reading the other's test.
     * {@code homeSpaceId} is a String, where one absent value has to do both jobs, so null cannot
     * clear it. A list can tell the difference, so here null is an omission and an empty list is a
     * deliberate clear — which is {@code Team}'s rule, and which
     * {@link #putWithAnEmptyUnavailabilityListClearsTheStoredOne} pins so the restore cannot quietly
     * become a freeze.
     */
    @Test
    void putWithoutUnavailabilityPeriodsKeepsTheStoredOnes() throws Exception {
        List<UnavailabilityPeriod> onHoliday = List.of(
            new UnavailabilityPeriod()
                .reason(UnavailabilityReason.HOLIDAY)
                .fromDate(LocalDate.of(2026, 9, 1))
                .toDate(LocalDate.of(2026, 9, 30))
        );
        insertedProfessional = professionalRepository.save(professional.unavailabilityPeriods(onHoliday));

        Professional withoutLeave = professionalRepository.findById(professional.getId()).orElseThrow();
        withoutLeave.setUnavailabilityPeriods(null);
        withoutLeave.setSpeciality(UPDATED_SPECIALITY);

        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, withoutLeave.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(withoutLeave))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unavailabilityPeriods").isArray())
            .andExpect(jsonPath("$.unavailabilityPeriods[0].reason").value(UnavailabilityReason.HOLIDAY.toString()));

        Professional persisted = getPersistedProfessional(professional);
        assertThat(persisted.getUnavailabilityPeriods()).hasSize(1);
        assertThat(persisted.getUnavailabilityPeriods().get(0).getFromDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(persisted.getUnavailabilityPeriods().get(0).getToDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        // The rest of the payload still lands — the restore is one field, not a rejection.
        assertThat(persisted.getSpeciality()).isEqualTo(UPDATED_SPECIALITY);
        // And the leave still means what it meant: the planner must still skip this date.
        assertThat(persisted.isAvailable(LocalDate.of(2026, 9, 15))).isFalse();
    }

    /**
     * And an empty list is a clear, not another omission — so the restore is not a freeze.
     *
     * <p>This is the half {@code homeSpaceId} cannot have, and the reason its rule is stated
     * separately. Without this case the restore above would pass just as well if it had been written
     * to ignore the field entirely, and there would then be no way at all to end somebody's leave
     * through the API.
     */
    @Test
    void putWithAnEmptyUnavailabilityListClearsTheStoredOne() throws Exception {
        insertedProfessional =
            professionalRepository.save(
                professional.unavailabilityPeriods(
                    List.of(
                        new UnavailabilityPeriod()
                            .reason(UnavailabilityReason.SICK_LEAVE)
                            .fromDate(LocalDate.of(2026, 9, 1))
                            .toDate(LocalDate.of(2026, 9, 30))
                    )
                )
            );

        Professional backAtWork = professionalRepository.findById(professional.getId()).orElseThrow();
        backAtWork.setUnavailabilityPeriods(new ArrayList<>());

        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, backAtWork.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(backAtWork))
            )
            .andExpect(status().isOk());

        Professional persisted = getPersistedProfessional(professional);
        assertThat(persisted.getUnavailabilityPeriods()).isEmpty();
        assertThat(persisted.isAvailable(LocalDate.of(2026, 9, 15))).isTrue();
    }

    @Test
    void putNonExistingProfessional() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        professional.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, professional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchProfessional() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamProfessional() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(professional)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateProfessionalWithPatch() throws Exception {
        // Initialize the database
        insertedProfessional = professionalRepository.save(professional);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the professional using partial update
        Professional partialUpdatedProfessional = new Professional();
        partialUpdatedProfessional.setId(professional.getId());

        partialUpdatedProfessional
            .role(UPDATED_ROLE)
            .licenceNumber(UPDATED_LICENCE_NUMBER)
            .verification(UPDATED_VERIFICATION)
            .caseCount(UPDATED_CASE_COUNT)
            .visitCount(UPDATED_VISIT_COUNT)
            .isArchived(UPDATED_IS_ARCHIVED);

        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedProfessional))
            )
            .andExpect(status().isOk());

        // Validate the Professional in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertProfessionalUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedProfessional, professional),
            getPersistedProfessional(professional)
        );
    }

    @Test
    void fullUpdateProfessionalWithPatch() throws Exception {
        // Initialize the database
        insertedProfessional = professionalRepository.save(professional);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the professional using partial update
        Professional partialUpdatedProfessional = new Professional();
        partialUpdatedProfessional.setId(professional.getId());

        partialUpdatedProfessional
            .role(UPDATED_ROLE)
            .speciality(UPDATED_SPECIALITY)
            .licenceNumber(UPDATED_LICENCE_NUMBER)
            .verification(UPDATED_VERIFICATION)
            .status(UPDATED_STATUS)
            .patientCount(UPDATED_PATIENT_COUNT)
            .caseCount(UPDATED_CASE_COUNT)
            .visitCount(UPDATED_VISIT_COUNT)
            .rating(UPDATED_RATING)
            .joinedOn(UPDATED_JOINED_ON)
            .isArchived(UPDATED_IS_ARCHIVED)
            .homeSpaceId(UPDATED_HOME_SPACE_ID);

        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedProfessional))
            )
            .andExpect(status().isOk());

        // Validate the Professional in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertProfessionalUpdatableFieldsEquals(partialUpdatedProfessional, getPersistedProfessional(partialUpdatedProfessional));
    }

    @Test
    void patchNonExistingProfessional() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        professional.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, professional.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchProfessional() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamProfessional() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(professional)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Professional in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteProfessional() throws Exception {
        // Initialize the database
        insertedProfessional = professionalRepository.save(professional);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the professional
        restProfessionalMockMvc
            .perform(delete(ENTITY_API_URL_ID, professional.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return professionalRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Professional getPersistedProfessional(Professional professional) {
        return professionalRepository.findById(professional.getId()).orElseThrow();
    }

    protected void assertPersistedProfessionalToMatchAllProperties(Professional expectedProfessional) {
        assertProfessionalAllPropertiesEquals(expectedProfessional, getPersistedProfessional(expectedProfessional));
    }

    protected void assertPersistedProfessionalToMatchUpdatableProperties(Professional expectedProfessional) {
        assertProfessionalAllUpdatablePropertiesEquals(expectedProfessional, getPersistedProfessional(expectedProfessional));
    }
}
