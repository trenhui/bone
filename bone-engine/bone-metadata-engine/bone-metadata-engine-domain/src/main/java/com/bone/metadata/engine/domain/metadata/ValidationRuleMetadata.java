package com.bone.metadata.engine.domain.metadata;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 验证规则元数据 */
@Data
@NoArgsConstructor
public class ValidationRuleMetadata {
  private String id;
  private String name;
  private String apiName;
  private String description;
  private String expression;
  private String errorMessage;
  private int order = 0;
  private boolean active = true;
  private String triggerEvent = "ALL";
  private Map<String, String> attributes = new HashMap<>();
  private String errorLocation; // 用于getFieldName()方法

  /** 获取规则名称 */
  public String getName() {
    return name;
  }

  /** 检查规则是否启用（兼容isEnabled方法） */
  public boolean isEnabled() {
    return active;
  }

  /** 获取字段名称（兼容旧方法） */
  public String getFieldName() {
    return errorLocation;
  }

  /** 获取错误消息（兼容旧方法） */
  public String getMessage() {
    return errorMessage;
  }

  /** 设置规则名称 */
  public void setName(String name) {
    this.name = name;
  }

  /** 设置错误消息 */
  public void setMessage(String message) {
    this.errorMessage = message;
  }

  /** 设置规则类型 */
  public void setType(String type) {
    // 类型字段实际上不存在，这里只是为了兼容调用
    // 可以根据需要添加实际的类型字段
  }

  /** 设置表达式 */
  public void setExpression(String expression) {
    this.expression = expression;
  }

  /** 设置字段名称 */
  public void setFieldName(String fieldName) {
    this.errorLocation = fieldName;
  }
}
