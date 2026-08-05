package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Photo;
import net.jojoaddison.service.dto.PhotoDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Photo} and its DTO {@link PhotoDTO}.
 */
@Mapper(componentModel = "spring")
public interface PhotoMapper extends EntityMapper<PhotoDTO, Photo> {}
