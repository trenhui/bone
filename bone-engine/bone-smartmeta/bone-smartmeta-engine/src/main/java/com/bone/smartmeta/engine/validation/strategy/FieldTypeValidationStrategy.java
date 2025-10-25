package com.bone.smartmeta.engine.validation.strategy;

import com.bone.smartmeta.engine.validation.ValidationResult;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段类型验证策略
 * 验证字段值的类型是否符合元数据定义
 */
public class FieldTypeValidationStrategy implements ValidationStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(FieldTypeValidationStrategy.class);
    
    // 日期格式化器
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    // 支持的字段类型集合
    private static final Set<String> SUPPORTED_TYPES = new HashSet<>(
            Arrays.asList("string", "integer", "int", "long", "double", "boolean", "date", "datetime", "array", "object"));
    
    @Override
    public void validate(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        log.debug("开始验证字段类型，实体类型: {}", entityMetadata.getApiName());
        
        for (SmartFieldMetadata field : entityMetadata.getFields().values()) {
            String fieldName = field.getApiName();
            String fieldLabel = field.getLabel() != null ? field.getLabel() : fieldName;
            String fieldType = getFieldTypeName(field);
            
            if (!entityData.containsKey(fieldName) || entityData.get(fieldName) == null) {
                continue; // 跳过空字段
            }
            
            Object value = entityData.get(fieldName);
            boolean isValidType = true;
            
            // 根据字段类型进行验证
            switch (fieldType.toLowerCase()) {
                case "string":
                    isValidType = value instanceof String;
                    break;
                case "integer":
                case "int":
                    // 整数类型需要确保值为整数
                    isValidType = value instanceof Integer || 
                                 (value instanceof Number && ((Number) value).doubleValue() == Math.floor(((Number) value).doubleValue()));
                    break;
                case "long":
                    // Long类型可以接受整数和长整数
                    isValidType = value instanceof Long || 
                                 (value instanceof Number && ((Number) value).doubleValue() == Math.floor(((Number) value).doubleValue()));
                    break;
                case "double":
                    // Double类型可以接受任何数值
                    isValidType = value instanceof Number;
                    break;
                case "boolean":
                    isValidType = value instanceof Boolean;
                    break;
                case "array":
                    isValidType = value instanceof List || value instanceof Object[];
                    break;
                case "object":
                    isValidType = value instanceof Map;
                    break;
                case "date":
                    // 日期类型验证
                    isValidType = validateDateType(value, fieldName, result, fieldLabel);
                    break;
                case "datetime":
                    // 日期时间类型验证
                    isValidType = validateDateTimeType(value, fieldName, result, fieldLabel);
                    break;
                default:
                    // 未知类型发出警告
                    if (!SUPPORTED_TYPES.contains(fieldType.toLowerCase())) {
                        ValidationResult.ValidationWarning validationWarning = 
                            ValidationResult.ValidationWarning.builder()
                                .fieldPath(fieldName)
                                .message(String.format("字段 '%s' 使用了未知的数据类型: %s", 
                                        fieldLabel, fieldType))
                                .build();
                        result.addWarning(validationWarning);
                    }
                    break;
            }
            
            // 类型不匹配时添加错误
            if (!isValidType) {
                ValidationResult.ValidationError validationError = 
                    ValidationResult.ValidationError.builder()
                        .fieldPath(fieldName)
                        .message(String.format("字段 '%s' 需要 %s 类型的值", 
                                fieldLabel, fieldType))
                        .build();
                result.addError(validationError);
            }
        }
        
        log.debug("字段类型验证完成，实体类型: {}", entityMetadata.getApiName());
    }
    
    /**
     * 验证日期类型
     */
    private boolean validateDateType(Object value, String fieldName, ValidationResult result, String fieldLabel) {
        if (value instanceof java.util.Date) {
            return true;
        } else if (value instanceof String) {
            try {
                // 尝试解析日期格式
                LocalDate.parse((String) value, DATE_FORMATTER);
                return true;
            } catch (DateTimeParseException e) {
                ValidationResult.ValidationError validationError = 
                    ValidationResult.ValidationError.builder()
                        .fieldPath(fieldName)
                        .message(String.format("字段 '%s' 不是有效的日期格式，请使用 %s 格式", 
                                fieldLabel, DATE_FORMATTER.toString()))
                        .build();
                result.addError(validationError);
                return false;
            }
        }
        return false;
    }
    
    /**
     * 验证日期时间类型
     */
    private boolean validateDateTimeType(Object value, String fieldName, ValidationResult result, String fieldLabel) {
        if (value instanceof java.util.Date) {
            return true;
        } else if (value instanceof String) {
            try {
                // 尝试解析日期时间格式
                LocalDateTime.parse((String) value, DATE_TIME_FORMATTER);
                return true;
            } catch (DateTimeParseException e) {
                ValidationResult.ValidationError validationError = 
                    ValidationResult.ValidationError.builder()
                        .fieldPath(fieldName)
                        .message(String.format("字段 '%s' 不是有效的日期时间格式，请使用 %s 格式", 
                                fieldLabel, DATE_TIME_FORMATTER.toString()))
                        .build();
                result.addError(validationError);
                return false;
            }
        }
        return false;
    }
    
    /**
     * 安全获取字段类型名称
     */
    private String getFieldTypeName(SmartFieldMetadata field) {
        try {
            // 尝试直接访问type字段
            java.lang.reflect.Field typeField = field.getClass().getDeclaredField("type");
            typeField.setAccessible(true);
            Object typeValue = typeField.get(field);
            if (typeValue != null) {
                return typeValue.toString();
            }
        } catch (Exception e) {
            // 忽略异常，返回默认值
        }
        return "string"; // 默认返回字符串类型
    }
    
    @Override
    public String getName() {
        return "fieldTypeValidation";
    }
}
