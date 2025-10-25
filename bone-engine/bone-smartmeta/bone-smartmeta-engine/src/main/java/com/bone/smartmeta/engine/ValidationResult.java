package com.bone.smartmeta.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证结果类
 * 封装验证过程中的错误和警告信息
 */
public class ValidationResult {
    
    private boolean valid = true;
    private Map<String, List<String>> errors = new HashMap<>();
    private Map<String, List<String>> warnings = new HashMap<>();
    
    /**
     * 验证是否通过
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * 添加错误信息
     */
    public void addError(String fieldName, String errorMessage) {
        valid = false;
        errors.computeIfAbsent(fieldName != null ? fieldName : "general", k -> new ArrayList<>()).add(errorMessage);
    }
    
    /**
     * 添加警告信息
     */
    public void addWarning(String fieldName, String warningMessage) {
        warnings.computeIfAbsent(fieldName != null ? fieldName : "general", k -> new ArrayList<>()).add(warningMessage);
    }
    
    /**
     * 获取字段错误信息
     */
    public Map<String, List<String>> getErrors() {
        return errors;
    }
    
    /**
     * 获取字段警告信息
     */
    public Map<String, List<String>> getWarnings() {
        return warnings;
    }
    
    /**
     * 获取所有错误信息
     */
    public List<String> getAllErrors() {
        List<String> allErrors = new ArrayList<>();
        errors.values().forEach(allErrors::addAll);
        return allErrors;
    }
    
    /**
     * 获取所有警告信息
     */
    public List<String> getAllWarnings() {
        List<String> allWarnings = new ArrayList<>();
        warnings.values().forEach(allWarnings::addAll);
        return allWarnings;
    }
    
    /**
     * 获取错误数量
     */
    public int getErrorCount() {
        return getAllErrors().size();
    }
    
    /**
     * 获取警告数量
     */
    public int getWarningCount() {
        return getAllWarnings().size();
    }
    
    /**
     * 合并另一个验证结果
     */
    public void merge(ValidationResult other) {
        if (other == null) {
            return;
        }
        
        // 合并错误
        for (Map.Entry<String, List<String>> entry : other.errors.entrySet()) {
            String fieldName = entry.getKey();
            for (String errorMessage : entry.getValue()) {
                addError(fieldName, errorMessage);
            }
        }
        
        // 合并警告
        for (Map.Entry<String, List<String>> entry : other.warnings.entrySet()) {
            String fieldName = entry.getKey();
            for (String warningMessage : entry.getValue()) {
                addWarning(fieldName, warningMessage);
            }
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{valid=").append(valid)
          .append(", errors=").append(getErrorCount())
          .append(", warnings=").append(getWarningCount())
          .append("}");
        
        if (getErrorCount() > 0) {
            sb.append("\n错误详情: ");
            for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                sb.append("\n  [").append(entry.getKey()).append("]: ");
                sb.append(String.join(", ", entry.getValue()));
            }
        }
        
        return sb.toString();
    }
}
