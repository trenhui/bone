package com.bone.metadata.sdk.domain.exception;

/**
 * 非唯一结果异常类
 * 当查询期望返回单个结果但实际返回多个结果时抛出
 */
public class NonUniqueResultException extends SDKException {
    
    /**
     * 创建非唯一结果异常
     * @param message 异常消息
     */
    public NonUniqueResultException(String message) {
        super(message);
    }
}