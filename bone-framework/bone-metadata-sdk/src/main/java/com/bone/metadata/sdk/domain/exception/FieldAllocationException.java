package com.bone.metadata.sdk.domain.exception;

public  class FieldAllocationException extends RuntimeException {
    public FieldAllocationException(String message, Throwable cause) {
        super(message, cause);
    }
    public FieldAllocationException(String message) {
        super(message);
    }
}
