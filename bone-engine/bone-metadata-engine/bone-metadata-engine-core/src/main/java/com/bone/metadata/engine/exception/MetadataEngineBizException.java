package com.bone.metadata.engine.exception;

import com.bone.core.exception.BizException;
import java.util.Map;

/** metadata-engine 业务异常抽象基类：继承平台 {@link BizException}（《Bone-DDD》§16.3）， 保留引擎侧字符串错误码、异常级别与上下文扩展。 */
public abstract class MetadataEngineBizException extends BizException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final ExceptionLevel level;
  private final Map<String, Object> context;

  protected MetadataEngineBizException(String message) {
    this(message, null, ExceptionLevel.ERROR, null);
  }

  protected MetadataEngineBizException(String message, String errorCode) {
    this(message, errorCode, ExceptionLevel.ERROR, null);
  }

  protected MetadataEngineBizException(String message, String errorCode, ExceptionLevel level) {
    this(message, errorCode, level, null);
  }

  protected MetadataEngineBizException(
      String message, String errorCode, ExceptionLevel level, Map<String, Object> context) {
    super(BizException.DEFAULT_ERROR_CODE, message);
    this.errorCode = errorCode;
    this.level = level != null ? level : ExceptionLevel.ERROR;
    this.context = context;
  }

  protected MetadataEngineBizException(String message, Throwable cause) {
    this(message, null, ExceptionLevel.ERROR, cause, null);
  }

  protected MetadataEngineBizException(String message, String errorCode, Throwable cause) {
    this(message, errorCode, ExceptionLevel.ERROR, cause, null);
  }

  protected MetadataEngineBizException(
      String message,
      String errorCode,
      ExceptionLevel level,
      Throwable cause,
      Map<String, Object> context) {
    super(BizException.DEFAULT_ERROR_CODE, message, cause);
    this.errorCode = errorCode;
    this.level = level != null ? level : ExceptionLevel.ERROR;
    this.context = context;
  }

  /** 获取 metadata-engine 字符串错误码，与 {@link BizException#getCode()} 数值码并列。 */
  public String getErrorCode() {
    return errorCode;
  }

  public ExceptionLevel getLevel() {
    return level;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  public enum ExceptionLevel {
    INFO,
    WARNING,
    ERROR
  }
}
