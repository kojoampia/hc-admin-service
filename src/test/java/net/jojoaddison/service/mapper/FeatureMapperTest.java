package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.FeatureAsserts.*;
import static net.jojoaddison.domain.FeatureTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeatureMapperTest {

    private FeatureMapper featureMapper;

    @BeforeEach
    void setUp() {
        featureMapper = new FeatureMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getFeatureSample1();
        var actual = featureMapper.toEntity(featureMapper.toDto(expected));
        assertFeatureAllPropertiesEquals(expected, actual);
    }
}
