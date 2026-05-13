package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Feature;
import net.jojoaddison.service.dto.FeatureDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Feature} and its DTO {@link FeatureDTO}.
 */
@Mapper(componentModel = "spring")
public interface FeatureMapper extends EntityMapper<FeatureDTO, Feature> {}
