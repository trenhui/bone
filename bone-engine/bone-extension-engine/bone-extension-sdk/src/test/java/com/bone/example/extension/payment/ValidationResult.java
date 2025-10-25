package com.bone.example.extension.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 验证结果对象
 * <p>
 * 封装支付请求验证的结果信息，包括验证状态、错误消息、错误码等
 */
public class ValidationResult {
    private boolean success;           // 是否验证成功
    private String message;            // 验证消息
    private List<String> errorDetails; // 错误详情列表
    private String errorCode;          // 错误码
    
    /**
     * 构造函数，初始化错误详情列表
     */
    public ValidationResult() {
        this.errorDetails = new ArrayList<>();
    }
    
    /**
     * 获取验证是否成功
     * 
     * @return 验证是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 设置验证是否成功
     * 
     * @param success 验证是否成功
     */
    public void success(boolean success) {
        this.success = success;
    }
    
    /**
     * 获取验证消息
     * 
     * @return 验证消息，成功时返回成功消息，失败时返回失败原因
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * 设置验证消息
     * 
     * @param message 验证消息，成功时返回成功消息，失败时返回失败原因
     */
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**
     * 获取错误详情列表
     * 
     * @return 详细的错误信息列表
     */
    public List<String> getErrorDetails() {
        return errorDetails;
    }
    
    /**
     * 设置错误详情列表
     * 
     * @param errorDetails 详细的错误信息列表
     */
    public void setErrorDetails(List<String> errorDetails) {
        this.errorDetails = errorDetails;
    }
    
    /**
     * 添加错误详情
     * 
     * @param errorDetail 单个错误详情信息
     */
    public void addErrorDetail(String errorDetail) {
        this.errorDetails.add(errorDetail);
    }
    
    /**
     * 获取错误码
     * 
     * @return 错误码，用于标识具体的错误类型
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * 设置错误码
     * 
     * @param errorCode 错误码，用于标识具体的错误类型
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    /**
     * 创建成功的验证结果
     * 
     * @return 成功的验证结果对象
     */
    public static ValidationResult success() {
        ValidationResult result = new ValidationResult();
        result.success(true);
        result.setMessage("验证成功");
        return result;
    }
    
    /**
     * 创建失败的验证结果
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     * @return 失败的验证结果对象
     */
    public static ValidationResult failure(String errorCode, String message) {
        ValidationResult result = new ValidationResult();
        result.success(false);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        return result;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return success == that.success && 
               Objects.equals(message, that.message) && 
               Objects.equals(errorDetails, that.errorDetails) && 
               Objects.equals(errorCode, that.errorCode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, errorDetails, errorCode);
    }
    
    @Override
    public String toString() {
        return "ValidationResult{" +
                "success=" + success +
                ", message='" + message + "'" +
                ", errorCode='" + errorCode + "'" +
                ", errorDetails=" + errorDetails +
                "}";
    }
}