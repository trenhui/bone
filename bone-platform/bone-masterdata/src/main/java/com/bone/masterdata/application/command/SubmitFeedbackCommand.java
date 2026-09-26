package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 下游反馈提交命令（G17）。feedbackType：CORRECTION / ADDITION / DUPLICATE。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitFeedbackCommand {

  @NotNull(message = "masterDataEntityId: 主数据实体ID不能为空")
  private Long masterDataEntityId;

  private Long recordId;
  private Long appId;
  private String feedbackType;

  @NotBlank(message = "content: 反馈内容不能为空")
  private String content;

  private String suggestedData;
}
