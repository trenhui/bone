package com.bone.tpa.claim.application.converter;

import com.bone.tpa.claim.application.dto.FileUploadRecordDTO;
import com.bone.tpa.sdk.claim.model.FileUploadRecord;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileUploadRecordConverter {

    FileUploadRecord toEntity(FileUploadRecordDTO fileUploadRecordDTO);

    FileUploadRecordDTO toDTO(FileUploadRecord fileUploadRecord);
}
