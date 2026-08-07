package net.jojoaddison.domain;

import static net.jojoaddison.domain.CategoryTestSamples.*;
import static net.jojoaddison.domain.ServiceActivityTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Category.class);
        Category category1 = getCategorySample1();
        Category category2 = new Category();
        assertThat(category1).isNotEqualTo(category2);

        category2.setId(category1.getId());
        assertThat(category1).isEqualTo(category2);

        category2 = getCategorySample2();
        assertThat(category1).isNotEqualTo(category2);
    }

    @Test
    void activityTest() {
        Category category = getCategoryRandomSampleGenerator();
        ServiceActivity serviceActivityBack = getServiceActivityRandomSampleGenerator();

        category.addActivity(serviceActivityBack);
        assertThat(category.getActivities()).containsOnly(serviceActivityBack);
        assertThat(serviceActivityBack.getCategory()).isEqualTo(category);

        category.removeActivity(serviceActivityBack);
        assertThat(category.getActivities()).doesNotContain(serviceActivityBack);
        assertThat(serviceActivityBack.getCategory()).isNull();

        category.activities(new HashSet<>(Set.of(serviceActivityBack)));
        assertThat(category.getActivities()).containsOnly(serviceActivityBack);
        assertThat(serviceActivityBack.getCategory()).isEqualTo(category);

        category.setActivities(new HashSet<>());
        assertThat(category.getActivities()).doesNotContain(serviceActivityBack);
        assertThat(serviceActivityBack.getCategory()).isNull();
    }
}
