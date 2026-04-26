package com.bone.tpa.claim.application.converter;

import com.bone.tpa.claim.application.dto.ClaimCopyLogDTO;
import com.bone.tpa.sdk.claim.model.ClaimCopyLog;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimCopyLogConverter {

    ClaimCopyLog toEntity(ClaimCopyLogDTO claimCopyLogDTO);

    ClaimCopyLogDTO toDTO(ClaimCopyLog claimCopyLog);

}
