package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.DocumentItem;
import net.jojoaddison.service.dto.DocumentItemDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DocumentItem} and its DTO {@link DocumentItemDTO}.
 */
@Mapper(componentModel = "spring")
public interface DocumentItemMapper extends EntityMapper<DocumentItemDTO, DocumentItem> {}
