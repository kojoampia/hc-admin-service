package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Facility;
import net.jojoaddison.service.dto.FacilityDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Facility} and its DTO {@link FacilityDTO}.
 */
@Mapper(componentModel = "spring")
public interface FacilityMapper extends EntityMapper<FacilityDTO, Facility> {}
