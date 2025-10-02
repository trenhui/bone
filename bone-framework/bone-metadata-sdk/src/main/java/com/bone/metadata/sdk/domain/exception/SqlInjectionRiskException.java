package com.bone.metadata.sdk.domain.exception;

public class SqlInjectionRiskException extends RuntimeException {
    public SqlInjectionRiskException(String message) {
        super(message);
    }

    public SqlInjectionRiskException(String message, Throwable cause) {
        super(message, cause);
    }
}