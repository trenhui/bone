package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMetaEntityCommand {
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  private String description;
  @NotBlank private String tableName;
  private Integer type;

  /** 0-GENERATIVE（默认） 1-RUNTIME */
  private Integer deliveryMode;

  private String icon;

  /** 所属模块（null 表示未分组） */
  private Long moduleId;

  /**
   * 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）。
   * 非空时平台在业务数据写入时按「前缀-日期-序号」生成单据号；为空 = 不启用。 仅对 RUNTIME（自动生成界面）实体生效，GENERATIVE 实体不落物理表故不注入。
   */
  private String autoNumber;
}
