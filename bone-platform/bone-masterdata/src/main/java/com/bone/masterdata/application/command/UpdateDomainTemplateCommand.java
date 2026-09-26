package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDomainTemplateCommand {

  @NotNull(message = "id: 模板ID不能为空")
  private Long id;

  private String domainName;
  private String description;
  private String fieldSchema;
  private String ruleSchema;
  private String categorySchema;
  private String defaultGovernanceTier;
}
