package com.bone.tpa.sdk.adjustment.exception;

public class InvalidRequestException extends RuntimeException {

    // 默认构造函数
    public InvalidRequestException() {
        super("Failed to acquire lock");
    }

    // 带消息的构造函数
    public InvalidRequestException(String message) {
        super(message);
    }

    // 带消息和原因的构造函数
    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    // 带原因的构造函数
    public InvalidRequestException(Throwable cause) {
        super(cause);
    }
}
