package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.FacilityAsserts.*;
import static net.jojoaddison.domain.FacilityTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FacilityMapperTest {

    private FacilityMapper facilityMapper;

    @BeforeEach
    void setUp() {
        facilityMapper = new FacilityMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getFacilitySample1();
        var actual = facilityMapper.toEntity(facilityMapper.toDto(expected));
        assertFacilityAllPropertiesEquals(expected, actual);
    }
}
