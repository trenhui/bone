package com.bone.tpa.claim.application.converter;

import com.bone.tpa.claim.application.dto.SignRecordTrackLogDTO;
import com.bone.tpa.sdk.claim.model.SignRecordTrackLog;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 签收记录对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SignRecordTrackLogConverter {
    SignRecordTrackLog toEntity(SignRecordTrackLogDTO signRecordDTO);

    SignRecordTrackLogDTO toDTO(SignRecordTrackLog signRecord);
}
