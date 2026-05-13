package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.PatientPlanAsserts.*;
import static net.jojoaddison.domain.PatientPlanTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientPlanMapperTest {

    private PatientPlanMapper patientPlanMapper;

    @BeforeEach
    void setUp() {
        patientPlanMapper = new PatientPlanMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPatientPlanSample1();
        var actual = patientPlanMapper.toEntity(patientPlanMapper.toDto(expected));
        assertPatientPlanAllPropertiesEquals(expected, actual);
    }
}
