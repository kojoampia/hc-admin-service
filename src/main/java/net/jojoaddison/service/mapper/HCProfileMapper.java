package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.HCProfile;
import net.jojoaddison.service.dto.HCProfileDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link HCProfile} and its DTO {@link HCProfileDTO}.
 */
@Mapper(componentModel = "spring")
public interface HCProfileMapper extends EntityMapper<HCProfileDTO, HCProfile> {}
