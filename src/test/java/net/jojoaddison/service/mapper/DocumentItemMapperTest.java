package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.DocumentItemAsserts.*;
import static net.jojoaddison.domain.DocumentItemTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentItemMapperTest {

    private DocumentItemMapper documentItemMapper;

    @BeforeEach
    void setUp() {
        documentItemMapper = new DocumentItemMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDocumentItemSample1();
        var actual = documentItemMapper.toEntity(documentItemMapper.toDto(expected));
        assertDocumentItemAllPropertiesEquals(expected, actual);
    }
}
