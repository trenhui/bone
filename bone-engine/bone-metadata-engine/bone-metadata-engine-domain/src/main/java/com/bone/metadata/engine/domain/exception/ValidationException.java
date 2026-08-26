package com.bone.metadata.engine.domain.exception;

import java.util.Map;

/** 验证异常类 用于表示验证过程中的错误 */
public class ValidationException extends MetadataEngineBizException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造函数
   *
   * @param message 错误消息
   */
  public ValidationException(String message) {
    super(message);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   */
  public ValidationException(String message, String errorCode) {
    super(message, errorCode);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   * @param level 异常级别
   */
  public ValidationException(String message, String errorCode, ExceptionLevel level) {
    super(message, errorCode, level);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param cause 根异常
   */
  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   * @param cause 根异常
   */
  public ValidationException(String message, String errorCode, Throwable cause) {
    super(message, errorCode, cause);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   * @param level 异常级别
   * @param cause 根异常
   */
  public ValidationException(
      String message, String errorCode, ExceptionLevel level, Throwable cause) {
    super(message, errorCode, level, cause, null);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   * @param level 异常级别
   * @param cause 根异常
   * @param context 上下文信息
   */
  public ValidationException(
      String message,
      String errorCode,
      ExceptionLevel level,
      Throwable cause,
      Map<String, Object> context) {
    super(message, errorCode, level, cause, context);
  }
}
