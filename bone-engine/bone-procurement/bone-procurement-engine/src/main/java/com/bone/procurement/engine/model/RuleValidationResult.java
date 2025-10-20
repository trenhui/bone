package com.bone.procurement.engine.model;

import lombok.Builder;
import lombok.Data;

/**
 * 规则验证结果类
 * 存储业务规则验证的详细信息和结果
 */
@Data
@Builder
public class RuleValidationResult {
    
    /**
     * 规则ID
     */
    private String ruleId;
    
    /**
     * 规则名称
     */
    private String ruleName;
    
    /**
     * 是否通过验证
     */
    private boolean passed;
    
    /**
     * 错误代码（如果验证失败）
     */
    private String errorCode;
    
    /**
     * 错误消息（如果验证失败）
     */
    private String errorMessage;
    
    /**
     * 错误级别：ERROR、WARNING、INFO
     */
    private String severity;
    
    /**
     * 相关字段（如果验证失败与特定字段相关）
     */
    private String relatedField;
    
    /**
     * 建议操作
     */
    private String suggestedAction;
    
    /**
     * 创建通过的验证结果
     */
    public static RuleValidationResult success(String ruleId, String ruleName) {
        return RuleValidationResult.builder()
                .ruleId(ruleId)
                .ruleName(ruleName)
                .passed(true)
                .severity("INFO")
                .build();
    }
    
    /**
     * 创建失败的验证结果
     */
    public static RuleValidationResult failure(String ruleId, String ruleName, String errorCode, String errorMessage) {
        return RuleValidationResult.builder()
                .ruleId(ruleId)
                .ruleName(ruleName)
                .passed(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .severity("ERROR")
                .build();
    }
    
    /**
     * 创建警告级别的验证结果
     */
    public static RuleValidationResult warning(String ruleId, String ruleName, String errorMessage) {
        return RuleValidationResult.builder()
                .ruleId(ruleId)
                .ruleName(ruleName)
                .passed(true)  // 警告不阻止流程
                .errorMessage(errorMessage)
                .severity("WARNING")
                .build();
    }
}