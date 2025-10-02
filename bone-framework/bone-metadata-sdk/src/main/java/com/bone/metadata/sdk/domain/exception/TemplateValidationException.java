package com.bone.metadata.sdk.domain.exception;

public class TemplateValidationException extends RuntimeException {
    public TemplateValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateValidationException(String message) {
        super(message);
    }
}