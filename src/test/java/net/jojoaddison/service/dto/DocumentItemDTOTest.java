package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DocumentItemDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DocumentItemDTO.class);
        DocumentItemDTO documentItemDTO1 = new DocumentItemDTO();
        documentItemDTO1.setId("id1");
        DocumentItemDTO documentItemDTO2 = new DocumentItemDTO();
        assertThat(documentItemDTO1).isNotEqualTo(documentItemDTO2);
        documentItemDTO2.setId(documentItemDTO1.getId());
        assertThat(documentItemDTO1).isEqualTo(documentItemDTO2);
        documentItemDTO2.setId("id2");
        assertThat(documentItemDTO1).isNotEqualTo(documentItemDTO2);
        documentItemDTO1.setId(null);
        assertThat(documentItemDTO1).isNotEqualTo(documentItemDTO2);
    }
}
