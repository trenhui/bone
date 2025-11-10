package com.bone.procurement.exception;

import org.springframework.http.HttpStatus;

/**
 * 订单未找到异常
 * 当请求的订单不存在时抛出
 */
public class OrderNotFoundException extends BusinessException {
    
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ERROR_CODE = "ORDER_NOT_FOUND";
    
    /**
     * 构造函数
     * @param message 错误消息
     */
    public OrderNotFoundException(String message) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param cause 异常原因
     */
    public OrderNotFoundException(String message, Throwable cause) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND, cause);
    }
}
