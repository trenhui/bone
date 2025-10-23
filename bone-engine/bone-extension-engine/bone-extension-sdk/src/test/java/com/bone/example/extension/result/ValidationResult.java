package com.bone.example.extension.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用验证结果类
 */
@Data
public class ValidationResult {
    // 手动添加无参数构造函数
    public ValidationResult() {
    }
    
    // 手动添加带参构造函数
    public ValidationResult(boolean success, String errorCode, String errorMessage) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    private boolean success;
    private String errorCode;
    private String errorMessage;
    
    /**
     * 创建成功的验证结果
     */
    public static ValidationResult success() {
        ValidationResult result = new ValidationResult();
        result.success = true;
        result.errorCode = null;
        result.errorMessage = null;
        return result;
    }
    
    /**
     * 创建失败的验证结果
     */
    public static ValidationResult fail(String errorCode, String errorMessage) {
        ValidationResult result = new ValidationResult();
        result.success = false;
        result.errorCode = errorCode;
        result.errorMessage = errorMessage;
        return result;
    }
}