package com.bone.metadata.sdk.domain.exception;

public class TemplateParseException extends RuntimeException {
    public TemplateParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateParseException(String message) {
        super(message);
    }
}