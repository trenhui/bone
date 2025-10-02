package com.bone.metadata.sdk.domain.exception;

public class MultipleResultsException extends RuntimeException {

    // 构造函数，传入错误信息
    public MultipleResultsException(String message) {
        super(message);
    }

    // 构造函数，传入错误信息和引起异常的原因
    public MultipleResultsException(String message, Throwable cause) {
        super(message, cause);
    }
}
