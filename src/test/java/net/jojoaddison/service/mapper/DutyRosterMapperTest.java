package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.DutyRosterAsserts.*;
import static net.jojoaddison.domain.DutyRosterTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DutyRosterMapperTest {

    private DutyRosterMapper dutyRosterMapper;

    @BeforeEach
    void setUp() {
        dutyRosterMapper = new DutyRosterMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDutyRosterSample1();
        var actual = dutyRosterMapper.toEntity(dutyRosterMapper.toDto(expected));
        assertDutyRosterAllPropertiesEquals(expected, actual);
    }
}
