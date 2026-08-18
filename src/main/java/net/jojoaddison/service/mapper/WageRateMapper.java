package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.WageRate;
import net.jojoaddison.service.dto.WageRateDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link WageRate} and its DTO {@link WageRateDTO}.
 */
@Mapper(componentModel = "spring")
public interface WageRateMapper extends EntityMapper<WageRateDTO, WageRate> {
    /**
     * The audit fields are stamped by {@code AuditingEntityCallback} and travel outward only —
     * ignoring them here is what stops a caller setting them through the DTO.
     */
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    WageRate toEntity(WageRateDTO dto);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Override
    void partialUpdate(@MappingTarget WageRate entity, WageRateDTO dto);
}
