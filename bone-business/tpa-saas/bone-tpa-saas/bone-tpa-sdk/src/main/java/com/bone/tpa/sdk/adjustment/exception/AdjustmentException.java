package com.bone.tpa.sdk.adjustment.exception;

public class AdjustmentException extends RuntimeException {
    private String errorCode;

    public AdjustmentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AdjustmentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }


    public AdjustmentException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
