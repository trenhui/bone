package com.bone.tpa.claim.application.converter;

import com.bone.tpa.claim.application.dto.ClaimTrackLogDTO;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 操作记录对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimTrackLogConverter {
    ClaimTrackLog toEntity(ClaimTrackLogDTO claimTrackLogDTO);

    ClaimTrackLogDTO toDTO(ClaimTrackLog claimTrackLog);
}
