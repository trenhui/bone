package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发布域模板版本（UC-P2）：快照当前 schema 到 mdm_template_version 并推进 current_version。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishDomainTemplateVersionCommand {

  @NotNull(message = "id: 模板ID不能为空")
  private Long id;

  /** 版本号（semver，如 1.1.0），同模板下唯一。 */
  @NotBlank(message = "versionNumber: 版本号不能为空")
  private String versionNumber;

  private String changeLog;
}
