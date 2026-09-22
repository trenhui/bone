package com.bone.masterdata.application.command;

import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDataQualityRuleCommand {
  private Long masterDataEntityId;
  private String name;
  private String type;
  private String expression;
  private RuleSeverity severity;
  private String description;
}
