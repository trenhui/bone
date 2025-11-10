package com.bone.procurement.exception;

import org.springframework.http.HttpStatus;

/**
 * 实体未找到异常
 * 当请求的实体不存在时抛出
 */
public class EntityNotFoundException extends BusinessException {
    
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ERROR_CODE = "ENTITY_NOT_FOUND";
    
    /**
     * 构造函数
     * @param message 错误消息
     */
    public EntityNotFoundException(String message) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param cause 异常原因
     */
    public EntityNotFoundException(String message, Throwable cause) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND, cause);
    }
    
    /**
     * 构造函数
     * @param entityType 实体类型
     * @param id 实体ID
     */
    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " with ID " + id + " not found", DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND);
    }
}
