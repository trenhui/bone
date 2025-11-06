package com.bone.metadata.sdk.domain.exception;

public class PersistenceException extends SDKException {
    private static final long serialVersionUID = 1L;
    
    public PersistenceException(String message, Throwable cause) {
        super("PERSISTENCE_ERROR", message, cause);
    }
    
    public PersistenceException(String message) {
        super("PERSISTENCE_ERROR", message);
    }
}