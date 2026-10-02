package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMetaFieldCommand {
  // entityId 由 URL 路径 {entityId} 注入（见 MetaFieldCatalogController），不由 body 提供，故不加 @NotNull
  private Long entityId;
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  @NotBlank private String type;

  private Integer length;
  private Boolean required;
  private Boolean unique;
  private String defaultValue;
  private String comment;

  // ===== 业界元数据 / 数据治理属性 =====
  /** 数据分级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET/TOP_SECRET（公开/内部/秘密/机密/绝密） */
  private String dataClassification;

  /** 是否个人敏感信息(PII) */
  private Boolean pii;

  /** 敏感级别：L1/L2/L3/L4（一般/较敏感/敏感/极敏感） */
  private String sensitivityLevel;

  /** 数据管家/责任人 */
  private String dataSteward;

  /** 业务术语/数据标准 */
  private String businessTerm;

  /** 来源系统（血缘） */
  private String sourceSystem;

  /** 枚举值/标准码表（JSON 文本，复用 meta_field.enum_values） */
  private String enumValues;

  /** 校验规则/质量规则（JSON 文本，复用 meta_field.validation_rules） */
  private String validationRules;

  private Integer sortOrder;

  /** fieldType 别名，兼容前端传参 */
  private String fieldType;
}
