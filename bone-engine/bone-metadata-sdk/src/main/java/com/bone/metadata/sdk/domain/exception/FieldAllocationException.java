package com.bone.metadata.sdk.domain.exception;

public class FieldAllocationException extends SDKException {
    private static final long serialVersionUID = 1L;
    
    public FieldAllocationException(String message, Throwable cause) {
        super("FIELD_ALLOCATION_ERROR", message, cause);
    }
    
    public FieldAllocationException(String message) {
        super("FIELD_ALLOCATION_ERROR", message);
    }
}
