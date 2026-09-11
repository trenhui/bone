package com.bone.integration.common.exception;

public class SystemException extends com.bone.core.exception.SystemException {
  public SystemException(String message) {
    super(message);
  }

  public SystemException(String message, Throwable cause) {
    super(message, cause);
  }
}
