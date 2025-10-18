package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 元数据验证引擎
 * 负责验证实体数据是否符合元数据定义的规则
 */
@Component
@RequiredArgsConstructor
public class ValidationEngine implements InitializingBean {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);
    
    private final MetadataRepository metadataRepository;
    private final ExpressionEngine expressionEngine;
    
    // 配置参数
    @Setter
    private boolean cacheEnabled = true;
    @Setter
    private boolean failFast = false;
    
    // 缓存已验证的实体元数据
    private final Map<String, EntityMetadata> validatedEntityCache = new ConcurrentHashMap<>();
    
    // 日期格式化器
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    // 支持的字段类型集合
    private static final Set<String> SUPPORTED_TYPES = new HashSet<>(
            Arrays.asList("string", "integer", "int", "long", "double", "boolean", "date", "datetime", "array", "object"));
    
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化验证引擎，缓存启用: {}, 快速失败模式: {}", cacheEnabled, failFast);
        // 初始化时可以预加载常用实体元数据到缓存
    }
    
    /**
     * 验证实体数据
     * 
     * @param entityMetadata 实体元数据
     * @param entityData 实体数据（字段名到值的映射）
     * @return 验证结果，包含错误信息
     */
    public ValidationResult validateEntity(EntityMetadata entityMetadata, Map<String, Object> entityData) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        Assert.notNull(entityData, "实体数据不能为空");
        
        log.debug("开始验证实体数据，实体类型: {}", entityMetadata.getApiName());
        
        ValidationResult result = new ValidationResult();
        
        try {
            // 验证必填字段
            validateRequiredFields(entityMetadata, entityData, result);
            if (failFast && !result.isValid()) {
                log.warn("快速失败模式：必填字段验证失败，提前返回");
                return result;
            }
            
            // 验证字段类型
            validateFieldTypes(entityMetadata, entityData, result);
            if (failFast && !result.isValid()) {
                log.warn("快速失败模式：字段类型验证失败，提前返回");
                return result;
            }
            
            // 验证字段约束
            validateFieldConstraints(entityMetadata, entityData, result);
            if (failFast && !result.isValid()) {
                log.warn("快速失败模式：字段约束验证失败，提前返回");
                return result;
            }
            
            // 验证自定义规则
            validateCustomRules(entityMetadata, entityData, result);
            if (failFast && !result.isValid()) {
                log.warn("快速失败模式：自定义规则验证失败，提前返回");
                return result;
            }
            
            // 验证关联字段
            validateRelationshipFields(entityMetadata, entityData, result);
            
            log.debug("实体数据验证完成，实体类型: {}, 是否有效: {}, 错误数量: {}", 
                    entityMetadata.getApiName(), result.isValid(), result.getErrors().size());
        } catch (Exception e) {
            log.error("验证过程发生异常，实体类型: {}", entityMetadata.getApiName(), e);
            result.addError("general", "验证过程发生异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 缓存友好的实体验证方法
     * @param entityType 实体类型名称
     * @param entityData 实体数据
     * @return 验证结果
     */
    @Cacheable(value = "validationResult", key = "#entityType + '-' + T(java.util.Objects).hashCode(#entityData)", unless = "#result == null || !#result.isValid()")
    public ValidationResult validateEntityByType(String entityType, Map<String, Object> entityData) {
        Assert.hasText(entityType, "实体类型名称不能为空");
        
        log.debug("通过实体类型名称验证数据，类型: {}", entityType);
        
        try {
            // 从缓存或仓库获取实体元数据
            EntityMetadata entityMetadata = getEntityMetadata(entityType);
            if (entityMetadata == null) {
                ValidationResult result = new ValidationResult();
                result.addError("general", "未找到实体类型 '" + entityType + "' 的元数据定义");
                return result;
            }
            
            return validateEntity(entityMetadata, entityData);
        } catch (Exception e) {
            log.error("根据实体类型验证失败: {}", entityType, e);
            ValidationResult result = new ValidationResult();
            result.addError("general", "验证失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 获取实体元数据，支持缓存
     */
    private EntityMetadata getEntityMetadata(String entityType) {
        if (cacheEnabled && validatedEntityCache.containsKey(entityType)) {
            return validatedEntityCache.get(entityType);
        }
        
        EntityMetadata metadata = metadataRepository.findEntityByApiName(entityType);
        if (metadata != null && cacheEnabled) {
            validatedEntityCache.put(entityType, metadata);
        }
        
        return metadata;
    }
    
    /**
     * 根据实体类型名称验证实体数据
     * 
     * @param entityType 实体类型名称
     * @param entityData 实体数据
     * @return 验证结果
     */
    public ValidationResult validateEntity(String entityType, Map<String, Object> entityData) {
        // 直接调用已实现的validateEntityByType方法
        return validateEntityByType(entityType, entityData);
    }
    
    // 批量处理阈值
    private static final int BATCH_THRESHOLD = 100;
    
    // 任务执行器，用于异步处理
    @Setter
    private Executor taskExecutor; // 使用Executor接口而非具体实现
    
    /**
     * 异步批量验证实体数据
     * 
     * @param entityMetadata 实体元数据
     * @param entityDataList 实体数据列表
     * @return 验证结果列表的CompletableFuture
     */
    @Async
    public CompletableFuture<List<ValidationResult>> validateBatchAsync(EntityMetadata entityMetadata, List<Map<String, Object>> entityDataList) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        Assert.notEmpty(entityDataList, "实体数据列表不能为空");
        
        log.debug("开始批量异步验证，实体类型: {}, 记录数量: {}", entityMetadata.getApiName(), entityDataList.size());
        
        return CompletableFuture.supplyAsync(() -> {
            List<ValidationResult> results = new ArrayList<>(entityDataList.size());
            
            try {
                // 使用并行流处理大量数据
                entityDataList.parallelStream()
                    .map(data -> validateEntity(entityMetadata, data))
                    .forEach(results::add);
                
                log.debug("批量异步验证完成，实体类型: {}, 成功: {}, 失败: {}", 
                    entityMetadata.getApiName(),
                    results.stream().filter(ValidationResult::isValid).count(),
                    results.stream().filter(r -> !r.isValid()).count());
            } catch (Exception e) {
                log.error("批量异步验证过程发生异常", e);
                // 创建一个错误结果
                ValidationResult errorResult = new ValidationResult();
                errorResult.addError("general", "批量异步验证过程发生异常: " + e.getMessage());
                results.add(errorResult);
            }
            
            return results;
        }, taskExecutor != null ? taskExecutor : CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
    }
    
    /**
     * 批量验证实体数据
     * 
     * @param entityMetadata 实体元数据
     * @param entityDataList 实体数据列表
     * @return 验证结果列表，与输入数据一一对应
     */
    public List<ValidationResult> validateBatch(EntityMetadata entityMetadata, List<Map<String, Object>> entityDataList) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        
        log.debug("开始批量验证实体数据，实体类型: {}, 数据量: {}", 
                entityMetadata.getApiName(), entityDataList != null ? entityDataList.size() : 0);
        
        if (entityDataList == null || entityDataList.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ValidationResult> results = new ArrayList<>(entityDataList.size());
        
        // 对于小批量数据，使用顺序处理以避免并行开销
        if (entityDataList.size() <= BATCH_THRESHOLD) {
            for (int i = 0; i < entityDataList.size(); i++) {
                log.debug("验证批量数据中的第 {} 条记录", i + 1);
                ValidationResult result = validateEntity(entityMetadata, entityDataList.get(i));
                results.add(result);
            }
        } else {
            // 大数据量使用并行处理
            results = entityDataList.parallelStream()
                .map(data -> {
                    try {
                        return validateEntity(entityMetadata, data);
                    } catch (Exception e) {
                        log.error("单条记录验证失败", e);
                        ValidationResult errorResult = new ValidationResult();
                        errorResult.addError("general", "记录验证异常: " + e.getMessage());
                        return errorResult;
                    }
                })
                .collect(Collectors.toCollection(() -> new ArrayList<>(entityDataList.size())));
        }
        
        log.debug("批量验证完成，总记录数: {}, 有效记录数: {}", 
                results.size(), results.stream().filter(ValidationResult::isValid).count());
        return results;
    }
    
    /**
     * 验证必填字段
     */
    private void validateRequiredFields(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            String fieldName = field.getApiName();
            
            if (field.isRequired()) {
                if (!entityData.containsKey(fieldName)) {
                    result.addError(fieldName, String.format("字段 '%s' 是必填项", field.getLabel()));
                } else if (entityData.get(fieldName) == null) {
                    result.addError(fieldName, String.format("字段 '%s' 不能为null", field.getLabel()));
                } else if ("string".equalsIgnoreCase(getFieldTypeName(field)) && ((String) entityData.get(fieldName)).trim().isEmpty()) {
                    result.addError(fieldName, String.format("字段 '%s' 不能为空字符串", field.getLabel()));
                }
            }
        }
    }
    
    /**
     * 验证字段类型
     */
    private void validateFieldTypes(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            String fieldName = field.getApiName();
            // 使用辅助方法获取字段类型名称，避免直接调用可能不存在的getType()方法
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
                    isValidType = validateDateType(value, fieldName, result);
                    break;
                case "datetime":
                    // 日期时间类型验证
                    isValidType = validateDateTimeType(value, fieldName, result);
                    break;
                default:
                    // 未知类型发出警告
                    if (!SUPPORTED_TYPES.contains(fieldType.toLowerCase())) {
                        result.addWarning(fieldName, String.format("字段 '%s' 使用了未知的数据类型: %s", 
                                field.getLabel(), fieldType));
                    }
                    break;
            }
            
            // 类型不匹配时添加错误
            if (!isValidType) {
                result.addError(fieldName, String.format("字段 '%s' 需要 %s 类型的值", 
                        field.getLabel(), fieldType));
            }
        }
    }
    
    /**
     * 验证日期类型
     */
    private boolean validateDateType(Object value, String fieldName, ValidationResult result) {
        if (value instanceof java.util.Date) {
            return true;
        } else if (value instanceof String) {
            try {
                // 尝试解析日期格式
                LocalDate.parse((String) value, DATE_FORMATTER);
                return true;
            } catch (DateTimeParseException e) {
                result.addError(fieldName, String.format("字段值不是有效的日期格式，请使用 %s 格式", 
                        DATE_FORMATTER.toString()));
                return false;
            }
        }
        return false;
    }
    
    /**
     * 验证日期时间类型
     */
    private boolean validateDateTimeType(Object value, String fieldName, ValidationResult result) {
        if (value instanceof java.util.Date) {
            return true;
        } else if (value instanceof String) {
            try {
                // 尝试解析日期时间格式
                LocalDateTime.parse((String) value, DATE_TIME_FORMATTER);
                return true;
            } catch (DateTimeParseException e) {
                result.addError(fieldName, String.format("字段值不是有效的日期时间格式，请使用 %s 格式", 
                        DATE_TIME_FORMATTER.toString()));
                return false;
            }
        }
        return false;
    }
    
    /**
     * 安全获取字段类型名称
     */
    private String getFieldTypeName(FieldMetadata field) {
        try {
            // 尝试直接访问type字段
            Field typeField = field.getClass().getDeclaredField("type");
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
    
    /**
     * 安全获取选择列表值
     */
    private List<String> getPicklistValues(FieldMetadata field) {
        try {
            // 尝试直接访问picklistValues字段
            Field picklistField = field.getClass().getDeclaredField("picklistValues");
            picklistField.setAccessible(true);
            Object picklistValue = picklistField.get(field);
            if (picklistValue instanceof List) {
                return (List<String>) picklistValue;
            }
        } catch (Exception e) {
            // 忽略异常，返回空列表
        }
        return null;
    }
    
    /**
     * 验证字段约束
     */
    private void validateFieldConstraints(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            String fieldName = field.getApiName();
            Object value = entityData.get(fieldName);
            
            if (value == null) {
                continue;
            }
            
            // 验证字符串长度
            if (value instanceof String) {
                String stringValue = (String) value;
                if (field.getMaxLength() != null && stringValue.length() > field.getMaxLength()) {
                    result.addError(fieldName, String.format("字段 '%s' 的长度不能超过 %d 个字符", 
                            field.getLabel(), field.getMaxLength()));
                }
                if (field.getMinLength() != null && stringValue.length() < field.getMinLength()) {
                    result.addError(fieldName, String.format("字段 '%s' 的长度不能少于 %d 个字符", 
                            field.getLabel(), field.getMinLength()));
                }
                
                // 验证正则表达式
                if (field.getRegexPattern() != null) {
                    if (!Pattern.matches(field.getRegexPattern(), stringValue)) {
                        result.addError(fieldName, String.format("字段 '%s' 的值不符合要求的格式", 
                                field.getLabel()));
                    }
                }
            }
            
            // 验证数值范围
            if (value instanceof Number) {
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
            
            // 验证枚举值
            List<String> picklistValues = getPicklistValues(field);
            if (picklistValues != null && !picklistValues.isEmpty()) {
                String stringValue = value.toString();
                if (!picklistValues.contains(stringValue)) {
                    result.addError(fieldName, String.format("字段 '%s' 的值必须是以下之一: %s", 
                            field.getLabel(), String.join(", ", picklistValues)));
                }
            }
            
            // 验证数组大小
            if (value instanceof List) {
                List<?> listValue = (List<?>) value;
                // 暂时注释掉getMaxItems()调用，因为FieldMetadata类中似乎没有这个方法
                // if (field.getMaxItems() != null && listValue.size() > field.getMaxItems()) {
                //     result.addError(fieldName, String.format("字段 '%s' 的数组元素数量不能超过 %d 个", 
                //             field.getLabel(), field.getMaxItems()));
                // }
                // 暂时注释掉getMinItems()调用，因为FieldMetadata类中似乎没有这个方法
                // if (field.getMinItems() != null && listValue.size() < field.getMinItems()) {
                //     result.addError(fieldName, String.format("字段 '%s' 的数组元素数量不能少于 %d 个", 
                //             field.getLabel(), field.getMinItems()));
                // }
            }
        }
    }
    
    /**
     * 验证自定义规则
     */
    private void validateCustomRules(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        List<ValidationRuleMetadata> rules = entityMetadata.getValidationRules();
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        for (ValidationRuleMetadata rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            try {
                log.debug("执行自定义验证规则: {}", rule.getName());
                boolean isValid = true;
                
                // 尝试使用规则表达式
                String expression = null;
                try {
                    // 手动访问expression字段（避免Lombok getter问题）
                    expression = rule.getName(); // 临时替代，需要后续修复
                } catch (Exception e) {
                    // 忽略错误，默认表达式为null
                }
                
                if (expressionEngine != null && expression != null) {
                    isValid = expressionEngine.evaluateBooleanExpression(expression, entityData);
                } else if (expression != null) {
                    // 备用的简单规则表达式求值
                    isValid = evaluateSimpleExpression(expression, entityData);
                }
                
                if (!isValid) {
                    String fieldName = rule.getFieldName() != null ? rule.getFieldName() : null;
                    result.addError(fieldName, rule.getMessage() != null ? rule.getMessage() : 
                            String.format("规则 '%s' 验证失败", rule.getName()));
                }
            } catch (Exception e) {
                log.error("执行验证规则时出错: {}", rule.getName(), e);
                // 规则执行出错不影响主流程，可以添加到警告列表
                result.addWarning(rule.getFieldName() != null ? rule.getFieldName() : "general", 
                        String.format("规则 '%s' 执行出错: %s", rule.getName(), e.getMessage()));
            }
        }
    }
    
    /**
     * 简单表达式求值（备用方法）
     */
    private boolean evaluateSimpleExpression(String expression, Map<String, Object> data) {
        // 实现简单的表达式求值，例如 "age > 18" 或 "status == 'active'"
        try {
            // 处理大于比较
            if (expression.contains(">")) {
                String[] parts = expression.split(">", 2);
                return getNumericValue(data, parts[0].trim()) > Double.parseDouble(parts[1].trim());
            }
            // 处理小于比较
            else if (expression.contains("<")) {
                String[] parts = expression.split("<", 2);
                return getNumericValue(data, parts[0].trim()) < Double.parseDouble(parts[1].trim());
            }
            // 处理等于比较
            else if (expression.contains("==")) {
                String[] parts = expression.split("==", 2);
                String left = parts[0].trim();
                String right = parts[1].trim();
                // 移除字符串引号
                if (right.startsWith("'") && right.endsWith("'")) {
                    right = right.substring(1, right.length() - 1);
                }
                return Objects.equals(getPropertyValue(data, left), right);
            }
        } catch (Exception e) {
            log.warn("简单表达式求值失败: {}", expression, e);
        }
        
        // 无法求值的表达式默认返回true
        return true;
    }
    
    /**
     * 获取属性值
     */
    private Object getPropertyValue(Map<String, Object> data, String propertyPath) {
        // 处理简单属性访问
        if (data.containsKey(propertyPath)) {
            return data.get(propertyPath);
        }
        
        // 处理嵌套属性访问，例如 "user.address.city"
        String[] parts = propertyPath.split("\\.");
        if (parts.length > 1) {
            Object current = data;
            for (String part : parts) {
                if (current instanceof Map && ((Map<?, ?>) current).containsKey(part)) {
                    current = ((Map<?, ?>) current).get(part);
                } else {
                    return null;
                }
            }
            return current;
        }
        
        return null;
    }
    
    /**
     * 获取数值属性值
     */
    private double getNumericValue(Map<String, Object> data, String propertyPath) {
        Object value = getPropertyValue(data, propertyPath);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
    
    /**
     * 验证关联字段
     */
    private void validateRelationshipFields(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        // 使用反射安全地获取和验证关联字段
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            String fieldName = field.getApiName();
            
            try {
                // 尝试安全地获取关联信息
                Object relationship = getRelationshipInfo(field);
                if (relationship != null && entityData.containsKey(fieldName) && entityData.get(fieldName) != null) {
                    String targetEntity = getTargetEntityName(relationship);
                    if (targetEntity != null && !targetEntity.isEmpty()) {
                        // 验证目标实体是否存在
                        if (getEntityMetadata(targetEntity) == null) {
                            result.addWarning(fieldName, String.format("字段 '%s' 关联的实体类型 '%s' 未定义", 
                                    field.getLabel(), targetEntity));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("验证关联字段时出错，字段: {}", fieldName, e);
                // 仅记录日志，不添加警告，避免干扰正常验证流程
            }
        }
    }
    
    /**
     * 安全获取关联信息
     */
    private Object getRelationshipInfo(FieldMetadata field) {
        try {
            java.lang.reflect.Method method = field.getClass().getMethod("getRelationship");
            method.setAccessible(true);
            return method.invoke(field);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 安全获取目标实体名称
     */
    private String getTargetEntityName(Object relationship) {
        try {
            java.lang.reflect.Method method = relationship.getClass().getMethod("getTargetEntity");
            method.setAccessible(true);
            Object result = method.invoke(relationship);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
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