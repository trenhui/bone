package com.bone.masterdata.application.query.dto;

import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataQualityRuleDTO {
    private Long id;
    private Long masterDataEntityId;
    private String name;
    private String type;
    private String expression;
    private RuleSeverity severity;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}