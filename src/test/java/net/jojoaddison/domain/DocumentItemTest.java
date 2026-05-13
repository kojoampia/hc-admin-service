package net.jojoaddison.domain;

import static net.jojoaddison.domain.DocumentItemTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DocumentItemTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DocumentItem.class);
        DocumentItem documentItem1 = getDocumentItemSample1();
        DocumentItem documentItem2 = new DocumentItem();
        assertThat(documentItem1).isNotEqualTo(documentItem2);

        documentItem2.setId(documentItem1.getId());
        assertThat(documentItem1).isEqualTo(documentItem2);

        documentItem2 = getDocumentItemSample2();
        assertThat(documentItem1).isNotEqualTo(documentItem2);
    }
}
