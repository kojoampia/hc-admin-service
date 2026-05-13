package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.PatientPlan;
import net.jojoaddison.service.dto.PatientPlanDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link PatientPlan} and its DTO {@link PatientPlanDTO}.
 */
@Mapper(componentModel = "spring")
public interface PatientPlanMapper extends EntityMapper<PatientPlanDTO, PatientPlan> {}
