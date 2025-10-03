package com.bone.metadata.sdk.domain.exception;

public class TemplateSecurityException extends RuntimeException {
    public TemplateSecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateSecurityException(String message) {
        super(message);
    }
}