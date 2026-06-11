package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.HCProfileAsserts.*;
import static net.jojoaddison.domain.HCProfileTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HCProfileMapperTest {

    private HCProfileMapper profileMapper;

    @BeforeEach
    void setUp() {
        profileMapper = new HCProfileMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getProfileSample1();
        var actual = profileMapper.toEntity(profileMapper.toDto(expected));
        assertProfileAllPropertiesEquals(expected, actual);
    }
}
