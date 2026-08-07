package net.jojoaddison.domain;

import static net.jojoaddison.domain.UserOptionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UserOptionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(UserOption.class);
        UserOption userOption1 = getUserOptionSample1();
        UserOption userOption2 = new UserOption();
        assertThat(userOption1).isNotEqualTo(userOption2);

        userOption2.setId(userOption1.getId());
        assertThat(userOption1).isEqualTo(userOption2);

        userOption2 = getUserOptionSample2();
        assertThat(userOption1).isNotEqualTo(userOption2);
    }
}
