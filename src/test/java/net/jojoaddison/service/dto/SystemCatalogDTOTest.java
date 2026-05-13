package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SystemCatalogDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(SystemCatalogDTO.class);
        SystemCatalogDTO systemCatalogDTO1 = new SystemCatalogDTO();
        systemCatalogDTO1.setId("id1");
        SystemCatalogDTO systemCatalogDTO2 = new SystemCatalogDTO();
        assertThat(systemCatalogDTO1).isNotEqualTo(systemCatalogDTO2);
        systemCatalogDTO2.setId(systemCatalogDTO1.getId());
        assertThat(systemCatalogDTO1).isEqualTo(systemCatalogDTO2);
        systemCatalogDTO2.setId("id2");
        assertThat(systemCatalogDTO1).isNotEqualTo(systemCatalogDTO2);
        systemCatalogDTO1.setId(null);
        assertThat(systemCatalogDTO1).isNotEqualTo(systemCatalogDTO2);
    }
}
