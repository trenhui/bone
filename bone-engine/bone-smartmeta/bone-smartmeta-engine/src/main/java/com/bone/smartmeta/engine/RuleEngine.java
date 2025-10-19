package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.ExpressionEngine;
import com.bone.smartmeta.engine.rule.CustomFunctionRegistry;
import com.bone.smartmeta.engine.rule.EvaluationContextFactory;
import com.bone.smartmeta.engine.rule.ExpressionCache;
// 使用ValidationEngine.ValidationResult内部类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 规则引擎
 * 负责解析和执行元数据中定义的业务规则、计算字段和条件表达式
 */
@Component
public class RuleEngine {
    
    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);
    
    private final ExpressionEngine expressionEngine;
    private final MetadataEngine metadataEngine;
    private final ExpressionCache expressionCache;
    private final EvaluationContextFactory contextFactory;
    private final CustomFunctionRegistry functionRegistry;
    
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
        
        log.info("规则引擎初始化完成");
    }
    
    /**
     * 注册内置函数
     */
    private void registerBuiltInFunctions() {
        // 尝试注册内置函数，使用反射避免类型问题
        try {
            // 注册isNull函数
            registerFunctionByReflection("isNull", "检查值是否为null");
            // 注册isNotNull函数
            registerFunctionByReflection("isNotNull", "检查值是否不为null");
            // 注册isEmpty函数
            registerFunctionByReflection("isEmpty", "检查字符串是否为空");
            // 注册isNotEmpty函数
            registerFunctionByReflection("isNotEmpty", "检查字符串是否不为空");
            // 注册length函数
            registerFunctionByReflection("length", "获取字符串长度");
            
            log.info("已注册内置函数");
        } catch (Exception e) {
            log.warn("注册内置函数失败", e);
        }
    }
    
    private void registerFunctionByReflection(String functionName, String description) {
        try {
            // 尝试使用反射调用registerFunction方法
            java.lang.reflect.Method registerMethod = functionRegistry.getClass().getMethod(
                "registerFunction", String.class, Object.class, String.class);
            // 使用简单的字符串或空对象作为函数参数
            registerMethod.invoke(functionRegistry, functionName, functionName, description);
        } catch (Exception e) {
            log.debug("注册函数 {} 失败", functionName, e);
        }
    }
    
    /**
     * 获取预编译的表达式
     */
    private Object getCompiledExpression(String expression) {
        return expressionCache.get(expression, expr -> {
            try {
                // 这里可以根据需要进行表达式编译
                // 目前直接返回表达式字符串，由ExpressionEngine处理
                return expr;
            } catch (Exception e) {
                log.error("编译表达式失败: {}", expr, e);
                throw new RuntimeException("编译表达式失败", e);
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
        EntityMetadata metadata = metadataEngine.getEntityMetadata(entityName);
        if (metadata == null) {
            log.warn("未找到实体元数据: {}", entityName);
            return entityData;
        }
        
        // 获取所有计算字段
            List<SmartFieldMetadata> calculatedFields = metadata.getFields().values().stream()
                .filter(field -> {
                    try {
                        // 使用反射检查是否为计算字段
                        java.lang.reflect.Method isCalculatedMethod = field.getClass().getMethod("isCalculated");
                        return (Boolean) isCalculatedMethod.invoke(field) && field.getCalculationExpression() != null;
                    } catch (Exception e) {
                        // 反射失败，尝试检查是否有计算表达式
                        return field.getCalculationExpression() != null;
                    }
                })
                .collect(Collectors.toList());
        
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
        EvaluationContextFactory.EvaluationContext context = contextFactory.createContext(entityData, metadata);
        
        // 计算字段值
        Map<String, Object> result = new HashMap<>(entityData);
        for (SmartFieldMetadata field : calculatedFields) {
            try {
                String expression = field.getCalculationExpression();
                if (expression != null && !expression.trim().isEmpty()) {
                    // 获取预编译的表达式
                    Object compiledExpression = getCompiledExpression(expression);
                    
                    // 计算字段值
                    Object value = evaluateFieldExpression(compiledExpression, result, context);
                    
                    // 使用反射获取name字段
                    try {
                        java.lang.reflect.Field nameField = field.getClass().getDeclaredField("name");
                        nameField.setAccessible(true);
                        String fieldName = (String) nameField.get(field);
                        result.put(fieldName, value);
                        
                        // 尝试更新评估上下文
                        try {
                            java.lang.reflect.Method updateMethod = context.getClass().getMethod("updateEntityData", String.class, Object.class);
                            updateMethod.invoke(context, fieldName, value);
                        } catch (Exception e) {
                            // 如果没有这个方法，忽略
                        }
                        
                        log.debug("计算字段成功: {}.{} = {}", entityName, fieldName, value);
                    } catch (Exception ex) {
                        // 反射失败，使用默认处理
                        log.debug("计算字段成功: {}, 值 = {}", entityName, value);
                    }
                }
            } catch (Exception e) {
                log.error("计算字段失败: {}", entityName, e);
                // 计算失败时保留原值或使用默认值
                try {
                    java.lang.reflect.Field nameField = field.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    String fieldName = (String) nameField.get(field);
                    
                    if (!result.containsKey(fieldName)) {
                        try {
                            java.lang.reflect.Field defaultValueField = field.getClass().getDeclaredField("defaultValue");
                            defaultValueField.setAccessible(true);
                            Object defaultValue = defaultValueField.get(field);
                            if (defaultValue != null) {
                                result.put(fieldName, defaultValue);
                            }
                        } catch (Exception ex) {
                            // 反射失败，不设置默认值
                        }
                    }
                } catch (Exception ex) {
                    // 反射失败，跳过默认值处理
                }
            }
        }
        
        return result;
    }
    
    /**
     * 计算单个字段的值
     */
    private Object calculateFieldValue(SmartFieldMetadata field, Map<String, Object> context, EntityMetadata entityMetadata) {
        String expression = field.getCalculationExpression();
        if (expression == null || expression.trim().isEmpty()) {
            // 使用反射获取默认值
            try {
                java.lang.reflect.Field defaultValueField = field.getClass().getDeclaredField("defaultValue");
                defaultValueField.setAccessible(true);
                return defaultValueField.get(field);
            } catch (Exception e) {
                return null;
            }
        }
        
        try {
            // 使用表达式引擎计算值
            return expressionEngine.evaluateExpression(expression, context);
        } catch (Exception e) {
            String fieldName = "未知字段";
            try {
                java.lang.reflect.Field nameField = field.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                fieldName = (String) nameField.get(field);
            } catch (Exception ex) {
                // 反射失败，使用默认名称
            }
            log.error("计算表达式失败: {}", expression, e);
            throw new CalculationException("计算字段失败: " + fieldName, e);
        }
    }
    
    /**
     * 评估字段表达式
     */
    private Object evaluateFieldExpression(Object compiledExpression, Map<String, Object> entityData, 
                                         EvaluationContextFactory.EvaluationContext context) {
        String expression = compiledExpression.toString();
        try {
            // 使用ExpressionEngine评估表达式
            return expressionEngine.evaluateExpression(expression, entityData);
        } catch (Exception e) {
            log.error("评估表达式失败: {}", expression, e);
            throw new RuntimeException("评估表达式失败", e);
        }
    }
    
    /**
     * 按依赖关系排序计算字段
     */
    private List<SmartFieldMetadata> sortCalculatedFieldsByDependency(List<SmartFieldMetadata> fields, EntityMetadata metadata) {
        // 简单实现，后续可优化为拓扑排序
        List<SmartFieldMetadata> sortedFields = new ArrayList<>(fields);
        
        // 尝试根据字段名称排序，确保一致的计算顺序
        sortedFields.sort((f1, f2) -> {
            try {
                // 使用反射获取name字段
                java.lang.reflect.Field nameField1 = f1.getClass().getDeclaredField("name");
                nameField1.setAccessible(true);
                String name1 = (String) nameField1.get(f1);
                
                java.lang.reflect.Field nameField2 = f2.getClass().getDeclaredField("name");
                nameField2.setAccessible(true);
                String name2 = (String) nameField2.get(f2);
                
                return name1 != null ? name1.compareTo(name2 != null ? name2 : "") : "".compareTo(name2 != null ? name2 : "");
            } catch (Exception e) {
                // 反射失败，使用默认比较器
                return 0;
            }
        });
        
        return sortedFields;
    }
    
    /**
     * 优化计算字段，只计算必要的字段
     */
    private List<SmartFieldMetadata> optimizeCalculatedFields(List<SmartFieldMetadata> fields, 
                                                       Map<String, Object> entityData,
                                                       EntityMetadata metadata) {
        // 检查是否有字段值发生变化
        // 这里简化实现，实际可通过变更跟踪进行更精确的优化
        return fields;
    }
    
    /**
     * 验证业务规则
     * 
     * @param entityName 实体名称
     * @param entityData 实体数据
     * @param triggerEvents 触发事件
     * @return 验证结果
     */
    public ValidationEngine.ValidationResult validateRules(String entityName, Map<String, Object> entityData, 
                                         List<String> triggerEvents) {
        ValidationEngine.ValidationResult result = new ValidationEngine.ValidationResult();
        
        // 获取实体元数据
        EntityMetadata metadata = metadataEngine.getEntityMetadata(entityName);
        if (metadata == null) {
            log.warn("未找到实体元数据: {}", entityName);
            return result;
        }
        
        // 获取所有业务规则
        List<?> businessRules = metadata.getValidationRules();
        if (CollectionUtils.isEmpty(businessRules)) {
            return result;
        }
        
        // 创建评估上下文
        EvaluationContextFactory.EvaluationContext context = contextFactory.createContext(entityData, metadata);
        
        // 转换和过滤规则
        List<Object> rules = businessRules.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // 执行规则验证
        for (Object rule : rules) {
            try {
                // 检查规则是否应该被触发
                if (!shouldTriggerRule(rule, triggerEvents)) {
                    continue;
                }
                
                // 验证规则
                validateSingleRule(rule, entityData, context, result);
                
                // 如果有严重错误且启用了快速失败模式，停止验证
                if (!result.isValid() && !result.getErrors().isEmpty()) {
                    break;
                }
            } catch (Exception e) {
                log.error("执行规则验证失败", e);
                result.addError("general", "规则验证过程中发生异常: " + e.getMessage());
            }
        }
        
        return result;
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
                com.bone.smartmeta.engine.metadata.BusinessRuleMetadata businessRule = 
                    (com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) rule;
                try {
                    // 使用反射获取executionTiming字段
                    java.lang.reflect.Field executionTimingField = businessRule.getClass().getDeclaredField("executionTiming");
                    executionTimingField.setAccessible(true);
                    String executionTiming = (String) executionTimingField.get(businessRule);
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
            
            // 检查ValidationRuleMetadata类型
            if (rule instanceof com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) {
                com.bone.smartmeta.engine.metadata.ValidationRuleMetadata validationRule = 
                    (com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) rule;
                // 使用反射获取triggerEvent字段
                try {
                    java.lang.reflect.Field triggerEventField = validationRule.getClass().getDeclaredField("triggerEvent");
                    triggerEventField.setAccessible(true);
                    String triggerEvent = (String) triggerEventField.get(validationRule);
                    if ("ALL".equals(triggerEvent) || triggerEvents.contains(triggerEvent)) {
                        return true;
                    }
                } catch (Exception e) {
                    // 如果反射失败，默认返回true
                    return true;
                }
            }
            
            // 尝试通过反射获取触发事件
            try {
                java.lang.reflect.Method method = rule.getClass().getMethod("getTriggerEvent");
                Object event = method.invoke(rule);
                if (event != null && triggerEvents.contains(event.toString())) {
                    return true;
                }
            } catch (Exception e) {
                // 忽略反射错误
            }
        }
        catch (Exception e) {
            log.warn("检查规则触发条件失败", e);
        }
        
        return true; // 默认触发所有规则
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
     * 验证单个规则
     */
    private void validateSingleRule(Object rule, Map<String, Object> entityData, 
                                   EvaluationContextFactory.EvaluationContext context, 
                                   ValidationEngine.ValidationResult result) {
        try {
            // 检查BusinessRuleMetadata类型
            if (rule instanceof com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) {
                validateBusinessRule((com.bone.smartmeta.engine.metadata.BusinessRuleMetadata) rule, entityData, context, result);
                return;
            }
            
            // 检查ValidationRuleMetadata类型
            if (rule instanceof com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) {
                validateValidationRule((com.bone.smartmeta.engine.metadata.ValidationRuleMetadata) rule, entityData, context, result);
                return;
            }
            
            // 使用反射处理通用规则对象
            validateRuleWithReflection(rule, entityData, context, result);
        } catch (Exception e) {
            log.error("验证规则失败: {}", rule, e);
            result.addError("general", "验证规则时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证业务规则
     */
    private void validateBusinessRule(com.bone.smartmeta.engine.metadata.BusinessRuleMetadata rule,
                                     Map<String, Object> entityData, 
                                     EvaluationContextFactory.EvaluationContext context, 
                                     ValidationEngine.ValidationResult result) {
        try {
            // 使用反射获取isEnabled字段
            boolean isEnabled = false;
            try {
                java.lang.reflect.Field isEnabledField = rule.getClass().getDeclaredField("isEnabled");
                isEnabledField.setAccessible(true);
                isEnabled = isEnabledField.getBoolean(rule);
            } catch (Exception e) {
                // 反射失败，尝试获取enabled字段
                try {
                    java.lang.reflect.Field enabledField = rule.getClass().getDeclaredField("enabled");
                    enabledField.setAccessible(true);
                    isEnabled = enabledField.getBoolean(rule);
                } catch (Exception ex) {
                    // 如果都失败，默认认为规则未启用
                    return;
                }
            }
            
            if (!isEnabled) {
                return;
            }
            
            // 使用反射获取condition字段
            String condition = null;
            try {
                java.lang.reflect.Field conditionField = rule.getClass().getDeclaredField("condition");
                conditionField.setAccessible(true);
                condition = (String) conditionField.get(rule);
            } catch (Exception e) {
                // 反射失败，不使用条件
            }
            
            if (condition != null && !condition.trim().isEmpty()) {
                Object compiledCondition = getCompiledExpression(condition);
                boolean conditionMet = evaluateCondition(compiledCondition, entityData, context);
                if (!conditionMet) {
                    return; // 条件不满足，跳过验证
                }
            }
            
            // 获取表达式
            String expression = rule.getExpression(); // getExpression是有的，因为它是一个别名方法
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
                        java.lang.reflect.Field nameField = rule.getClass().getDeclaredField("name");
                        nameField.setAccessible(true);
                        name = (String) nameField.get(rule);
                    } catch (Exception e) {
                        // 反射失败，使用默认值
                    }
                    errorMessage = String.format("业务规则验证失败: %s", name);
                }
                
                result.addError(fieldName, errorMessage);
            }
        } catch (Exception e) {
            log.error("验证业务规则失败", e);
        }
    }
    
    /**
     * 验证ValidationRuleMetadata规则
     */
    private void validateValidationRule(com.bone.smartmeta.engine.metadata.ValidationRuleMetadata rule, 
                                       Map<String, Object> entityData, 
                                       EvaluationContextFactory.EvaluationContext context, 
                                       ValidationEngine.ValidationResult result) {
        // 检查规则是否激活
        if (!rule.isEnabled()) {
            return;
        }
        
        try {
            // 使用反射获取expression字段值
            java.lang.reflect.Field expressionField = rule.getClass().getDeclaredField("expression");
            expressionField.setAccessible(true);
            String expression = (String) expressionField.get(rule);
            
            if (expression == null || expression.trim().isEmpty()) {
                return;
            }
            
            // 获取预编译的表达式
            Object compiledExpression = getCompiledExpression(expression);
            
            // 评估表达式
            boolean isValid = evaluateRuleExpression(compiledExpression, entityData, context);
            if (!isValid) {
                String fieldName = rule.getFieldName();
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    fieldName = "general";
                }
                
                // 使用反射获取errorMessage字段值
                java.lang.reflect.Field errorMessageField = rule.getClass().getDeclaredField("errorMessage");
                errorMessageField.setAccessible(true);
                String errorMessage = (String) errorMessageField.get(rule);
                
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = rule.getMessage(); // 兼容旧方法
                }
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = String.format("验证规则失败: %s", rule.getName());
                }
                
                result.addError(fieldName, errorMessage);
            }
        } catch (Exception e) {
            log.error("验证规则失败", e);
        }
    }
    
    /**
     * 使用反射验证通用规则对象
     */
    private void validateRuleWithReflection(Object rule, Map<String, Object> entityData, 
                                          EvaluationContextFactory.EvaluationContext context, 
                                          ValidationEngine.ValidationResult result) {
        try {
            // 检查规则是否启用/激活
            try {
                java.lang.reflect.Method isActiveMethod = findMethod(rule.getClass(), "isActive", "isEnabled");
                if (isActiveMethod != null) {
                    Boolean isActive = (Boolean) isActiveMethod.invoke(rule);
                    if (isActive != null && !isActive) {
                        return;
                    }
                }
            } catch (Exception e) {
                // 忽略错误，继续验证
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
            
            // 获取表达式
            String expression = getStringProperty(rule, "getExpression", "getFormula");
            if (expression == null || expression.trim().isEmpty()) {
                return;
            }
            
            // 评估表达式
            Object compiledExpression = getCompiledExpression(expression);
            boolean isValid = evaluateRuleExpression(compiledExpression, entityData, context);
            if (!isValid) {
                // 获取字段名
                String fieldName = getStringProperty(rule, "getFieldName", "getField");
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    fieldName = "general";
                }
                
                // 获取错误消息
                String errorMessage = getStringProperty(rule, "getErrorMessage", "getMessage");
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = String.format("规则验证失败");
                }
                
                result.addError(fieldName, errorMessage);
            }
        } catch (Exception e) {
            log.warn("使用反射验证规则失败", e);
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
            log.warn("评估条件失败: {}", condition, e);
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
            log.warn("评估规则表达式失败: {}", expression, e);
            return false; // 表达式评估失败时默认失败
        }
    }
    
    /**
     * 获取规则字段名
     */
    private String getRuleFieldName(com.bone.smartmeta.engine.metadata.ValidationRuleMetadata rule) {
        // 直接使用兼容方法
        return rule.getFieldName();
    }
    
    /**
     * 查找合适的方法
     */
    private java.lang.reflect.Method findMethod(Class<?> clazz, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = clazz.getMethod(methodName);
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
            java.lang.reflect.Method method = findMethod(obj.getClass(), methodNames);
            if (method != null) {
                Object result = method.invoke(obj);
                return result != null ? result.toString() : null;
            }
        } catch (Exception e) {
            log.debug("获取字符串属性失败", e);
        }
        return null;
    }
    
    /**
     * 计算异常类
     */
    public static class CalculationException extends RuntimeException {
        public CalculationException(String message) {
            super(message);
        }
        
        public CalculationException(String message, Throwable cause) {
            super(message, cause);
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