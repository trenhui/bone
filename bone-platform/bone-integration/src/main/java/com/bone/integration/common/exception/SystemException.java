package com.bone.integration.common.exception;

import com.bone.core.exception.DomainException;

public class SystemException extends DomainException {
    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}