package com.bone.metadata.sdk.domain.exception;

public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceNotFoundException(String message) {
        super(message);
    }
}