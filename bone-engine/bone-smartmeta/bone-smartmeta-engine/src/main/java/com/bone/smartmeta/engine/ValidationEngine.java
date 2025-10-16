package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 元数据验证引擎
 * 负责验证实体数据是否符合元数据定义的规则
 */
public class ValidationEngine {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);
    
    /**
     * 验证实体数据
     * 
     * @param entityMetadata 实体元数据
     * @param entityData 实体数据（字段名到值的映射）
     * @return 验证结果，包含错误信息
     */
    public ValidationResult validateEntity(EntityMetadata entityMetadata, Map<String, Object> entityData) {
        ValidationResult result = new ValidationResult();
        
        // 验证必填字段
        validateRequiredFields(entityMetadata, entityData, result);
        
        // 验证字段类型
        validateFieldTypes(entityMetadata, entityData, result);
        
        // 验证字段约束
        validateFieldConstraints(entityMetadata, entityData, result);
        
        // 验证自定义规则
        validateCustomRules(entityMetadata, entityData, result);
        
        return result;
    }
    
    /**
     * 验证必填字段
     */
    private void validateRequiredFields(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        for (FieldMetadata field : entityMetadata.getFields()) {
            if (field.isRequired() && !entityData.containsKey(field.getApiName())) {
                result.addError(field.getApiName(), String.format("字段 '%s' 是必填项", field.getLabel()));
            } else if (field.isRequired() && entityData.get(field.getApiName()) == null) {
                result.addError(field.getApiName(), String.format("字段 '%s' 不能为null", field.getLabel()));
            }
        }
    }
    
    /**
     * 验证字段类型
     */
    private void validateFieldTypes(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        // 实现字段类型验证逻辑
        // TODO: 实现具体的类型验证
    }
    
    /**
     * 验证字段约束
     */
    private void validateFieldConstraints(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        for (FieldMetadata field : entityMetadata.getFields()) {
            String fieldName = field.getApiName();
            Object value = entityData.get(fieldName);
            
            if (value == null) {
                continue;
            }
            
            // 验证字符串长度
            if (value instanceof String && field.getMaxLength() != null) {
                String stringValue = (String) value;
                if (stringValue.length() > field.getMaxLength()) {
                    result.addError(fieldName, String.format("字段 '%s' 的长度不能超过 %d 个字符", 
                            field.getLabel(), field.getMaxLength()));
                }
                if (field.getMinLength() != null && stringValue.length() < field.getMinLength()) {
                    result.addError(fieldName, String.format("字段 '%s' 的长度不能少于 %d 个字符", 
                            field.getLabel(), field.getMinLength()));
                }
            }
            
            // 验证数值范围
            if ((value instanceof Number) && (field.getMaxValue() != null || field.getMinValue() != null)) {
                double numValue = ((Number) value).doubleValue();
                if (field.getMaxValue() != null && numValue > field.getMaxValue()) {
                    result.addError(fieldName, String.format("字段 '%s' 的值不能大于 %s", 
                            field.getLabel(), field.getMaxValue()));
                }
                if (field.getMinValue() != null && numValue < field.getMinValue()) {
                    result.addError(fieldName, String.format("字段 '%s' 的值不能小于 %s", 
                            field.getLabel(), field.getMinValue()));
                }
            }
            
            // 验证正则表达式
            if (value instanceof String && field.getRegexPattern() != null) {
                String stringValue = (String) value;
                if (!Pattern.matches(field.getRegexPattern(), stringValue)) {
                    result.addError(fieldName, String.format("字段 '%s' 的值不符合要求的格式", 
                            field.getLabel()));
                }
            }
        }
    }
    
    /**
     * 验证自定义规则
     */
    private void validateCustomRules(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        for (ValidationRuleMetadata rule : entityMetadata.getValidationRules()) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            try {
                // 这里应该有规则表达式的求值逻辑
                // 暂时简单地跳过实际的规则验证
                // boolean isValid = evaluateRuleExpression(rule.getExpression(), entityData);
                boolean isValid = true; // 暂时假设验证通过
                
                if (!isValid) {
                    String fieldName = rule.getFieldName() != null ? rule.getFieldName() : null;
                    result.addError(fieldName, rule.getMessage());
                }
            } catch (Exception e) {
                log.error("Error evaluating validation rule: {}", rule.getName(), e);
                // 规则执行出错不影响主流程，可以添加到日志或警告列表
                result.addWarning("RuleEvaluationError", String.format("规则 '%s' 执行出错: %s", 
                        rule.getName(), e.getMessage()));
            }
        }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private boolean valid = true;
        private Map<String, List<String>> errors = new HashMap<>();
        private Map<String, List<String>> warnings = new HashMap<>();
        
        public boolean isValid() {
            return valid;
        }
        
        public void addError(String fieldName, String errorMessage) {
            valid = false;
            errors.computeIfAbsent(fieldName != null ? fieldName : "general", k -> new ArrayList<>()).add(errorMessage);
        }
        
        public void addWarning(String fieldName, String warningMessage) {
            warnings.computeIfAbsent(fieldName != null ? fieldName : "general", k -> new ArrayList<>()).add(warningMessage);
        }
        
        public Map<String, List<String>> getErrors() {
            return errors;
        }
        
        public Map<String, List<String>> getWarnings() {
            return warnings;
        }
        
        public List<String> getAllErrors() {
            List<String> allErrors = new ArrayList<>();
            errors.values().forEach(allErrors::addAll);
            return allErrors;
        }
        
        public List<String> getAllWarnings() {
            List<String> allWarnings = new ArrayList<>();
            warnings.values().forEach(allWarnings::addAll);
            return allWarnings;
        }
    }
}