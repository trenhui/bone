package com.bone.metadata.sdk.domain.exception;

public class TemplateNotFoundException extends SDKException {
  private static final long serialVersionUID = 1L;

  public TemplateNotFoundException(String message, Throwable cause) {
    super("TEMPLATE_NOT_FOUND_ERROR", message, cause);
  }

  public TemplateNotFoundException(String message) {
    super("TEMPLATE_NOT_FOUND_ERROR", message);
  }
}
