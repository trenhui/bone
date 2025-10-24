package com.bone.metadata.sdk.domain.exception;

/**
 * 多结果异常类
 * 当查询期望返回单个结果但实际返回多个结果时抛出
 */
public class MultipleResultsException extends SDKException {

    private static final String ERROR_CODE = "MULTIPLE_RESULTS_ERROR";
    
    /**
     * 创建多结果异常
     * @param message 异常消息
     */
    public MultipleResultsException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 创建多结果异常
     * @param message 异常消息
     * @param cause 根本原因
     */
    public MultipleResultsException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
