package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 建质量整改工单命令（G11）。severity：HIGH/MEDIUM/LOW；dueAt：ISO-8601 日期时间。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQualityIssueCommand {

  @NotNull(message = "masterDataEntityId: 主数据实体ID不能为空")
  private Long masterDataEntityId;

  private Long recordId;
  private Long checkId;
  private Long ruleId;

  @NotBlank(message = "issueDesc: 问题描述不能为空")
  private String issueDesc;

  private String severity;
  private Long assigneeId;
  private String dueAt;
}
