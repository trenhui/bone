package com.bone.core.exception;

public class LockAcquireFailedException extends RuntimeException {
    public LockAcquireFailedException(String message,Exception e) {
        super(message,e);
    }

    public LockAcquireFailedException(String message) {
        super(message);
    }
}