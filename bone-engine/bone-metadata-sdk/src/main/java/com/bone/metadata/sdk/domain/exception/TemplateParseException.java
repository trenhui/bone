package com.bone.metadata.sdk.domain.exception;

public class TemplateParseException extends SDKException {
  private static final long serialVersionUID = 1L;

  public TemplateParseException(String message) {
    super("TEMPLATE_PARSE_ERROR", message);
  }

  public TemplateParseException(String message, Throwable cause) {
    super("TEMPLATE_PARSE_ERROR", message, cause);
  }
}
