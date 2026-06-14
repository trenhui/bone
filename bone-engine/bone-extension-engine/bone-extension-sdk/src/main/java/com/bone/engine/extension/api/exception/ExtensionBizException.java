package com.bone.engine.extension.api.exception;

import java.util.Objects;

/**
 * 扩展点业务异常基类（《Bone-DDD》§16.3：避免 {@code BusinessException} 命名）。
 *
 * <p>各插件业务异常可继承此类；继承 {@link ExtensionException} 保持框架异常体系一致。
 */
public class ExtensionBizException extends ExtensionException {

  private static final long serialVersionUID = 1L;

  private final String module;

  protected ExtensionBizException() {
    super("EXTENSION_BIZ_ERROR");
    this.module = "DEFAULT";
  }

  protected ExtensionBizException(String errorCode) {
    super(errorCode);
    this.module = "DEFAULT";
  }

  protected ExtensionBizException(String errorCode, String message) {
    super(errorCode, message);
    this.module = "DEFAULT";
  }

  protected ExtensionBizException(String errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
    this.module = "DEFAULT";
  }

  public ExtensionBizException(String module, String errorCode, String message) {
    super(errorCode, message);
    this.module = module;
  }

  public ExtensionBizException(String module, String errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
    this.module = module;
  }

  public String getModule() {
    return module;
  }

  public boolean hasErrorCode() {
    String errorCode = getErrorCode();
    return errorCode != null && !errorCode.trim().isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExtensionBizException that = (ExtensionBizException) o;
    return Objects.equals(getErrorCode(), that.getErrorCode())
        && Objects.equals(getMessage(), that.getMessage())
        && Objects.equals(module, that.module);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getErrorCode(), getMessage(), module);
  }

  @Override
  public String toString() {
    return "ExtensionBizException{"
        + "errorCode='"
        + getErrorCode()
        + '\''
        + ", module='"
        + module
        + '\''
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }

  public static ExtensionBizException of(String module, String errorCode, String message) {
    return new ExtensionBizException(module, errorCode, message);
  }

  public static ExtensionBizException of(
      String module, String errorCode, String message, Throwable cause) {
    return new ExtensionBizException(module, errorCode, message, cause);
  }

  public static ExtensionBizException userServiceException(String errorCode, String message) {
    return new ExtensionBizException("USER_SERVICE", errorCode, message);
  }

  public static ExtensionBizException riskControlException(String errorCode, String message) {
    return new ExtensionBizException("RISK_CONTROL", errorCode, message);
  }
}
