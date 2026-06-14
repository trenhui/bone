package com.bone.metadata.sdk.domain.exception;

public class TemplateValidationException extends SDKException {
  private static final long serialVersionUID = 1L;

  public TemplateValidationException(String message) {
    super("TEMPLATE_VALIDATION_ERROR", message);
  }

  public TemplateValidationException(String message, Throwable cause) {
    super("TEMPLATE_VALIDATION_ERROR", message, cause);
  }
}
