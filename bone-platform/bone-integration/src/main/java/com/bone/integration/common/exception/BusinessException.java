package com.bone.integration.common.exception;

import com.bone.core.exception.DomainException;

public class BusinessException extends DomainException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}