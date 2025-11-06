package com.bone.metadata.sdk.domain.exception;

public class ServiceNotFoundException extends SDKException {
    private static final long serialVersionUID = 1L;
    
    public ServiceNotFoundException(String message, Throwable cause) {
        super("SERVICE_NOT_FOUND_ERROR", message, cause);
    }

    public ServiceNotFoundException(String message) {
        super("SERVICE_NOT_FOUND_ERROR", message);
    }
}