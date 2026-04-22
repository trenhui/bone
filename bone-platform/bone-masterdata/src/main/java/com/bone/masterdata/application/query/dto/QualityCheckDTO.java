package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class QualityCheckDTO {
    private Long id;
    private Long masterDataEntityId;
    private Date startTime;
    private Date endTime;
    private String status;
    private Integer totalRecords;
    private Integer passedRecords;
    private Integer failedRecords;
}
