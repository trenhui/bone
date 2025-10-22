package com.bone.engine.extension.metadata.example;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用验证结果类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean valid = true;
    private List<String> errors = new ArrayList<>();
    private Map<String, List<String>> fieldErrors = new HashMap<>();
    private String errorCode;
    private String errorMessage;
    
    /**
     * 设置验证是否有效
     */
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    /**
     * 添加错误消息
     */
    public void addErrorMessage(String message) {
        this.valid = false;
        this.errorMessage = message;
        this.errors.add(message);
    }
    
    /**
     * 添加错误
     */
    public void addError(String message) {
        this.valid = false;
        this.errors.add(message);
    }
    
    /**
     * 添加字段错误
     */
    public void addFieldError(String fieldName, String message) {
        this.valid = false;
        fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(message);
    }
    
    /**
     * 检查是否有效
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * 设置错误代码
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    /**
     * 设置错误消息
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * 创建成功的验证结果
     */
    public static ValidationResult success() {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        return result;
    }
    
    /**
     * 创建失败的验证结果
     */
    public static ValidationResult fail(String errorCode, String errorMessage) {
        ValidationResult result = new ValidationResult();
        result.setValid(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }
}