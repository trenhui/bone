package com.bone.tpa.intelligent.adjustment.converter;

import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.sdk.adjustment.model.Plan;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PlanConvert {

    Plan toEntity(PlanDTO dto);

    List<PlanDTO> toDtoList(List<Plan> plan);


}
