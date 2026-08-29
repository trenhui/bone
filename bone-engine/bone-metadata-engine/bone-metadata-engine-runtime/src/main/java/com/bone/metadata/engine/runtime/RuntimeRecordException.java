package com.bone.metadata.engine.runtime;

/** 模式 B 运行时数据面业务异常 */
public class RuntimeRecordException extends RuntimeException {

  private final String errorCode;

  public RuntimeRecordException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
