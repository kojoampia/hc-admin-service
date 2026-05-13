package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.SystemCatalogAsserts.*;
import static net.jojoaddison.domain.SystemCatalogTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemCatalogMapperTest {

    private SystemCatalogMapper systemCatalogMapper;

    @BeforeEach
    void setUp() {
        systemCatalogMapper = new SystemCatalogMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getSystemCatalogSample1();
        var actual = systemCatalogMapper.toEntity(systemCatalogMapper.toDto(expected));
        assertSystemCatalogAllPropertiesEquals(expected, actual);
    }
}
