package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.SystemCatalog;
import net.jojoaddison.service.dto.SystemCatalogDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link SystemCatalog} and its DTO {@link SystemCatalogDTO}.
 */
@Mapper(componentModel = "spring")
public interface SystemCatalogMapper extends EntityMapper<SystemCatalogDTO, SystemCatalog> {}
