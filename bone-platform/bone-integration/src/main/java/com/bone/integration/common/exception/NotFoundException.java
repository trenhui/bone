package com.bone.integration.common.exception;

public class NotFoundException extends com.bone.core.exception.BizException {
  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
