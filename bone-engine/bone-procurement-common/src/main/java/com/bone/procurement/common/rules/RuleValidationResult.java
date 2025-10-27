package com.bone.procurement.common.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则验证结果
 * 用于封装业务规则验证的结果信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleValidationResult {
    
    /**
     * 规则名称
     */
    private String ruleName;
    
    /**
     * 是否验证通过
     */
    private boolean valid;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 严重程度
     */
    private Severity severity;
    
    /**
     * 关联字段
     */
    private String fieldName;
    
    /**
     * 严重程度枚举
     */
    public enum Severity {
        ERROR,      // 错误：阻止操作继续
        WARNING,    // 警告：提示但允许继续
        INFO        // 信息：仅提供信息
    }
}