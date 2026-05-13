package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PricingPlanDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PricingPlanDTO.class);
        PricingPlanDTO pricingPlanDTO1 = new PricingPlanDTO();
        pricingPlanDTO1.setId("id1");
        PricingPlanDTO pricingPlanDTO2 = new PricingPlanDTO();
        assertThat(pricingPlanDTO1).isNotEqualTo(pricingPlanDTO2);
        pricingPlanDTO2.setId(pricingPlanDTO1.getId());
        assertThat(pricingPlanDTO1).isEqualTo(pricingPlanDTO2);
        pricingPlanDTO2.setId("id2");
        assertThat(pricingPlanDTO1).isNotEqualTo(pricingPlanDTO2);
        pricingPlanDTO1.setId(null);
        assertThat(pricingPlanDTO1).isNotEqualTo(pricingPlanDTO2);
    }
}
