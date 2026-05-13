package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.service.dto.DutyRosterDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DutyRoster} and its DTO {@link DutyRosterDTO}.
 */
@Mapper(componentModel = "spring")
public interface DutyRosterMapper extends EntityMapper<DutyRosterDTO, DutyRoster> {}
