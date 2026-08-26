package com.bone.metadata.engine.domain.metadata;

import lombok.Getter;
import lombok.Setter;

/** 业务规则元数据模型类 */
@Getter
@Setter
public class BusinessRuleMetadata {

  // 规则ID（统一建模兼容）
  private String id;

  // 规则API名称（统一建模兼容）
  private String apiName;

  // 规则名称
  private String name;

  // 关联实体API名称（统一建模兼容）
  private String entityName;

  // 规则类型（统一建模兼容）
  private String ruleType;

  // 是否启用（统一建模兼容）
  private boolean active = true;

  // 规则所属领域（统一建模兼容）
  private String domain;

  // 触发事件列表（统一建模兼容）
  private java.util.List<String> triggerEvents = new java.util.ArrayList<>();

  // 规则标签
  private String label;

  // 规则描述
  private String description;

  // 规则条件
  private String condition;

  // 规则动作
  private String action;

  // 执行时机
  private String
      executionTiming; // BEFORE_INSERT, AFTER_INSERT, BEFORE_UPDATE, AFTER_UPDATE, BEFORE_DELETE,
  // AFTER_DELETE

  // 是否启用
  private boolean enabled = true;

  // 严重程度
  private String severity = "ERROR";

  // 错误消息
  private String errorMessage = "";

  // 关联字段名
  private String fieldName = "";

  /** 获取表达式（作为getCondition的别名） */
  public String getExpression() {
    return this.condition;
  }

  /** 设置表达式（作为setCondition的别名） */
  public void setExpression(String expression) {
    this.condition = expression;
  }

  /** 获取字段名 */
  public String getFieldName() {
    return this.fieldName;
  }

  /** 设置字段名 */
  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  /** 获取严重程度 */
  public String getSeverity() {
    return this.severity;
  }

  /** 设置严重程度 */
  public void setSeverity(String severity) {
    this.severity = severity;
  }

  /** 获取错误消息 */
  public String getErrorMessage() {
    return this.errorMessage;
  }

  /** 设置错误消息 */
  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  /** 添加触发事件 */
  public void addTriggerEvent(String event) {
    if (triggerEvents == null) {
      triggerEvents = new java.util.ArrayList<>();
    }
    if (!triggerEvents.contains(event)) {
      triggerEvents.add(event);
    }
  }

  /** 判断是否触发指定事件 */
  public boolean triggersEvent(String event) {
    return triggerEvents != null && triggerEvents.contains(event);
  }

  /** 获取依赖字段列表（统一建模兼容） */
  public java.util.List<String> getDependentFields() {
    java.util.List<String> deps = new java.util.ArrayList<>();
    if (fieldName != null && !fieldName.isEmpty()) {
      deps.add(fieldName);
    }
    return deps;
  }
}
