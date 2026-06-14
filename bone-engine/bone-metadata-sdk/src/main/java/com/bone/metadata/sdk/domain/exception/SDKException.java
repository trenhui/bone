package com.bone.metadata.sdk.domain.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** SDK异常基类 所有Bone Metadata SDK抛出的异常都应继承此类 */
public class SDKException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  // 错误码
  private final String errorCode;
  // 唯一错误ID，用于追踪
  private final String errorId;
  // 错误上下文信息
  private final Map<String, Object> context = new HashMap<>();
  // 发生时间
  private final long timestamp;

  /**
   * 创建SDK异常
   *
   * @param message 异常消息
   */
  public SDKException(String message) {
    this("SDK_ERROR", message, null);
  }

  /**
   * 创建SDK异常
   *
   * @param errorCode 错误码
   * @param message 异常消息
   */
  public SDKException(String errorCode, String message) {
    this(errorCode, message, null);
  }

  /**
   * 创建SDK异常
   *
   * @param message 异常消息
   * @param cause 根本原因
   */
  public SDKException(String message, Throwable cause) {
    this("SDK_ERROR", message, cause);
  }

  /**
   * 创建SDK异常
   *
   * @param errorCode 错误码
   * @param message 异常消息
   * @param cause 根本原因
   */
  public SDKException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode != null ? errorCode : "SDK_ERROR";
    this.errorId = UUID.randomUUID().toString();
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 创建SDK异常
   *
   * @param cause 根本原因
   */
  public SDKException(Throwable cause) {
    this("SDK_ERROR", cause != null ? cause.getMessage() : "Unknown error", cause);
  }

  /**
   * 获取错误码
   *
   * @return 错误码
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * 获取错误ID
   *
   * @return 错误ID
   */
  public String getErrorId() {
    return errorId;
  }

  /**
   * 获取错误上下文
   *
   * @return 上下文信息
   */
  public Map<String, Object> getContext() {
    return new HashMap<>(context);
  }

  /**
   * 添加上下文信息
   *
   * @param key 键
   * @param value 值
   * @return 当前异常实例
   */
  public SDKException addContext(String key, Object value) {
    this.context.put(key, value);
    return this;
  }

  /**
   * 获取异常发生时间戳
   *
   * @return 时间戳
   */
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getName())
        .append(" [errorId=")
        .append(errorId)
        .append(", errorCode=")
        .append(errorCode)
        .append(", message=")
        .append(getMessage())
        .append("]");

    if (!context.isEmpty()) {
      sb.append(" Context: " + context);
    }

    return sb.toString();
  }
}
