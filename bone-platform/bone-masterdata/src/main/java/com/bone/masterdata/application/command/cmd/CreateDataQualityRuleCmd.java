package com.bone.masterdata.application.command.cmd;

import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateDataQualityRuleCmd {
    private Long masterDataEntityId;
    private String name;
    private String type;
    private String expression;
    private RuleSeverity severity;
    private String description;
}