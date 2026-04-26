package com.bone.tpa.submission.application.converter;

import com.bone.tpa.sdk.submission.model.ClaimDocument;
import com.bone.tpa.submission.application.dto.ClaimDocumentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimDocumentConverter {
    ClaimDocumentDTO toDTO(ClaimDocument document);

    ClaimDocument toEntity(ClaimDocumentDTO documentDTO);
}
