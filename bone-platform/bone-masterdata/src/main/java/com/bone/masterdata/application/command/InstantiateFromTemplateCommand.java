package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 从域模板实例化主数据实体（UC-T1 主流程 A）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantiateFromTemplateCommand {

  @NotNull(message = "templateId: 模板ID不能为空")
  private Long templateId;

  /** 租户内唯一的实体编码；缺省时由服务端按 domainCode 生成。 */
  private String entityCode;

  @NotBlank(message = "name: 实体名称不能为空")
  private String name;

  private String description;

  /** 治理等级：缺省跟随模板默认值（§4.3）。 */
  private String governanceTier;

  /** 责任归口应用ID，可空表示平台共享域（§3.2）。 */
  private Long owningAppId;

  /** 是否按模板 field_schema 同步创建字段，缺省 true。 */
  private Boolean withFields;
}
