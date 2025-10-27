package com.bone.smartmeta.engine;

// 统一使用metadata包中的类
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.rule.CustomFunctionRegistry;
import com.bone.smartmeta.engine.rule.EvaluationContextFactory;
import com.bone.smartmeta.engine.rule.ExpressionCache;
import com.bone.smartmeta.engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Rule Engine
 * Responsible for parsing and executing business rules, calculated fields, and conditional expressions defined in metadata
 */
@Component
public class RuleEngine {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RuleEngine.class);
    
    private final ExpressionEngine expressionEngine;
    private final MetadataEngine metadataEngine;
    private final ExpressionCache expressionCache;
    private final EvaluationContextFactory contextFactory;
    private final CustomFunctionRegistry functionRegistry;
    
    // Performance metrics
        private final AtomicLong ruleEvaluations = new AtomicLong(0);
        private final AtomicLong ruleFailures = new AtomicLong(0);
        private final AtomicLong fieldCalculations = new AtomicLong(0);
        private final AtomicLong fieldCalculationFailures = new AtomicLong(0);
    
    @Autowired
    public RuleEngine(ExpressionEngine expressionEngine, 
                     MetadataEngine metadataEngine,
                     ExpressionCache expressionCache,
                     EvaluationContextFactory contextFactory,
                     CustomFunctionRegistry functionRegistry) {
        this.expressionEngine = expressionEngine;
        this.metadataEngine = metadataEngine;
        this.expressionCache = expressionCache;
        this.contextFactory = contextFactory;
        this.functionRegistry = functionRegistry;
        
        // 注册内置函数
        registerBuiltInFunctions();
        
        LOGGER.info("Rule engine initialized successfully");
    }
    
    /**
     * Register built-in functions
     */
    private void registerBuiltInFunctions() {
        try {
            // 注册常用函数
            registerFunctionByName("isNull", "检查值是否为null");
            registerFunctionByName("isNotNull", "检查值是否不为null");
            registerFunctionByName("isEmpty", "检查字符串是否为空");
            registerFunctionByName("isNotEmpty", "检查字符串是否不为空");
            registerFunctionByName("length", "获取字符串长度");
            
            LOGGER.info("Built-in functions registered successfully");
        } catch (Exception e) {
            LOGGER.warn("Failed to register built-in functions", e);
        }
    }
    
    private void registerFunctionByName(String functionName, String description) {
        try {
            functionRegistry.registerFunction(functionName, functionName, description);
        } catch (Exception e) {
            LOGGER.debug("Failed to register function {}", functionName, e);
        }
    }
    
    /**
     * Get compiled expression
     */
    private Object getCompiledExpression(String expression) {
        return expressionCache.get(expression, expr -> {
            try {
                // 这里可以根据需要进行表达式编译
                // 目前直接返回表达式字符串，由ExpressionEngine处理
                return expr;
            } catch (Exception e) {
                LOGGER.error("Expression compilation failed: {}", expr, e);
                throw new RuntimeException("Expression compilation failed", e);
            }
        });
    }
    
    /**
     * 计算实体的计算字段
     * 
     * @param entityName 实体名称
     * @param entityData 实体数据
     * @param incremental 是否增量计算
     * @return 计算后的实体数据
     */
    public Map<String, Object> calculateFields(String entityName, Map<String, Object> entityData, boolean incremental) {
        // 获取实体元数据
        Object metadataObj = metadataEngine.getEntityMetadata(entityName);
        EntityMetadata metadata = (metadataObj instanceof EntityMetadata) ? (EntityMetadata) metadataObj : null;
        
        if (metadata == null) {
            LOGGER.warn("Entity metadata not found: {}", entityName);
            return entityData;
        }
        
        // 获取所有计算字段
        List<SmartFieldMetadata> calculatedFields = extractCalculatedFields(metadata);
        
        if (calculatedFields.isEmpty()) {
            return entityData;
        }
        
        // 增量计算优化
        if (incremental) {
            calculatedFields = optimizeCalculatedFields(calculatedFields, entityData, metadata);
        }
        
        // 按依赖关系排序计算字段
        calculatedFields = sortCalculatedFieldsByDependency(calculatedFields, metadata);
        
        // 创建评估上下文
        EvaluationContextFactory.EvaluationContext context = contextFactory.createContext(entityData, null);
        
        // 计算字段值
        Map<String, Object> result = new HashMap<>(entityData);
        for (SmartFieldMetadata field : calculatedFields) {
            calculateField(field, entityName, result, context);
        }
        
        return result;
    }
    
    /**
     * 提取计算字段
     */
    private List<SmartFieldMetadata> extractCalculatedFields(EntityMetadata metadata) {
        List<SmartFieldMetadata> calculatedFields = new ArrayList<>();
        
        try {
            // 使用反射安全地获取字段
            Method getFieldsMethod = metadata.getClass().getMethod("getFields");
            Object fieldsObj = getFieldsMethod.invoke(metadata);
            
            if (fieldsObj instanceof Map) {
                Map<?, ?> fieldsMap = (Map<?, ?>) fieldsObj;
                for (Object fieldObj : fieldsMap.values()) {
                    if (fieldObj instanceof SmartFieldMetadata) {
                        SmartFieldMetadata smartField = (SmartFieldMetadata) fieldObj;
                        
                        // 安全地检查是否为计算字段
                        boolean isCalculated = isFieldCalculated(smartField);
                        String expression = getFieldCalculationExpression(smartField);
                        
                        if (isCalculated && expression != null && !expression.trim().isEmpty()) {
                            calculatedFields.add(smartField);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting calculated fields: {}", e.getMessage());
        }
        
        return calculatedFields;
    }
    
    /**
     * 检查字段是否为计算字段
     */
    private boolean isFieldCalculated(SmartFieldMetadata field) {
        try {
            Method isCalculatedMethod = field.getClass().getMethod("isCalculated");
            return (Boolean) isCalculatedMethod.invoke(field);
        } catch (Exception e) {
            // 如果方法不存在，返回false
            return false;
        }
    }
    
    /**
     * 获取字段计算表达式
     */
    private String getFieldCalculationExpression(SmartFieldMetadata field) {
        try {
            Method getExpressionMethod = field.getClass().getMethod("getCalculationExpression");
            return (String) getExpressionMethod.invoke(field);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 计算单个字段
     */
    private void calculateField(SmartFieldMetadata field, String entityName, 
                              Map<String, Object> result, 
                              EvaluationContextFactory.EvaluationContext context) {
        fieldCalculations.incrementAndGet();
        
        try {
            String expression = field.getCalculationExpression();
            if (expression == null || expression.trim().isEmpty()) {
                handleEmptyExpression(field, result);
                return;
            }
            
            // 获取预编译的表达式
            Object compiledExpression = getCompiledExpression(expression);
            
            // 计算字段值
            Object value = evaluateFieldExpression(compiledExpression, result, context);
            
            // 获取字段名
            String fieldName = getFieldName(field);
            
            // 更新结果和上下文
            updateFieldValue(fieldName, value, result, context);
            
            LOGGER.debug("Field calculation successful: {}.{} = {}", entityName, fieldName, value);
        } catch (Exception e) {
            fieldCalculationFailures.incrementAndGet();
            handleFieldCalculationError(field, entityName, e, result);
        }
    }
    
    /**
     * 处理空表达式的情况
     */
    private void handleEmptyExpression(SmartFieldMetadata field, Map<String, Object> result) {
        try {
            String fieldName = getFieldName(field);
            Object defaultValue = getFieldDefaultValue(field);
            
            if (defaultValue != null && !result.containsKey(fieldName)) {
                result.put(fieldName, defaultValue);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get field default value", e);
        }
    }
    
    /**
     * 获取字段名称
     */
    private String getFieldName(SmartFieldMetadata field) {
        try {
            Field nameField = field.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(field);
        } catch (Exception e) {
            LOGGER.debug("Failed to get field name", e);
            return "unknown_field"; // 返回默认名称
        }
    }
    
    /**
     * 获取字段默认值
     */
    private Object getFieldDefaultValue(SmartFieldMetadata field) {
        try {
            Field defaultValueField = field.getClass().getDeclaredField("defaultValue");
            defaultValueField.setAccessible(true);
            return defaultValueField.get(field);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 更新字段值
     */
    private void updateFieldValue(String fieldName, Object value, 
                                Map<String, Object> result, 
                                EvaluationContextFactory.EvaluationContext context) {
        // 更新结果集
        result.put(fieldName, value);
        
        // 尝试更新评估上下文
        try {
            Method updateMethod = context.getClass().getMethod("updateEntityData", String.class, Object.class);
            updateMethod.invoke(context, fieldName, value);
        } catch (Exception e) {
            // 如果没有这个方法，忽略
        }
    }
    
    /**
     * Handle field calculation error
     */
    private void handleFieldCalculationError(SmartFieldMetadata field, String entityName, 
                                           Exception e, Map<String, Object> result) {
        LOGGER.error("Field calculation failed: {}", entityName, e);
        
        try {
            String fieldName = getFieldName(field);
            
            // 如果结果中没有这个字段，尝试设置默认值
            if (!result.containsKey(fieldName)) {
                Object defaultValue = getFieldDefaultValue(field);
                if (defaultValue != null) {
                    result.put(fieldName, defaultValue);
                }
            }
        } catch (Exception ex) {
            // 反射失败，跳过默认值处理
        }
    }
    
    /**
     * Evaluate field expression
     */
    private Object evaluateFieldExpression(Object compiledExpression, Map<String, Object> entityData, 
                                         EvaluationContextFactory.EvaluationContext context) {
        String expression = compiledExpression.toString();
        try {
            return expressionEngine.evaluateExpression(expression, entityData);
        } catch (Exception e) {
            LOGGER.error("Expression evaluation failed: {}", expression, e);
            throw new RuntimeException("Expression evaluation failed", e);
        }
    }
    
    /**
     * Sort calculated fields by dependency
     */
    private List<SmartFieldMetadata> sortCalculatedFieldsByDependency(List<SmartFieldMetadata> fields, EntityMetadata metadata) {
        if (fields.isEmpty()) {
            return fields;
        }
        
        // 简单实现，后续可优化为拓扑排序
        List<SmartFieldMetadata> sortedFields = new ArrayList<>(fields);
        
        // 尝试根据字段名称排序，确保一致的计算顺序
        sortedFields.sort((f1, f2) -> {
            try {
                String name1 = getFieldName(f1);
                String name2 = getFieldName(f2);
                return Objects.compare(name1, name2, Comparator.nullsLast(String::compareTo));
            } catch (Exception e) {
                // 反射失败，使用默认比较器
                return 0;
            }
        });
        
        return sortedFields;
    }
    
    /**
     * Optimize calculated fields to compute only necessary fields
     */
    private List<SmartFieldMetadata> optimizeCalculatedFields(List<SmartFieldMetadata> fields, 
                                                         Map<String, Object> entityData,
                                                         EntityMetadata metadata) {
        // 检查是否有字段值发生变化
        // 这里简化实现，实际可通过变更跟踪进行更精确的优化
        return fields;
    }
    
    /**
     * Validate business rules
     * 
     * @param entityName the entity name
     * @param entityData the entity data
     * @param triggerEvents the trigger events
     * @return validation result
     */
    public Map<String, Object> validateRules(String entityName, Map<String, Object> entityData, 
                                         List<String> triggerEvents) {
        ValidationResult validationResult = validateRulesWithResult(entityName, entityData, triggerEvents);
        
        // 转换为Map结果
        Map<String, Object> result = new HashMap<>();
        result.put("valid", validationResult.isValid());
        result.put("errors", validationResult.getErrors().stream()
                .map(error -> error.getFieldPath() + ": " + error.getMessage())
                .collect(Collectors.toList()));
        
        return result;
    }
    
    /**
     * 验证业务规则并返回ValidationResult对象
     * 
     * @param entityName 实体名称
     * @param entityData 实体数据
     * @param triggerEvents 触发事件
     * @return 验证结果
     */
    public ValidationResult validateRulesWithResult(String entityName, Map<String, Object> entityData, 
                                                 List<String> triggerEvents) {
        ValidationResult result = ValidationResult.builder().build();
        
        // 获取实体元数据
        Object metadataObj = metadataEngine.getEntityMetadata(entityName);
        EntityMetadata metadata = (metadataObj instanceof EntityMetadata) ? (EntityMetadata) metadataObj : null;
        
        if (metadata == null) {
            LOGGER.warn("Entity metadata not found: {}", entityName);
            result.addError(createGeneralError("Entity metadata not found: " + entityName));
            return result;
        }
        
        // 验证实体规则
        validateEntityRules(metadata, entityData, triggerEvents, result);
        
        return result;
    }
    
    /**
     * 验证实体规则
     * 
     * @param metadata 实体元数据
     * @param entityData 实体数据
     * @param triggerEvents 触发事件列表
     * @param result 验证结果对象
     */
    private void validateEntityRules(EntityMetadata metadata, Map<String, Object> entityData, 
                                   List<String> triggerEvents, ValidationResult result) {
        // 获取所有业务规则
        List<?> businessRules = metadata.getValidationRules();
        if (CollectionUtils.isEmpty(businessRules)) {
            return;
        }
        
        // 创建评估上下文
        EvaluationContextFactory.EvaluationContext context = contextFactory.createContext(entityData, null);
        
        // 转换和过滤规则
        List<Object> rules = businessRules.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // 执行规则验证
        for (Object rule : rules) {
            ruleEvaluations.incrementAndGet();
            
            try {
                // 检查规则是否应该被触发
                if (!shouldTriggerRule(rule, triggerEvents)) {
                    continue;
                }
                
                // 验证规则
                validateSingleRule(rule, entityData, context, result);
                
                // 如果有错误且启用了快速失败模式，停止验证
                if (!result.isValid() && isFailFastEnabled()) {
                    break;
                }
            } catch (Exception e) {
                ruleFailures.incrementAndGet();
                LOGGER.error("Rule validation execution failed", e);
                result.addError(createGeneralError("Rule validation exception: " + e.getMessage()));
                
                if (isFailFastEnabled()) {
                    break;
                }
            }
        }
    }
    
    /**
     * 检查是否启用快速失败模式
     */
    private boolean isFailFastEnabled() {
        // 可以从配置或系统属性中获取
        return true; // 默认启用快速失败
    }
    
    /**
     * 验证单个规则
     */
    private void validateSingleRule(Object rule, Map<String, Object> entityData, 
                                  EvaluationContextFactory.EvaluationContext context, 
                                  ValidationResult result) {
        try {
            // 根据规则类型进行验证
            if (rule instanceof com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) {
                validateBusinessRule((com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) rule, entityData, context, result);
            } else if (rule instanceof com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) {
                validateValidationRule((com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) rule, entityData, context, result);
            } else {
                // 使用反射处理通用规则对象
                validateRuleWithReflection(rule, entityData, context, result);
            }
        } catch (Exception e) {
            ruleFailures.incrementAndGet();
            LOGGER.error("Rule validation failed: {}", rule, e);
            result.addError(createGeneralError("Rule validation exception: " + e.getMessage()));
        }
    }
    
    /**
     * 创建通用错误信息
     */
    private ValidationResult.ValidationError createGeneralError(String message) {
        return ValidationResult.ValidationError.builder()
            .fieldPath("general")
            .message(message)
            .build();
    }
    
    /**
     * 验证业务规则
     */
    private void validateBusinessRule(com.bone.smartmeta.engine.metadata.BusinessRuleMetadata rule, 
                                    Map<String, Object> entityData, 
                                    EvaluationContextFactory.EvaluationContext context, 
                                    ValidationResult result) {
        // 检查规则是否启用
        if (!isRuleEnabled(rule)) {
            return;
        }
        
        try {
            // 检查条件
            String condition = getCondition(rule);
            if (condition != null && !condition.trim().isEmpty()) {
                Object compiledCondition = getCompiledExpression(condition);
                boolean conditionMet = evaluateCondition(compiledCondition, entityData, context);
                if (!conditionMet) {
                    return; // 条件不满足，跳过验证
                }
            }
            
            // 获取并评估表达式
            validateRuleExpression(rule, entityData, context, result);
        } catch (Exception e) {
            LOGGER.error("Business rule validation failed", e);
            result.addError(createGeneralError("Business rule validation exception: " + e.getMessage()));
        }
    }
    
    /**
     * 验证ValidationRuleMetadata规则
     */
    private void validateValidationRule(com.bone.smartmeta.engine.metadata.ValidationRuleMetadata rule, 
                                      Map<String, Object> entityData, 
                                      EvaluationContextFactory.EvaluationContext context, 
                                      ValidationResult result) {
        // 检查规则是否激活
        if (!rule.isEnabled()) {
            return;
        }
        
        try {
            // 获取表达式
            String expression = getRuleExpression(rule);
            if (expression == null || expression.trim().isEmpty()) {
                return;
            }
            
            // 评估表达式
            Object compiledExpression = getCompiledExpression(expression);
            boolean isValid = evaluateRuleExpression(compiledExpression, entityData, context);
            
            if (!isValid) {
                // 构建错误信息
                String fieldName = rule.getFieldName();
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    fieldName = "general";
                }
                
                String errorMessage = getRuleErrorMessage(rule);
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(errorMessage)
                    .build());
            }
        } catch (Exception e) {
            LOGGER.error("Rule validation failed", e);
            result.addError(createGeneralError("Rule validation exception: " + e.getMessage()));
        }
    }
    
    /**
     * 使用反射验证通用规则对象
     */
    private void validateRuleWithReflection(Object rule, Map<String, Object> entityData, 
                                         EvaluationContextFactory.EvaluationContext context, 
                                         ValidationResult result) {
        try {
            // 检查规则是否启用/激活
            if (!isRuleActive(rule)) {
                return;
            }
            
            // 检查条件
            String condition = getStringProperty(rule, "getCondition");
            if (condition != null && !condition.trim().isEmpty()) {
                Object compiledCondition = getCompiledExpression(condition);
                boolean conditionMet = evaluateCondition(compiledCondition, entityData, context);
                if (!conditionMet) {
                    return;
                }
            }
            
            // 获取并评估表达式
            String expression = getStringProperty(rule, "getExpression", "getFormula");
            if (expression == null || expression.trim().isEmpty()) {
                return;
            }
            
            Object compiledExpression = getCompiledExpression(expression);
            boolean isValid = evaluateRuleExpression(compiledExpression, entityData, context);
            
            if (!isValid) {
                // 构建错误信息
                String fieldName = getStringProperty(rule, "getFieldName", "getField");
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    fieldName = "general";
                }
                
                String errorMessage = getStringProperty(rule, "getErrorMessage", "getMessage");
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = "规则验证失败";
                }
                
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(errorMessage)
                    .build());
            }
        } catch (Exception e) {
            LOGGER.warn("Reflection-based rule validation failed", e);
            result.addError(createGeneralError("Reflection-based rule validation exception: " + e.getMessage()));
        }
    }
    
    /**
     * 验证规则表达式
     */
    private void validateRuleExpression(com.bone.smartmeta.engine.metadata.BusinessRuleMetadata rule, 
                                     Map<String, Object> entityData, 
                                     EvaluationContextFactory.EvaluationContext context, 
                                     ValidationResult result) {
        // 获取表达式
        String expression = rule.getExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return;
        }
        
        // 评估表达式
        Object compiledExpression = getCompiledExpression(expression);
        boolean isValid = evaluateRuleExpression(compiledExpression, entityData, context);
        
        if (!isValid) {
            String fieldName = rule.getFieldName();
            if (fieldName == null || fieldName.trim().isEmpty()) {
                fieldName = "general";
            }
            
            String errorMessage = rule.getErrorMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                // 使用反射获取name字段
                String name = "未知规则";
                try {
                    Field nameField = rule.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    name = (String) nameField.get(rule);
                } catch (Exception e) {
                    // 反射失败，使用默认值
                }
                errorMessage = String.format("业务规则验证失败: %s", name);
            }
            
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(errorMessage)
                .build());
        }
    }
    
    /**
     * 检查规则是否启用
     */
    private boolean isRuleEnabled(com.bone.smartmeta.engine.metadata.BusinessRuleMetadata rule) {
        try {
            // 尝试获取isEnabled字段
            try {
                Field isEnabledField = rule.getClass().getDeclaredField("isEnabled");
                isEnabledField.setAccessible(true);
                return isEnabledField.getBoolean(rule);
            } catch (Exception e) {
                // 反射失败，尝试获取enabled字段
                try {
                    Field enabledField = rule.getClass().getDeclaredField("enabled");
                    enabledField.setAccessible(true);
                    return enabledField.getBoolean(rule);
                } catch (Exception ex) {
                    // 如果都失败，默认认为规则未启用
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取规则条件
     */
    private String getCondition(com.bone.smartmeta.engine.metadata.BusinessRuleMetadata rule) {
        try {
            Field conditionField = rule.getClass().getDeclaredField("condition");
            conditionField.setAccessible(true);
            return (String) conditionField.get(rule);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取规则表达式
     */
    private String getRuleExpression(com.bone.smartmeta.engine.metadata.ValidationRuleMetadata rule) {
        try {
            Field expressionField = rule.getClass().getDeclaredField("expression");
            expressionField.setAccessible(true);
            return (String) expressionField.get(rule);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取规则错误消息
     */
    private String getRuleErrorMessage(com.bone.smartmeta.engine.metadata.ValidationRuleMetadata rule) {
        try {
            Field errorMessageField = rule.getClass().getDeclaredField("errorMessage");
            errorMessageField.setAccessible(true);
            String errorMessage = (String) errorMessageField.get(rule);
            
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = rule.getMessage(); // 兼容旧方法
            }
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = String.format("验证规则失败: %s", rule.getName());
            }
            
            return errorMessage;
        } catch (Exception e) {
            return "验证规则失败";
        }
    }
    
    /**
     * 检查规则是否激活
     */
    private boolean isRuleActive(Object rule) {
        try {
            Method isActiveMethod = findMethod(rule.getClass(), "isActive", "isEnabled");
            if (isActiveMethod != null) {
                Boolean isActive = (Boolean) isActiveMethod.invoke(rule);
                return isActive != null && isActive;
            }
        } catch (Exception e) {
            // 忽略错误，默认启用
        }
        return true; // 默认规则是激活的
    }
    
    /**
     * 检查规则是否应该被触发
     */
    private boolean shouldTriggerRule(Object rule, List<String> triggerEvents) {
        if (CollectionUtils.isEmpty(triggerEvents)) {
            return true; // 没有指定触发事件，默认触发
        }
        
        try {
            // 检查BusinessRuleMetadata类型
            if (rule instanceof com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) {
                return shouldTriggerBusinessRule((com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) rule, triggerEvents);
            }
            
            // 检查ValidationRuleMetadata类型
            if (rule instanceof com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) {
                return shouldTriggerValidationRule((com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) rule, triggerEvents);
            }
            
            // 尝试通过反射获取触发事件
            try {
                Method method = rule.getClass().getMethod("getTriggerEvent");
                Object event = method.invoke(rule);
                if (event != null && triggerEvents.contains(event.toString())) {
                    return true;
                }
            } catch (Exception e) {
                // 忽略反射错误
            }
        } catch (Exception e) {
            LOGGER.warn("Rule trigger condition check failed", e);
        }
        
        return true; // 默认触发所有规则
    }
    
    /**
     * 检查业务规则是否应该被触发
     */
    private boolean shouldTriggerBusinessRule(com.bone.smartmeta.engine.metadata.BusinessRuleMetadata rule, List<String> triggerEvents) {
        try {
            // 使用反射获取executionTiming字段
            Field executionTimingField = rule.getClass().getDeclaredField("executionTiming");
            executionTimingField.setAccessible(true);
            String executionTiming = (String) executionTimingField.get(rule);
            
            if (executionTiming != null) {
                // 转换执行时机到触发事件
                String event = timingToEvent(executionTiming);
                if (event != null && triggerEvents.contains(event)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 反射失败，默认触发
        }
        
        return true; // 默认触发
    }
    
    /**
     * 检查验证规则是否应该被触发
     */
    private boolean shouldTriggerValidationRule(com.bone.smartmeta.engine.metadata.ValidationRuleMetadata rule, List<String> triggerEvents) {
        try {
            // 使用反射获取triggerEvent字段
            Field triggerEventField = rule.getClass().getDeclaredField("triggerEvent");
            triggerEventField.setAccessible(true);
            String triggerEvent = (String) triggerEventField.get(rule);
            
            return "ALL".equals(triggerEvent) || triggerEvents.contains(triggerEvent);
        } catch (Exception e) {
            // 如果反射失败，默认返回true
            return true;
        }
    }
    
    /**
     * 将执行时机转换为触发事件
     */
    private String timingToEvent(String executionTiming) {
        switch (executionTiming) {
            case "BEFORE_INSERT":
            case "AFTER_INSERT":
                return "CREATE";
            case "BEFORE_UPDATE":
            case "AFTER_UPDATE":
                return "UPDATE";
            case "BEFORE_DELETE":
            case "AFTER_DELETE":
                return "DELETE";
            default:
                return null;
        }
    }
    
    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(Object compiledCondition, Map<String, Object> entityData, 
                                    EvaluationContextFactory.EvaluationContext context) {
        String condition = compiledCondition.toString();
        try {
            Object result = expressionEngine.evaluateExpression(condition, entityData);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            LOGGER.warn("Condition evaluation failed: {}", condition, e);
            return true; // 条件评估失败时默认通过
        }
    }
    
    /**
     * 评估规则表达式
     */
    private boolean evaluateRuleExpression(Object compiledExpression, Map<String, Object> entityData, 
                                         EvaluationContextFactory.EvaluationContext context) {
        String expression = compiledExpression.toString();
        try {
            Object result = expressionEngine.evaluateExpression(expression, entityData);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            LOGGER.warn("Rule expression evaluation failed: {}", expression, e);
            return false; // 表达式评估失败时默认失败
        }
    }
    
    /**
     * 查找合适的方法
     */
    private Method findMethod(Class<?> clazz, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = clazz.getMethod(methodName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                // 尝试下一个方法名
            }
        }
        return null;
    }
    
    /**
     * 获取字符串属性值
     */
    private String getStringProperty(Object obj, String... methodNames) {
        try {
            Method method = findMethod(obj.getClass(), methodNames);
            if (method != null) {
                Object result = method.invoke(obj);
                return result != null ? result.toString() : null;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get string property", e);
        }
        return null;
    }
    
    /**
     * 获取规则引擎的性能统计信息
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("ruleEvaluations", ruleEvaluations.get());
        stats.put("ruleFailures", ruleFailures.get());
        stats.put("fieldCalculations", fieldCalculations.get());
        stats.put("fieldCalculationFailures", fieldCalculationFailures.get());
        
        // 计算失败率
        if (ruleEvaluations.get() > 0) {
            double ruleFailureRate = (double) ruleFailures.get() / ruleEvaluations.get();
            stats.put("ruleFailureRate", ruleFailureRate);
        }
        
        if (fieldCalculations.get() > 0) {
            double fieldFailureRate = (double) fieldCalculationFailures.get() / fieldCalculations.get();
            stats.put("fieldFailureRate", fieldFailureRate);
        }
        
        return stats;
    }
    
    /**
     * 计算异常类
     */
    public static class CalculationException extends com.bone.smartmeta.engine.exception.CalculationException {
        public CalculationException(String message) {
            super(message, null, null);
        }
        
        public CalculationException(String message, Throwable cause) {
            super(message, null, null, cause);
        }
    }
    
    /**
     * 提供默认的RuleEngine Bean
     */
    @ConditionalOnMissingBean
    @Bean
    public RuleEngine defaultRuleEngine(ExpressionEngine expressionEngine,
                                       MetadataEngine metadataEngine,
                                       ExpressionCache expressionCache,
                                       EvaluationContextFactory contextFactory,
                                       CustomFunctionRegistry functionRegistry) {
        return new RuleEngine(expressionEngine, metadataEngine, expressionCache, contextFactory, functionRegistry);
    }
}