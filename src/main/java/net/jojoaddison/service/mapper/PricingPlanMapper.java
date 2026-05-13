package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.PricingPlan;
import net.jojoaddison.service.dto.PricingPlanDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link PricingPlan} and its DTO {@link PricingPlanDTO}.
 */
@Mapper(componentModel = "spring")
public interface PricingPlanMapper extends EntityMapper<PricingPlanDTO, PricingPlan> {}
