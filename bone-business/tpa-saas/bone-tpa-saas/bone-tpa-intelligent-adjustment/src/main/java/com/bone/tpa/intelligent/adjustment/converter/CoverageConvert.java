package com.bone.tpa.intelligent.adjustment.converter;

import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CoverageConvert {

    Coverage toEntity(CoverageDTO dto);

    List<Coverage> toEntityList(List<CoverageDTO> dtoList);

    List<CoverageDTO> toDtoList(List<Coverage> dbList);
}
