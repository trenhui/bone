package com.bone.integration.common.exception;

import com.bone.core.exception.DomainException;

public class NotFoundException extends DomainException {
  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
