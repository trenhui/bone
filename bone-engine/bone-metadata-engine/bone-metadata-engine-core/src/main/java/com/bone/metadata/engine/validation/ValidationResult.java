package com.bone.metadata.engine.validation;

import java.util.*;
import java.util.stream.Collectors;

/** 统一验证结果类 提供通用的验证结果表示和处理机制，支持多种业务场景 */
public class ValidationResult {

  private boolean valid;
  private List<ValidationError> errors;
  private List<ValidationWarning> warnings;
  private Map<String, Object> context;
  private long timestamp;
  private ValidationLevel level;

  // 构造方法
  private ValidationResult() {
    this.valid = true;
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
    this.context = new HashMap<>();
    this.timestamp = System.currentTimeMillis();
    this.level = ValidationLevel.INFO;
  }

  // 静态工厂方法 - 创建成功结果
  public static ValidationResult success() {
    return new ValidationResult();
  }

  // 创建失败结果
  public static ValidationResult failure(String errorMessage) {
    ValidationResult result = new ValidationResult();
    result.addError(errorMessage);
    return result;
  }

  // builder方法
  public static Builder builder() {
    return new Builder();
  }

  // 添加错误
  public void addError(ValidationError error) {
    this.errors.add(error);
    this.valid = false;
    updateLevel();
  }

  // 添加错误（简化版）
  public void addError(String message) {
    addError(message, null, null);
  }

  // 添加错误（带字段）
  public void addError(String message, String fieldPath) {
    addError(message, fieldPath, null);
  }

  // 添加错误（完整版）
  public void addError(String message, String fieldPath, String code) {
    ValidationError error =
        ValidationError.builder().message(message).fieldPath(fieldPath).code(code).build();
    addError(error);
  }

  // 添加警告
  public void addWarning(ValidationWarning warning) {
    this.warnings.add(warning);
    updateLevel();
  }

  // 添加警告（简化版）
  public void addWarning(String message) {
    addWarning(message, null, null);
  }

  // 添加警告（带字段）
  public void addWarning(String message, String fieldPath) {
    addWarning(message, fieldPath, null);
  }

  // 添加警告（完整版）
  public void addWarning(String message, String fieldPath, String code) {
    ValidationWarning warning =
        ValidationWarning.builder().message(message).fieldPath(fieldPath).code(code).build();
    addWarning(warning);
  }

  // 更新验证级别
  private void updateLevel() {
    if (!valid) {
      level = ValidationLevel.ERROR;
    } else if (!warnings.isEmpty()) {
      level = ValidationLevel.WARNING;
    } else {
      level = ValidationLevel.INFO;
    }
  }

  // getter方法
  public boolean isValid() {
    return valid;
  }

  public List<ValidationError> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  public List<ValidationWarning> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  public Map<String, Object> getContext() {
    return Collections.unmodifiableMap(context);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public ValidationLevel getLevel() {
    return level;
  }

  // 获取错误数量
  public int getErrorCount() {
    return errors.size();
  }

  // 获取警告数量
  public int getWarningCount() {
    return warnings.size();
  }

  // 检查是否有错误
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  // 检查是否有警告
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  // 获取所有错误消息
  public List<String> getAllErrorMessages() {
    return errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
  }

  // 获取所有警告消息
  public List<String> getAllWarningMessages() {
    return warnings.stream().map(ValidationWarning::getMessage).collect(Collectors.toList());
  }

  // 合并另一个验证结果
  public void merge(ValidationResult other) {
    if (other == null) return;

    this.errors.addAll(other.errors);
    this.warnings.addAll(other.warnings);
    this.context.putAll(other.context);
    this.valid = this.valid && other.valid;
    updateLevel();
  }

  // 错误内部类
  public static class ValidationError {
    private String message;
    private String fieldPath;
    private String code;

    private ValidationError() {}

    public static Builder builder() {
      return new Builder();
    }

    public String getMessage() {
      return message;
    }

    public String getFieldPath() {
      return fieldPath;
    }

    public String getCode() {
      return code;
    }

    public static class Builder {
      private ValidationError error = new ValidationError();

      public Builder message(String message) {
        error.message = message;
        return this;
      }

      public Builder fieldPath(String fieldPath) {
        error.fieldPath = fieldPath;
        return this;
      }

      public Builder code(String code) {
        error.code = code;
        return this;
      }

      public ValidationError build() {
        return error;
      }
    }
  }

  // 警告内部类
  public static class ValidationWarning {
    private String message;
    private String fieldPath;
    private String code;

    private ValidationWarning() {}

    public static Builder builder() {
      return new Builder();
    }

    public String getMessage() {
      return message;
    }

    public String getFieldPath() {
      return fieldPath;
    }

    public String getCode() {
      return code;
    }

    public static class Builder {
      private ValidationWarning warning = new ValidationWarning();

      public Builder message(String message) {
        warning.message = message;
        return this;
      }

      public Builder fieldPath(String fieldPath) {
        warning.fieldPath = fieldPath;
        return this;
      }

      public Builder code(String code) {
        warning.code = code;
        return this;
      }

      public ValidationWarning build() {
        return warning;
      }
    }
  }

  // Builder内部类
  public static class Builder {
    private ValidationResult result = new ValidationResult();

    public Builder error(ValidationError error) {
      result.addError(error);
      return this;
    }

    public Builder error(String message) {
      result.addError(message);
      return this;
    }

    public Builder error(String message, String fieldPath) {
      result.addError(message, fieldPath);
      return this;
    }

    public Builder error(String message, String fieldPath, String code) {
      result.addError(message, fieldPath, code);
      return this;
    }

    public Builder warning(ValidationWarning warning) {
      result.addWarning(warning);
      return this;
    }

    public Builder warning(String message) {
      result.addWarning(message);
      return this;
    }

    public Builder warning(String message, String fieldPath) {
      result.addWarning(message, fieldPath);
      return this;
    }

    public Builder warning(String message, String fieldPath, String code) {
      result.addWarning(message, fieldPath, code);
      return this;
    }

    public Builder context(String key, Object value) {
      result.context.put(key, value);
      return this;
    }

    public Builder context(Map<String, Object> context) {
      result.context.putAll(context);
      return this;
    }

    public Builder timestamp(long timestamp) {
      result.timestamp = timestamp;
      return this;
    }

    public ValidationResult build() {
      return result;
    }
  }

  /** 验证级别枚举 */
  public enum ValidationLevel {
    INFO, // 信息级别
    WARNING, // 警告级别
    ERROR // 错误级别
  }
}
