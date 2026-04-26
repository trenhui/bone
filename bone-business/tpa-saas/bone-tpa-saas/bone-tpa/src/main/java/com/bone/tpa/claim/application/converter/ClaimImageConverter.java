package com.bone.tpa.claim.application.converter;

import com.bone.tpa.claim.application.dto.ClaimImageDTO;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 赔案影像件对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimImageConverter {
    ClaimImage toEntity(ClaimImageDTO claimImageDTO);

    ClaimImageDTO toDTO(ClaimImage claimImage);
}
