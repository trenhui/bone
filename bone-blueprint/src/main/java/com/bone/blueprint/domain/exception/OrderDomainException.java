package com.bone.blueprint.domain.exception;

import com.bone.core.exception.DomainException;

public class OrderDomainException extends DomainException {
    
    public OrderDomainException(String message) {
        super(message);
    }
    
    public OrderDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}