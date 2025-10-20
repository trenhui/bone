package com.bone.example.extension.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用验证结果类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean success;
    private String errorCode;
    private String errorMessage;
    
    /**
     * 创建成功的验证结果
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .success(true)
                .build();
    }
    
    /**
     * 创建失败的验证结果
     */
    public static ValidationResult fail(String errorCode, String errorMessage) {
        return ValidationResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}