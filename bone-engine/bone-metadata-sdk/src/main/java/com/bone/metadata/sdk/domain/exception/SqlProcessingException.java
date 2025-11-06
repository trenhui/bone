package com.bone.metadata.sdk.domain.exception;

public class SqlProcessingException extends SDKException {
    private static final long serialVersionUID = 1L;
    
    public SqlProcessingException(String message) {
        super("SQL_PROCESSING_ERROR", message);
    }

    public SqlProcessingException(String message, Throwable cause) {
        super("SQL_PROCESSING_ERROR", message, cause);
    }
}