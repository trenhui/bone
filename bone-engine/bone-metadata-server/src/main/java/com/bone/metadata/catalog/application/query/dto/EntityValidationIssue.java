package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

/**
 * 建模期静态校验问题（建模工作台闭环：发布前自检的原子项）。
 *
 * <p>level 分级语义：{@code ERROR}=阻断发布（发布按钮禁用）；{@code WARNING}=不阻断但建议处理； {@code
 * INFO}=提示性信息（如已发布实体的受限编辑说明）。
 */
@Data
public class EntityValidationIssue {

  public static final String LEVEL_ERROR = "ERROR";
  public static final String LEVEL_WARNING = "WARNING";
  public static final String LEVEL_INFO = "INFO";

  /** ERROR / WARNING / INFO */
  private String level;

  /** 问题码（如 ENTITY_CODE_INVALID / FIELD_CODE_DUPLICATE / PHYSICAL_DRIFT） */
  private String code;

  private String message;

  /** 字段级问题才填，便于前端点击跳转到问题字段 */
  private Long fieldId;

  private String fieldName;

  public static EntityValidationIssue error(String code, String message) {
    return of(LEVEL_ERROR, code, message, null, null);
  }

  public static EntityValidationIssue warning(String code, String message) {
    return of(LEVEL_WARNING, code, message, null, null);
  }

  public static EntityValidationIssue info(String code, String message) {
    return of(LEVEL_INFO, code, message, null, null);
  }

  public static EntityValidationIssue fieldError(
      String code, String message, Long fieldId, String fieldName) {
    return of(LEVEL_ERROR, code, message, fieldId, fieldName);
  }

  public static EntityValidationIssue fieldWarning(
      String code, String message, Long fieldId, String fieldName) {
    return of(LEVEL_WARNING, code, message, fieldId, fieldName);
  }

  private static EntityValidationIssue of(
      String level, String code, String message, Long fieldId, String fieldName) {
    EntityValidationIssue issue = new EntityValidationIssue();
    issue.level = level;
    issue.code = code;
    issue.message = message;
    issue.fieldId = fieldId;
    issue.fieldName = fieldName;
    return issue;
  }
}
