package com.bone.masterdata.application.query.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataQualityReportDTO {
    private Long id;
    private Long qualityCheckId;
    private Long masterDataEntityId;
    private String reportData;
    private Integer issueCount;
    private LocalDateTime createdAt;
    private String status;
    private Integer totalRecords;
    private Integer passedRecords;
    private Integer failedRecords;
}