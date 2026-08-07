package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Team;
import net.jojoaddison.service.dto.ProfessionalDTO;
import net.jojoaddison.service.dto.TeamDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Team} and its DTO {@link TeamDTO}.
 */
@Mapper(componentModel = "spring")
public interface TeamMapper extends EntityMapper<TeamDTO, Team> {
    @Mapping(target = "supervisor", source = "supervisor", qualifiedByName = "professionalLicenceNumber")
    TeamDTO toDto(Team s);

    @Named("professionalLicenceNumber")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "licenceNumber", source = "licenceNumber")
    ProfessionalDTO toDtoProfessionalLicenceNumber(Professional professional);
}
