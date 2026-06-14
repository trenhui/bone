package com.bone.metadata.sdk.domain.exception;

public class TemplateSecurityException extends SDKException {
  private static final long serialVersionUID = 1L;

  public TemplateSecurityException(String message) {
    super("TEMPLATE_SECURITY_ERROR", message);
  }

  public TemplateSecurityException(String message, Throwable cause) {
    super("TEMPLATE_SECURITY_ERROR", message, cause);
  }
}
