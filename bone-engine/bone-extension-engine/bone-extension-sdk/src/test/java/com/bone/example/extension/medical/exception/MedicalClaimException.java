package com.bone.example.extension.medical.exception;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * 医疗保险理赔业务异常类
 * <p>
 * 用于表示理赔处理过程中发生的业务逻辑异常，包含错误码和理赔ID等上下文信息。
 * 该异常类设计遵循以下原则：
 * <ul>
 *     <li>包含错误码，便于错误识别和国际化处理</li>
 *     <li>包含理赔ID，便于追踪和问题定位</li>
 *     <li>保留原始异常信息，便于异常链路追踪</li>
 *     <li>提供多种构造函数，满足不同场景的使用需求</li>
 * </ul>
 */
public class MedicalClaimException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码，用于标识具体的业务错误类型
     */
    private final String errorCode;
    
    /**
     * 理赔ID，用于定位具体的理赔请求
     */
    private final String claimId;
    
    /**
     * 创建医疗保险理赔异常
     * 
     * @param message 异常消息
     */
    public MedicalClaimException(final String message) {
        super(message);
        this.errorCode = null;
        this.claimId = null;
    }
    
    /**
     * 创建医疗保险理赔异常，包含原始异常
     * 
     * @param message 异常消息
     * @param cause 原始异常
     */
    public MedicalClaimException(final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.claimId = null;
    }
    
    /**
     * 创建医疗保险理赔异常，包含错误码
     * 
     * @param message 异常消息
     * @param errorCode 错误码
     */
    public MedicalClaimException(final String message, final String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.claimId = null;
    }
    
    /**
     * 创建医疗保险理赔异常，包含错误码和原始异常
     * 
     * @param message 异常消息
     * @param errorCode 错误码
     * @param cause 原始异常
     */
    public MedicalClaimException(final String message, final String errorCode, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.claimId = null;
    }
    
    /**
     * 创建医疗保险理赔异常，包含错误码和理赔ID
     * 
     * @param message 异常消息
     * @param errorCode 错误码
     * @param claimId 理赔ID
     */
    public MedicalClaimException(final String message, final String errorCode, final String claimId) {
        super(message);
        this.errorCode = errorCode;
        this.claimId = claimId;
    }
    
    /**
     * 创建医疗保险理赔异常，包含错误码、理赔ID和原始异常
     * 
     * @param message 异常消息
     * @param errorCode 错误码
     * @param claimId 理赔ID
     * @param cause 原始异常
     */
    public MedicalClaimException(final String message, final String errorCode, final String claimId, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.claimId = claimId;
    }
    
    /**
     * 获取错误码
     * 
     * @return 错误码，如果未设置则返回null
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * 获取理赔ID
     * 
     * @return 理赔ID，如果未设置则返回null
     */
    public String getClaimId() {
        return claimId;
    }
    
    /**
     * 检查是否包含错误码
     * 
     * @return 如果包含有效的错误码则返回true，否则返回false
     */
    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.trim().isEmpty();
    }
    
    /**
     * 检查是否包含理赔ID
     * 
     * @return 如果包含有效的理赔ID则返回true，否则返回false
     */
    public boolean hasClaimId() {
        return claimId != null && !claimId.trim().isEmpty();
    }
    
    /**
     * 返回异常的详细描述字符串
     * <p>
     * 包含异常消息、错误码和理赔ID等信息，便于调试和日志记录。
     * 
     * @return 异常的详细描述字符串
     */
    @Override
    public String toString() {
        if (hasErrorCode() && hasClaimId()) {
            return MessageFormat.format("{0}: [错误码: {1}, 理赔ID: {2}]", 
                    getMessage(), errorCode, claimId);
        } else if (hasErrorCode()) {
            return MessageFormat.format("{0}: [错误码: {1}]", 
                    getMessage(), errorCode);
        } else if (hasClaimId()) {
            return MessageFormat.format("{0}: [理赔ID: {1}]", 
                    getMessage(), claimId);
        }
        return super.toString();
    }
    
    /**
     * 比较两个异常是否相等
     * <p>
     * 两个异常相等的条件是：它们是同一个对象，或者它们的类、消息、错误码和理赔ID都相等。
     * 
     * @param obj 要比较的对象
     * @return 如果两个对象相等则返回true，否则返回false
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MedicalClaimException)) {
            return false;
        }
        final MedicalClaimException other = (MedicalClaimException) obj;
        return Objects.equals(errorCode, other.errorCode) && 
               Objects.equals(claimId, other.claimId) &&
               Objects.equals(getMessage(), other.getMessage());
    }
    
    /**
     * 返回异常的哈希码
     * 
     * @return 异常的哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), errorCode, claimId);
    }
}