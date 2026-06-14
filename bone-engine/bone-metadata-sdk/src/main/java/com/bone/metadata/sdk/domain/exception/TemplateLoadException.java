package com.bone.metadata.sdk.domain.exception;

public class TemplateLoadException extends SDKException {
  private static final long serialVersionUID = 1L;

  public TemplateLoadException(String message, Throwable cause) {
    super("TEMPLATE_LOAD_ERROR", message, cause);
  }

  public TemplateLoadException(String message) {
    super("TEMPLATE_LOAD_ERROR", message);
  }
}
