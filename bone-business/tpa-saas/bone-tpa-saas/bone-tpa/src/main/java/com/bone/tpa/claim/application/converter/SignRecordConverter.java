package com.bone.tpa.claim.application.converter;

import com.bone.tpa.claim.application.dto.SignRecordDTO;
import com.bone.tpa.sdk.claim.model.SignRecord;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 签收记录对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SignRecordConverter {
    SignRecord toEntity(SignRecordDTO signRecordDTO);

    SignRecordDTO toDTO(SignRecord signRecord);
}
