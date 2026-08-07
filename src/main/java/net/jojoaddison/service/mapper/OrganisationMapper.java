package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.service.dto.AddressDTO;
import net.jojoaddison.service.dto.OrganisationDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Organisation} and its DTO {@link OrganisationDTO}.
 */
@Mapper(componentModel = "spring")
public interface OrganisationMapper extends EntityMapper<OrganisationDTO, Organisation> {
    @Mapping(target = "address", source = "address", qualifiedByName = "addressId")
    OrganisationDTO toDto(Organisation s);

    @Named("addressId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    AddressDTO toDtoAddressId(Address address);
}
