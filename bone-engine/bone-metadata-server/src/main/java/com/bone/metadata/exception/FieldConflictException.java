package com.bone.metadata.exception;

public class FieldConflictException extends RuntimeException {
  public FieldConflictException(String message) {
    super(message);
  }

  public FieldConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
