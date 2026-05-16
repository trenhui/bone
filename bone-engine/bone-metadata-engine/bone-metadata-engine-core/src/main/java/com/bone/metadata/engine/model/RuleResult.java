package com.bone.metadata.engine.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 业务规则执行结果 包含规则执行的成功/失败状态、错误信息和警告信息 */
public class RuleResult {

  /** 是否所有规则都执行成功 */
  private boolean success;

  /** 错误信息列表 */
  private List<RuleError> errors;

  /** 警告信息列表 */
  private List<RuleWarning> warnings;

  /** 规则执行过程中的附加信息 */
  private Map<String, Object> additionalInfo;

  public RuleResult() {
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
    this.additionalInfo = new java.util.HashMap<>();
    this.success = true;
  }

  public RuleResult(
      boolean success,
      List<RuleError> errors,
      List<RuleWarning> warnings,
      Map<String, Object> additionalInfo) {
    this.success = success;
    this.errors = (errors != null) ? errors : new ArrayList<>();
    this.warnings = (warnings != null) ? warnings : new ArrayList<>();
    this.additionalInfo = (additionalInfo != null) ? additionalInfo : new java.util.HashMap<>();
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public List<RuleError> getErrors() {
    return errors;
  }

  public void setErrors(List<RuleError> errors) {
    this.errors = errors;
  }

  public List<RuleWarning> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<RuleWarning> warnings) {
    this.warnings = warnings;
  }

  public Map<String, Object> getAdditionalInfo() {
    return additionalInfo;
  }

  public void setAdditionalInfo(Map<String, Object> additionalInfo) {
    this.additionalInfo = additionalInfo;
  }

  /** 添加错误信息 */
  public void addError(String ruleName, String message) {
    errors.add(new RuleError(ruleName, message));
    this.success = false;
  }

  /** 添加警告信息 */
  public void addWarning(String ruleName, String message) {
    warnings.add(new RuleWarning(ruleName, message));
  }

  /** 获取错误消息的汇总文本 */
  public String getErrorMessage() {
    StringBuilder sb = new StringBuilder();
    for (RuleError error : errors) {
      sb.append("[")
          .append(error.getRuleName())
          .append("] ")
          .append(error.getMessage())
          .append("; ");
    }
    return sb.toString();
  }

  /** 规则错误信息 */
  public static class RuleError {
    private String ruleName;
    private String message;

    public RuleError() {}

    public RuleError(String ruleName, String message) {
      this.ruleName = ruleName;
      this.message = message;
    }

    public String getRuleName() {
      return ruleName;
    }

    public void setRuleName(String ruleName) {
      this.ruleName = ruleName;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }
  }

  /** 规则警告信息 */
  public static class RuleWarning {
    private String ruleName;
    private String message;

    public RuleWarning() {}

    public RuleWarning(String ruleName, String message) {
      this.ruleName = ruleName;
      this.message = message;
    }

    public String getRuleName() {
      return ruleName;
    }

    public void setRuleName(String ruleName) {
      this.ruleName = ruleName;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }
  }
}
