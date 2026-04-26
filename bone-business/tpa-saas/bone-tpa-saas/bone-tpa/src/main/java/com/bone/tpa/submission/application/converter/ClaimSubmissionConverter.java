package com.bone.tpa.submission.application.converter;

import com.bone.tpa.sdk.submission.model.ClaimSubmission;
import com.bone.tpa.submission.application.dto.ClaimSubmissionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClaimSubmissionConverter {
    ClaimSubmissionDTO toDTO(ClaimSubmission submission);

    ClaimSubmission toEntity(ClaimSubmissionDTO submissionDTO);
}
