package com.bone.tpa.intelligent.adjustment.converter;

import com.bone.tpa.intelligent.adjustment.dto.LiabilityShareDTO;
import com.bone.tpa.intelligent.adjustment.dto.LiabilitySharingRelationDTO;
import com.bone.tpa.sdk.adjustment.model.LiabilitySharing;
import com.bone.tpa.sdk.adjustment.model.LiabilitySharingRelation;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LiabilityShareConvert   {

    List<LiabilityShareDTO> toDtoList(List<LiabilitySharing> entityList);


    List<LiabilitySharing> toEntityList(List<LiabilityShareDTO> dtoList);



    List<LiabilitySharingRelationDTO> toRelationDtoList(List<LiabilitySharingRelation> entityList);

    List<LiabilitySharingRelation> toRelationEntityList(List<LiabilitySharingRelationDTO> dtoList);

}
