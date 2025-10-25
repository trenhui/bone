package com.bone.example.extension.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 验证结果对象
 * <p>
 * 封装验证过程中的验证状态、错误码和错误信息，用于统一的验证结果返回。
 * 提供了一系列静态工厂方法，简化验证结果的创建。
 */
public class ValidationResult {
    
    /**
     * 验证状态：成功
     */
    private boolean success;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 获取错误信息
     * 
     * @return 错误信息字符串
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取错误码（兼容方法）
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    // 移除重复的getErrorMessage()方法定义
    
    /**
     * 详细错误信息列表
     */
    private List<String> detailedMessages = new ArrayList<>();
    
    /**
     * 是否验证通过
     * 
     * @return 如果验证通过则返回true，否则返回false
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 是否验证通过（兼容方法）
     * 
     * @return 如果验证通过则返回true，否则返回false
     */
    public boolean isValid() {
        return isSuccess();
    }
    
    /**
     * 创建成功的验证结果
     * 
     * @return 验证成功的结果
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .success(true)
                .build();
    }
    
    /**
     * 创建失败的验证结果
     * 
     * @param errorCode 错误代码
     * @param errorMessage 错误信息
     * @return 验证失败的结果
     */
    public static ValidationResult fail(final String errorCode, final String errorMessage) {
        Objects.requireNonNull(errorCode, "错误代码不能为null");
        Objects.requireNonNull(errorMessage, "错误信息不能为null");
        
        return ValidationResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 创建验证失败的结果（兼容方法）
     * 
     * @param errorMessage 错误信息
     * @return 验证失败的结果
     */
    public static ValidationResult failure(final String errorMessage) {
        return fail("VALIDATION_ERROR", errorMessage);
    }
    
    /**
     * 添加详细错误信息
     * 
     * @param message 详细错误信息
     * @return 当前对象，支持链式调用
     */
    public ValidationResult addDetailedMessage(final String message) {
        if (message != null) {
            if (this.detailedMessages == null) {
                this.detailedMessages = new ArrayList<>();
            }
            this.detailedMessages.add(message);
        }
        return this;
    }
    
    /**
     * 获取详细错误信息列表（不可修改）
     * 
     * @return 详细错误信息列表
     */
    public List<String> getDetailedMessages() {
        return detailedMessages == null ? Collections.emptyList() : 
               Collections.unmodifiableList(detailedMessages);
    }
    
    /**
     * 合并另一个验证结果
     * 
     * @param other 另一个验证结果
     * @return 合并后的验证结果
     */
    public ValidationResult merge(final ValidationResult other) {
        if (other == null) {
            return this;
        }
        
        // 如果当前成功但other失败，返回失败结果
        if (this.success && !other.success) {
            return other;
        }
        
        // 如果当前失败，合并详细错误信息
            if (!this.success && !other.success) {
                ValidationResult merged = new ValidationResult();
                merged.success = false;
                merged.errorCode = this.errorCode;
                merged.errorMessage = this.errorMessage;
                List<String> allMessages = new ArrayList<>();
            
            if (this.detailedMessages != null) {
                allMessages.addAll(this.detailedMessages);
            }
            if (other.detailedMessages != null) {
                allMessages.addAll(other.detailedMessages);
            }
            
            merged.detailedMessages = allMessages;
            return merged;
        }
        
        // 两个都成功，返回成功结果
        return success();
    }
    
    @Override
    public String toString() {
        if (success) {
            return "ValidationResult{success=true}";
        }
        return String.format("ValidationResult{success=false, errorCode='%s', errorMessage='%s'}", 
                errorCode, errorMessage);
    }
    
    // 手动实现builder方法
    public static ValidationResultBuilder builder() {
        return new ValidationResultBuilder();
    }
    
    public static class ValidationResultBuilder {
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private List<String> detailedMessages = new ArrayList<>();
        
        public ValidationResultBuilder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public ValidationResultBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        public ValidationResultBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public ValidationResultBuilder detailedMessages(List<String> detailedMessages) {
            this.detailedMessages = detailedMessages;
            return this;
        }
        
        public ValidationResult build() {
            ValidationResult result = new ValidationResult();
            result.success = this.success;
            result.errorCode = this.errorCode;
            result.errorMessage = this.errorMessage;
            result.detailedMessages = this.detailedMessages;
            return result;
        }
    }
}