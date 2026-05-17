package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;

public record QualityReportDTO(
        Long id,
        Long qualityCheckId,
        String reportData,
        Integer issueCount,
        LocalDateTime createdAt) {
}
