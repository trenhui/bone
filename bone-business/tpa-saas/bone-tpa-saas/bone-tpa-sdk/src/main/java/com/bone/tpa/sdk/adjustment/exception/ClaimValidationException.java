package com.bone.tpa.sdk.adjustment.exception;

public class ClaimValidationException extends RuntimeException {
    public ClaimValidationException(String message) {
        super(message);
    }

    public ClaimValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
