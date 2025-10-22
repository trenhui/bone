package com.bone.smartmeta.engine.exception;

/**
 * 计算异常类，用于表示字段计算过程中的错误
 */
public class CalculationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private final String fieldName;
    private final String entityType;
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param fieldName 字段名
     * @param entityType 实体类型
     */
    public CalculationException(String message, String fieldName, String entityType) {
        super(message + " [field=" + fieldName + ", entityType=" + entityType + "]");
        this.fieldName = fieldName;
        this.entityType = entityType;
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param fieldName 字段名
     * @param entityType 实体类型
     * @param cause 根异常
     */
    public CalculationException(String message, String fieldName, String entityType, Throwable cause) {
        super(message + " [field=" + fieldName + ", entityType=" + entityType + "]", cause);
        this.fieldName = fieldName;
        this.entityType = entityType;
    }
    
    /**
     * 获取字段名
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * 获取实体类型
     */
    public String getEntityType() {
        return entityType;
    }
}