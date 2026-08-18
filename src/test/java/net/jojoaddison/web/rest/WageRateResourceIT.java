package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.repository.WageRateRepository;
import net.jojoaddison.service.dto.WageRateDTO;
import net.jojoaddison.service.mapper.WageRateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link WageRateResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class WageRateResourceIT {

    private static final ProfessionalRole DEFAULT_ROLE = ProfessionalRole.DOCTOR;
    private static final ProfessionalRole UPDATED_ROLE = ProfessionalRole.NURSE;

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal(500);
    private static final BigDecimal UPDATED_AMOUNT = new BigDecimal(550);

    private static final String DEFAULT_CURRENCY = "GHS";

    private static final LocalDate DEFAULT_VALID_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate UPDATED_VALID_FROM = LocalDate.of(2026, 9, 1);

    private static final String ENTITY_API_URL = "/api/wage-rates";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private WageRateRepository wageRateRepository;

    @Autowired
    private WageRateMapper wageRateMapper;

    @Autowired
    private MockMvc restWageRateMockMvc;

    private WageRate wageRate;

    public static WageRate createEntity() {
        return new WageRate().role(DEFAULT_ROLE).amount(DEFAULT_AMOUNT).currency(DEFAULT_CURRENCY).validFrom(DEFAULT_VALID_FROM);
    }

    @BeforeEach
    void initTest() {
        wageRateRepository.deleteAll();
        wageRate = createEntity();
    }

    @Test
    void createWageRate() throws Exception {
        long databaseSizeBeforeCreate = wageRateRepository.count();
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);

        restWageRateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(wageRateDTO)))
            .andExpect(status().isCreated());

        assertThat(wageRateRepository.count()).isEqualTo(databaseSizeBeforeCreate + 1);
        WageRate saved = wageRateRepository.findAll().getFirst();
        assertThat(saved.getRole()).isEqualTo(DEFAULT_ROLE);
        assertThat(saved.getAmount()).isEqualByComparingTo(DEFAULT_AMOUNT);
        assertThat(saved.getCurrency()).isEqualTo(DEFAULT_CURRENCY);
        assertThat(saved.getValidFrom()).isEqualTo(DEFAULT_VALID_FROM);
    }

    @Test
    void createWageRateWithExistingIdIsRejected() throws Exception {
        wageRate.setId(UUID.randomUUID().toString());
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);
        long databaseSizeBeforeCreate = wageRateRepository.count();

        restWageRateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(wageRateDTO)))
            .andExpect(status().isBadRequest());

        assertThat(wageRateRepository.count()).isEqualTo(databaseSizeBeforeCreate);
    }

    @Test
    void checkRoleIsRequired() throws Exception {
        wageRate.setRole(null);
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);

        restWageRateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(wageRateDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkAmountIsRequired() throws Exception {
        wageRate.setAmount(null);
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);

        restWageRateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(wageRateDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkValidFromIsRequired() throws Exception {
        wageRate.setValidFrom(null);
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);

        restWageRateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(wageRateDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getAllWageRates() throws Exception {
        wageRateRepository.save(wageRate);

        restWageRateMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.[*].id").value(hasItem(wageRate.getId())))
            .andExpect(jsonPath("$.[*].role").value(hasItem(DEFAULT_ROLE.toString())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)));
    }

    @Test
    void getAllWageRatesIsPaginated() throws Exception {
        wageRateRepository.save(wageRate);

        restWageRateMockMvc
            .perform(get(ENTITY_API_URL))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(header().exists("Link"));
    }

    @Test
    void getWageRate() throws Exception {
        wageRateRepository.save(wageRate);

        restWageRateMockMvc
            .perform(get(ENTITY_API_URL_ID, wageRate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(wageRate.getId()))
            .andExpect(jsonPath("$.role").value(DEFAULT_ROLE.toString()))
            .andExpect(jsonPath("$.validFrom").value(DEFAULT_VALID_FROM.toString()));
    }

    @Test
    void getNonExistingWageRate() throws Exception {
        restWageRateMockMvc.perform(get(ENTITY_API_URL_ID, UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @Test
    void putExistingWageRate() throws Exception {
        wageRateRepository.save(wageRate);
        long databaseSizeBeforeUpdate = wageRateRepository.count();

        WageRate updated = wageRateRepository.findById(wageRate.getId()).orElseThrow();
        updated.role(UPDATED_ROLE).amount(UPDATED_AMOUNT).validFrom(UPDATED_VALID_FROM);
        WageRateDTO wageRateDTO = wageRateMapper.toDto(updated);

        restWageRateMockMvc
            .perform(
                put(ENTITY_API_URL_ID, wageRateDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(wageRateDTO))
            )
            .andExpect(status().isOk());

        assertThat(wageRateRepository.count()).isEqualTo(databaseSizeBeforeUpdate);
        WageRate saved = wageRateRepository.findById(wageRate.getId()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UPDATED_ROLE);
        assertThat(saved.getAmount()).isEqualByComparingTo(UPDATED_AMOUNT);
    }

    @Test
    void putWithIdMismatchIsRejected() throws Exception {
        wageRateRepository.save(wageRate);
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);

        restWageRateMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(wageRateDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void putNonExistingWageRateIsRejected() throws Exception {
        wageRate.setId(UUID.randomUUID().toString());
        WageRateDTO wageRateDTO = wageRateMapper.toDto(wageRate);

        restWageRateMockMvc
            .perform(
                put(ENTITY_API_URL_ID, wageRateDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(wageRateDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void patchWageRateAppliesOnlyNonNullFields() throws Exception {
        wageRateRepository.save(wageRate);

        WageRateDTO patch = new WageRateDTO();
        patch.setId(wageRate.getId());
        patch.setAmount(UPDATED_AMOUNT);

        restWageRateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, patch.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(patch))
            )
            .andExpect(status().isOk());

        WageRate saved = wageRateRepository.findById(wageRate.getId()).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo(UPDATED_AMOUNT);
        // untouched by the patch
        assertThat(saved.getRole()).isEqualTo(DEFAULT_ROLE);
        assertThat(saved.getValidFrom()).isEqualTo(DEFAULT_VALID_FROM);
    }

    @Test
    void deleteWageRate() throws Exception {
        wageRateRepository.save(wageRate);
        long databaseSizeBeforeDelete = wageRateRepository.count();

        restWageRateMockMvc.perform(delete(ENTITY_API_URL_ID, wageRate.getId())).andExpect(status().isNoContent());

        assertThat(wageRateRepository.count()).isEqualTo(databaseSizeBeforeDelete - 1);
    }

    /**
     * The configuration screen leads with one row per role, and it must be the row in force — not
     * the newest row overall, and not the first one stored.
     */
    @Test
    void getCurrentWageRatesReturnsTheRateInForcePerRole() throws Exception {
        wageRateRepository.saveAll(
            List.of(
                new WageRate()
                    .role(ProfessionalRole.DOCTOR)
                    .amount(new BigDecimal(500))
                    .currency("GHS")
                    .validFrom(LocalDate.of(2026, 1, 1)),
                new WageRate()
                    .role(ProfessionalRole.DOCTOR)
                    .amount(new BigDecimal(550))
                    .currency("GHS")
                    .validFrom(LocalDate.of(2026, 6, 1)),
                // not yet in force on the asOf date below
                new WageRate()
                    .role(ProfessionalRole.DOCTOR)
                    .amount(new BigDecimal(600))
                    .currency("GHS")
                    .validFrom(LocalDate.of(2027, 1, 1)),
                new WageRate().role(ProfessionalRole.NURSE).amount(new BigDecimal(300)).currency("GHS").validFrom(LocalDate.of(2026, 1, 1))
            )
        );

        restWageRateMockMvc
            .perform(get(ENTITY_API_URL + "/current?asOf=2026-08-18"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.role == 'DOCTOR')].amount").value(hasItem(550)))
            .andExpect(jsonPath("$[?(@.role == 'NURSE')].amount").value(hasItem(300)));
    }

    /**
     * A role nobody has priced yet is absent from the current rates, not present at zero — the
     * console has to be able to tell "not configured" from "free".
     */
    @Test
    void getCurrentWageRatesOmitsUnpricedRoles() throws Exception {
        wageRateRepository.save(
            new WageRate().role(ProfessionalRole.DOCTOR).amount(new BigDecimal(500)).currency("GHS").validFrom(LocalDate.of(2026, 1, 1))
        );

        restWageRateMockMvc
            .perform(get(ENTITY_API_URL + "/current?asOf=2026-08-18"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].role").value("DOCTOR"));
    }

    @Test
    void getWageRateHistoryIsNewestFirst() throws Exception {
        wageRateRepository.saveAll(
            List.of(
                new WageRate()
                    .role(ProfessionalRole.DOCTOR)
                    .amount(new BigDecimal(500))
                    .currency("GHS")
                    .validFrom(LocalDate.of(2026, 1, 1)),
                new WageRate()
                    .role(ProfessionalRole.DOCTOR)
                    .amount(new BigDecimal(550))
                    .currency("GHS")
                    .validFrom(LocalDate.of(2026, 6, 1)),
                new WageRate().role(ProfessionalRole.NURSE).amount(new BigDecimal(300)).currency("GHS").validFrom(LocalDate.of(2026, 1, 1))
            )
        );

        restWageRateMockMvc
            .perform(get(ENTITY_API_URL + "/history/{role}", ProfessionalRole.DOCTOR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].validFrom").value("2026-06-01"))
            .andExpect(jsonPath("$[1].validFrom").value("2026-01-01"));
    }
}
