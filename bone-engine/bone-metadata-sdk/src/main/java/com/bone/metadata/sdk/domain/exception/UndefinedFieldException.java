package com.bone.metadata.sdk.domain.exception;

public class UndefinedFieldException extends SDKException {
    private static final long serialVersionUID = 1L;
    
    public UndefinedFieldException(String message) {
        super("UNDEFINED_FIELD_ERROR", message);
    }
}