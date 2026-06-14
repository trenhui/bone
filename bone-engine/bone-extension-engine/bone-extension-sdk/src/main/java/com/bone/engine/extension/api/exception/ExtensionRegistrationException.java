package com.bone.engine.extension.api.exception;

/**
 * 扩展点框架核心异常基类，用于表示扩展点相关的异常
 *
 * <p>所有扩展点框架抛出的业务异常都应该继承此类，确保异常处理的一致性和可识别性
 *
 * @author bone team
 */
public class ExtensionRegistrationException extends RuntimeException {

  /** 异常错误码，用于识别不同类型的异常 */
  private final String errorCode;

  /**
   * 创建一个新的扩展点异常实例
   *
   * @param message 详细异常信息
   */
  public ExtensionRegistrationException(String message) {
    super(message);
    this.errorCode = "EXTENSION_REGISTRATION_ERROR";
  }

  /**
   * 创建一个新的扩展点异常实例
   *
   * @param message 详细异常信息
   * @param cause 根本原因异常
   */
  public ExtensionRegistrationException(String message, Throwable cause) {
    super(message, cause);
    this.errorCode = "EXTENSION_REGISTRATION_ERROR";
  }

  /**
   * 创建一个新的扩展点异常实例
   *
   * @param errorCode 错误码
   * @param message 详细异常信息
   */
  public ExtensionRegistrationException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * 创建一个新的扩展点异常实例
   *
   * @param errorCode 错误码
   * @param message 详细异常信息
   * @param cause 根本原因异常
   */
  public ExtensionRegistrationException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * 获取异常错误码
   *
   * @return 错误码字符串
   */
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String toString() {
    return "ExtensionRegistrationException{"
        + "errorCode='"
        + errorCode
        + "'"
        + ", message='"
        + getMessage()
        + "'"
        + '}';
  }
}
