package com.bone.metadata.sdk.domain.exception;

public class TemplateLoadException extends RuntimeException {
    public TemplateLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateLoadException(String message) {
        super(message);
    }
}