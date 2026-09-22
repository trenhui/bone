package com.bone.masterdata.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDataQualityRuleCommand {
  private Long id;
  private String name;
  private String type;
  private String expression;
  private String severity;
  private String description;
}
