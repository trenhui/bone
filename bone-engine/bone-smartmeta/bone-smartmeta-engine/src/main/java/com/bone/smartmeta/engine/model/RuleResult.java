package com.bone.smartmeta.engine.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 业务规则执行结果
 * 包含规则执行的成功/失败状态、错误信息和警告信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {
    
    /**
     * 是否所有规则都执行成功
     */
    private boolean success;
    
    /**
     * 错误信息列表
     */
    @Builder.Default
    private List<RuleError> errors = new ArrayList<>();
    
    /**
     * 警告信息列表
     */
    @Builder.Default
    private List<RuleWarning> warnings = new ArrayList<>();
    
    /**
     * 规则执行过程中的附加信息
     */
    @Builder.Default
    private Map<String, Object> additionalInfo = new java.util.HashMap<>();
    
    /**
     * 添加错误信息
     */
    public void addError(String ruleName, String message) {
        errors.add(new RuleError(ruleName, message));
        this.success = false;
    }
    
    /**
     * 添加警告信息
     */
    public void addWarning(String ruleName, String message) {
        warnings.add(new RuleWarning(ruleName, message));
    }
    
    /**
     * 获取错误消息的汇总文本
     */
    public String getErrorMessage() {
        StringBuilder sb = new StringBuilder();
        for (RuleError error : errors) {
            sb.append("[")
              .append(error.getRuleName())
              .append("] ")
              .append(error.getMessage())
              .append("; ");
        }
        return sb.toString();
    }
    
    /**
     * 规则错误信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleError {
        private String ruleName;
        private String message;
    }
    
    /**
     * 规则警告信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleWarning {
        private String ruleName;
        private String message;
    }
}