package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.PricingPlanAsserts.*;
import static net.jojoaddison.domain.PricingPlanTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingPlanMapperTest {

    private PricingPlanMapper pricingPlanMapper;

    @BeforeEach
    void setUp() {
        pricingPlanMapper = new PricingPlanMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPricingPlanSample1();
        var actual = pricingPlanMapper.toEntity(pricingPlanMapper.toDto(expected));
        assertPricingPlanAllPropertiesEquals(expected, actual);
    }
}
