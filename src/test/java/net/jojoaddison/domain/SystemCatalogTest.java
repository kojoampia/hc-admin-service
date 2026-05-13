package net.jojoaddison.domain;

import static net.jojoaddison.domain.SystemCatalogTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SystemCatalogTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(SystemCatalog.class);
        SystemCatalog systemCatalog1 = getSystemCatalogSample1();
        SystemCatalog systemCatalog2 = new SystemCatalog();
        assertThat(systemCatalog1).isNotEqualTo(systemCatalog2);

        systemCatalog2.setId(systemCatalog1.getId());
        assertThat(systemCatalog1).isEqualTo(systemCatalog2);

        systemCatalog2 = getSystemCatalogSample2();
        assertThat(systemCatalog1).isNotEqualTo(systemCatalog2);
    }
}
