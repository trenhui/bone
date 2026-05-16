package com.bone.metadata.engine.security;

import java.util.ArrayList;
import java.util.List;

/** 数据脱敏规则类 用于定义不同类型的数据脱敏策略 */
public class DataMaskingRule {
  private String fieldName;
  private String type;
  private String pattern;
  private String replacement;
  private List<String> exceptions = new ArrayList<>();

  // 脱敏类型常量
  public static final String TYPE_PHONE = "PHONE";
  public static final String TYPE_ID_CARD = "ID_CARD";
  public static final String TYPE_BANK_CARD = "BANK_CARD";
  public static final String TYPE_NAME = "NAME";
  public static final String TYPE_EMAIL = "EMAIL";
  public static final String TYPE_ADDRESS = "ADDRESS";
  public static final String TYPE_GENERAL = "GENERAL";

  // getters and setters
  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  public String getReplacement() {
    return replacement;
  }

  public void setReplacement(String replacement) {
    this.replacement = replacement;
  }

  public List<String> getExceptions() {
    return exceptions;
  }

  public void setExceptions(List<String> exceptions) {
    this.exceptions = exceptions != null ? exceptions : new ArrayList<>();
  }

  /** 添加例外角色 */
  public void addException(String role) {
    if (role != null) {
      this.exceptions.add(role);
    }
  }

  @Override
  public String toString() {
    return "DataMaskingRule{"
        + "fieldName='"
        + fieldName
        + '\''
        + ", type='"
        + type
        + '\''
        + ", pattern='"
        + pattern
        + '\''
        + ", replacement='"
        + replacement
        + '\''
        + ", exceptions="
        + exceptions
        + '}';
  }
}
