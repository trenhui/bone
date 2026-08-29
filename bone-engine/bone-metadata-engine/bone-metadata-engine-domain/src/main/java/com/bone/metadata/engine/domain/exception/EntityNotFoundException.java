package com.bone.metadata.engine.domain.exception;

import java.util.Map;

/** 实体未找到异常类 用于表示实体查找失败的错误 */
public class EntityNotFoundException extends MetadataEngineBizException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造函数
   *
   * @param message 错误消息
   */
  public EntityNotFoundException(String message) {
    super(message);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   */
  public EntityNotFoundException(String message, String errorCode) {
    super(message, errorCode);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   * @param level 异常级别
   */
  public EntityNotFoundException(String message, String errorCode, ExceptionLevel level) {
    super(message, errorCode, level);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param cause 根异常
   */
  public EntityNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param errorCode 错误码
   * @param cause 根异常
   */
  public EntityNotFoundException(String message, String errorCode, Throwable cause) {
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
  public EntityNotFoundException(
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
  public EntityNotFoundException(
      String message,
      String errorCode,
      ExceptionLevel level,
      Throwable cause,
      Map<String, Object> context) {
    super(message, errorCode, level, cause, context);
  }
}
