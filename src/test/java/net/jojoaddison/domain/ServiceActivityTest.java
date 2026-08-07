package net.jojoaddison.domain;

import static net.jojoaddison.domain.CategoryTestSamples.*;
import static net.jojoaddison.domain.ServiceActivityTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ServiceActivityTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ServiceActivity.class);
        ServiceActivity serviceActivity1 = getServiceActivitySample1();
        ServiceActivity serviceActivity2 = new ServiceActivity();
        assertThat(serviceActivity1).isNotEqualTo(serviceActivity2);

        serviceActivity2.setId(serviceActivity1.getId());
        assertThat(serviceActivity1).isEqualTo(serviceActivity2);

        serviceActivity2 = getServiceActivitySample2();
        assertThat(serviceActivity1).isNotEqualTo(serviceActivity2);
    }

    @Test
    void categoryTest() {
        ServiceActivity serviceActivity = getServiceActivityRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        serviceActivity.setCategory(categoryBack);
        assertThat(serviceActivity.getCategory()).isEqualTo(categoryBack);

        serviceActivity.category(null);
        assertThat(serviceActivity.getCategory()).isNull();
    }
}
