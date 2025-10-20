package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 验证结果类
 * 提供统一的验证结果表示和处理机制
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    // 验证状态
    @Builder.Default
    private boolean isValid = true;
    
    // 全局错误信息列表
    @Builder.Default
    private List<ValidationError> globalErrors = new ArrayList<>();
    
    // 字段错误信息映射（字段名 -> 错误列表）
    @Builder.Default
    private Map<String, List<ValidationError>> fieldErrors = new HashMap<>();
    
    // 警告信息列表
    @Builder.Default
    private List<ValidationWarning> warnings = new ArrayList<>();
    
    // 验证时间戳
    private long timestamp;
    
    // 验证上下文信息
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();
    
    /**
     * 添加全局错误
     */
    public void addGlobalError(String message) {
        addGlobalError(message, null, null);
    }
    
    /**
     * 添加全局错误
     */
    public void addGlobalError(String message, String errorCode) {
        addGlobalError(message, errorCode, null);
    }
    
    /**
     * 添加全局错误
     */
    public void addGlobalError(String message, String errorCode, Map<String, Object> attributes) {
        ValidationError error = ValidationError.builder()
                .message(message)
                .errorCode(errorCode)
                .attributes(attributes)
                .build();
        globalErrors.add(error);
        this.isValid = false;
    }
    
    /**
     * 添加字段错误
     */
    public void addFieldError(String fieldName, String message) {
        addFieldError(fieldName, message, null, null);
    }
    
    /**
     * 添加字段错误
     */
    public void addFieldError(String fieldName, String message, String errorCode) {
        addFieldError(fieldName, message, errorCode, null);
    }
    
    /**
     * 添加字段错误
     */
    public void addFieldError(String fieldName, String message, String errorCode, Map<String, Object> attributes) {
        ValidationError error = ValidationError.builder()
                .field(fieldName)
                .message(message)
                .errorCode(errorCode)
                .attributes(attributes)
                .build();
        
        fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(error);
        this.isValid = false;
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String message) {
        addWarning(message, null, null);
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String message, String warningCode) {
        addWarning(message, warningCode, null);
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String message, String warningCode, Map<String, Object> attributes) {
        ValidationWarning warning = ValidationWarning.builder()
                .message(message)
                .warningCode(warningCode)
                .attributes(attributes)
                .build();
        warnings.add(warning);
    }
    
    /**
     * 添加字段警告
     */
    public void addFieldWarning(String fieldName, String message) {
        addFieldWarning(fieldName, message, null, null);
    }
    
    /**
     * 添加字段警告
     */
    public void addFieldWarning(String fieldName, String message, String warningCode) {
        addFieldWarning(fieldName, message, warningCode, null);
    }
    
    /**
     * 添加字段警告
     */
    public void addFieldWarning(String fieldName, String message, String warningCode, Map<String, Object> attributes) {
        ValidationWarning warning = ValidationWarning.builder()
                .field(fieldName)
                .message(message)
                .warningCode(warningCode)
                .attributes(attributes)
                .build();
        warnings.add(warning);
    }
    
    /**
     * 获取指定字段的错误信息
     */
    public List<ValidationError> getErrorsForField(String fieldName) {
        return fieldErrors.getOrDefault(fieldName, new ArrayList<>());
    }
    
    /**
     * 获取所有错误信息（全局错误 + 字段错误）
     */
    public List<ValidationError> getAllErrors() {
        List<ValidationError> allErrors = new ArrayList<>(globalErrors);
        fieldErrors.values().forEach(allErrors::addAll);
        return allErrors;
    }
    
    /**
     * 获取所有错误消息文本
     */
    public List<String> getAllErrorMessages() {
        return getAllErrors().stream()
                .map(ValidationError::getMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有警告消息文本
     */
    public List<String> getAllWarningMessages() {
        return warnings.stream()
                .map(ValidationWarning::getMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查是否有字段错误
     */
    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
    
    /**
     * 检查是否有全局错误
     */
    public boolean hasGlobalErrors() {
        return !globalErrors.isEmpty();
    }
    
    /**
     * 检查是否有警告
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * 获取错误总数
     */
    public int getErrorCount() {
        return globalErrors.size() + fieldErrors.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * 获取警告总数
     */
    public int getWarningCount() {
        return warnings.size();
    }
    
    /**
     * 合并另一个验证结果
     */
    public void merge(ValidationResult other) {
        if (other == null) {
            return;
        }
        
        // 合并错误状态
        this.isValid = this.isValid && other.isValid;
        
        // 合并全局错误
        this.globalErrors.addAll(other.globalErrors);
        
        // 合并字段错误
        other.fieldErrors.forEach((fieldName, errors) -> {
            this.fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).addAll(errors);
        });
        
        // 合并警告
        this.warnings.addAll(other.warnings);
        
        // 合并上下文
        this.context.putAll(other.context);
    }
    
    /**
     * 创建一个成功的验证结果
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .isValid(true)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建一个失败的验证结果
     */
    public static ValidationResult failure(String errorMessage) {
        ValidationResult result = ValidationResult.builder()
                .isValid(false)
                .timestamp(System.currentTimeMillis())
                .build();
        result.addGlobalError(errorMessage);
        return result;
    }
    
    /**
     * 创建一个失败的验证结果
     */
    public static ValidationResult failure(String fieldName, String errorMessage) {
        ValidationResult result = ValidationResult.builder()
                .isValid(false)
                .timestamp(System.currentTimeMillis())
                .build();
        result.addFieldError(fieldName, errorMessage);
        return result;
    }
    
    /**
     * 验证错误类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field; // 字段名，全局错误为null
        private String message; // 错误消息
        private String errorCode; // 错误代码
        private Map<String, Object> attributes; // 附加属性
    }
    
    /**
     * 验证警告类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String field; // 字段名，全局警告为null
        private String message; // 警告消息
        private String warningCode; // 警告代码
        private Map<String, Object> attributes; // 附加属性
    }
}