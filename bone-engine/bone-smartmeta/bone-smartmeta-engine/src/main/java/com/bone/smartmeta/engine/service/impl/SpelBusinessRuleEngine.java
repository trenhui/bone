package com.bone.smartmeta.engine.service.impl;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.model.EntityMetadata;
import com.bone.smartmeta.engine.model.FieldMetadata;
import com.bone.smartmeta.engine.model.RuleResult;
import com.bone.smartmeta.engine.service.BusinessRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Spring表达式语言的业务规则引擎实现
 * 支持复杂条件表达式、计算字段和业务规则验证
 */
@Service
public class SpelBusinessRuleEngine implements BusinessRuleEngine {

    private static final Logger logger = LoggerFactory.getLogger(SpelBusinessRuleEngine.class);
    
    private final MetadataEngine metadataEngine;
    private final ExpressionParser expressionParser;
    private final Map<String, Map<String, Expression>> expressionCache;
    
    @Autowired
    public SpelBusinessRuleEngine(MetadataEngine metadataEngine) {
        this.metadataEngine = metadataEngine;
        this.expressionParser = new SpelExpressionParser();
        this.expressionCache = new ConcurrentHashMap<>();
    }
    
    @Override
    public RuleResult executeRules(String entityName, Map<String, Object> entityData) {
        RuleResult result = RuleResult.builder().success(true).build();
        
        try {
            // 获取实体元数据
            EntityMetadata metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
            if (metadata == null) {
                result.addError("MetadataNotFound", "Entity metadata not found: " + entityName);
                return result;
            }
            
            // 构建表达式上下文
            EvaluationContext context = buildEvaluationContext(entityData);
            
            // 执行字段级验证规则
            if (metadata.getFields() != null) {
                for (Map.Entry<String, FieldMetadata> entry : metadata.getFields().entrySet()) {
                    FieldMetadata fieldMetadata = entry.getValue();
                    executeFieldRules(fieldMetadata, entityData, context, result);
                }
            }
            
            // 执行实体级业务规则（这里假设有一个getBusinessRules方法）
            executeEntityRules(metadata, entityData, context, result);
            
        } catch (Exception e) {
            logger.error("Failed to execute rules for entity: {}", entityName, e);
            result.addError("RuleExecutionError", "Error executing rules: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public RuleResult executeRules(String entityName, Map<String, Object> entityData, List<String> ruleNames) {
        RuleResult result = RuleResult.builder().success(true).build();
        
        try {
            // 获取实体元数据
            EntityMetadata metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
            if (metadata == null) {
                result.addError("MetadataNotFound", "Entity metadata not found: " + entityName);
                return result;
            }
            
            // 构建表达式上下文
            EvaluationContext context = buildEvaluationContext(entityData);
            
            // 执行指定的规则
            executeNamedRules(metadata, entityData, ruleNames, context, result);
            
        } catch (Exception e) {
            logger.error("Failed to execute specified rules for entity: {}", entityName, e);
            result.addError("RuleExecutionError", "Error executing rules: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public boolean validate(String entityName, Map<String, Object> entityData) {
        RuleResult result = executeRules(entityName, entityData);
        return result.isSuccess();
    }
    
    @Override
    public Map<String, Object> calculateFields(String entityName, Map<String, Object> entityData) {
        try {
            // 获取实体元数据
            EntityMetadata metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
            if (metadata == null) {
                logger.warn("Entity metadata not found: {}", entityName);
                return entityData;
            }
            
            // 创建数据副本以避免修改原始数据
            Map<String, Object> resultData = new HashMap<>(entityData);
            
            // 构建表达式上下文
            EvaluationContext context = buildEvaluationContext(resultData);
            
            // 计算所有计算字段
            if (metadata.getFields() != null) {
                // 按依赖关系排序计算字段
                List<FieldMetadata> calculatedFields = getCalculatedFieldsSortedByDependency(metadata);
                
                for (FieldMetadata fieldMetadata : calculatedFields) {
                    if (fieldMetadata.isCalculated() && fieldMetadata.getCalculationExpression() != null) {
                        Object value = evaluateExpression(fieldMetadata.getCalculationExpression(), context);
                        String fieldKey = fieldMetadata.getApiName() != null ? 
                                fieldMetadata.getApiName() : fieldMetadata.getName();
                        resultData.put(fieldKey, value);
                        // 更新上下文以支持依赖字段的计算
                        context.setVariable(fieldKey, value);
                    }
                }
            }
            
            return resultData;
        } catch (Exception e) {
            logger.error("Failed to calculate fields for entity: {}", entityName, e);
            throw new RuntimeException("Field calculation failed", e);
        }
    }
    
    @Override
    public Object calculateField(String entityName, Map<String, Object> entityData, String fieldName) {
        try {
            // 获取实体元数据
            EntityMetadata metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
            if (metadata == null) {
                logger.warn("Entity metadata not found: {}", entityName);
                return null;
            }
            
            // 查找字段元数据
            FieldMetadata fieldMetadata = findFieldMetadata(metadata, fieldName);
            if (fieldMetadata == null || !fieldMetadata.isCalculated() || fieldMetadata.getCalculationExpression() == null) {
                logger.warn("Calculation field not found or not calculated: {}.{}", entityName, fieldName);
                return null;
            }
            
            // 构建表达式上下文
            EvaluationContext context = buildEvaluationContext(entityData);
            
            // 计算字段值
            return evaluateExpression(fieldMetadata.getCalculationExpression(), context);
        } catch (Exception e) {
            logger.error("Failed to calculate field: {}.{}", entityName, fieldName, e);
            throw new RuntimeException("Field calculation failed", e);
        }
    }
    
    @Override
    public List<Map<String, Object>> getRules(String entityName) {
        List<Map<String, Object>> rules = new ArrayList<>();
        
        try {
            // 获取实体元数据
            EntityMetadata metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
            if (metadata == null) {
                return rules;
            }
            
            // 提取字段级规则
            if (metadata.getFields() != null) {
                for (Map.Entry<String, FieldMetadata> entry : metadata.getFields().entrySet()) {
                    FieldMetadata fieldMetadata = entry.getValue();
                    // 这里可以提取字段级的验证规则
                }
            }
            
            // 提取实体级规则
            // 假设metadata有getBusinessRules方法
        } catch (Exception e) {
            logger.error("Failed to get rules for entity: {}", entityName, e);
        }
        
        return rules;
    }
    
    @Override
    public void registerRule(String ruleName, String entityName, Map<String, Object> ruleDefinition) {
        // 实现动态注册规则的逻辑
        logger.info("Rule registered: {} for entity: {}", ruleName, entityName);
    }
    
    @Override
    public boolean evaluateCondition(String entityName, Map<String, Object> entityData, String conditionExpression) {
        try {
            EvaluationContext context = buildEvaluationContext(entityData);
            return evaluateExpression(conditionExpression, context, Boolean.class);
        } catch (Exception e) {
            logger.error("Failed to evaluate condition for entity: {}", entityName, e);
            return false;
        }
    }
    
    // 私有辅助方法
    
    private void executeFieldRules(FieldMetadata fieldMetadata, Map<String, Object> entityData,
                                 EvaluationContext context, RuleResult result) {
        // 执行字段级验证规则
        // 1. 必填验证
        if (fieldMetadata.isRequired()) {
            String fieldKey = fieldMetadata.getApiName() != null ? 
                    fieldMetadata.getApiName() : fieldMetadata.getName();
            Object value = entityData.get(fieldKey);
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                result.addError("RequiredField", "Field '" + fieldKey + "' is required");
            }
        }
        
        // 2. 长度验证
        validateFieldLength(fieldMetadata, entityData, result);
        
        // 3. 数值范围验证
        validateFieldRange(fieldMetadata, entityData, result);
        
        // 4. 正则表达式验证
        validateFieldPattern(fieldMetadata, entityData, result);
        
        // 5. 自定义验证表达式
        if (fieldMetadata.getValidationExpression() != null) {
            try {
                boolean isValid = evaluateExpression(fieldMetadata.getValidationExpression(), context, Boolean.class);
                if (!isValid) {
                    String errorMessage = fieldMetadata.getErrorMessage() != null ? 
                            fieldMetadata.getErrorMessage() : "Field validation failed";
                    result.addError("FieldValidation", errorMessage);
                }
            } catch (Exception e) {
                logger.warn("Failed to evaluate validation expression for field: {}", fieldMetadata.getName(), e);
                result.addError("ValidationError", "Invalid validation expression for field: " + fieldMetadata.getName());
            }
        }
    }
    
    private void executeEntityRules(EntityMetadata metadata, Map<String, Object> entityData,
                                  EvaluationContext context, RuleResult result) {
        // 实现实体级业务规则执行逻辑
        // 这里可以从metadata中获取业务规则并执行
    }
    
    private void executeNamedRules(EntityMetadata metadata, Map<String, Object> entityData,
                                 List<String> ruleNames, EvaluationContext context, RuleResult result) {
        // 实现指定规则的执行逻辑
    }
    
    private List<FieldMetadata> getCalculatedFieldsSortedByDependency(EntityMetadata metadata) {
        List<FieldMetadata> calculatedFields = new ArrayList<>();
        
        if (metadata.getFields() != null) {
            for (FieldMetadata field : metadata.getFields().values()) {
                if (field.isCalculated() && field.getCalculationExpression() != null) {
                    calculatedFields.add(field);
                }
            }
            
            // 简单排序，实际项目中可能需要更复杂的拓扑排序
            // 这里可以基于字段依赖关系进行排序
        }
        
        return calculatedFields;
    }
    
    private FieldMetadata findFieldMetadata(EntityMetadata metadata, String fieldName) {
        if (metadata.getFields() == null) {
            return null;
        }
        
        // 先尝试通过名称查找
        FieldMetadata field = metadata.getFields().get(fieldName);
        if (field != null) {
            return field;
        }
        
        // 再尝试通过apiName查找
        for (FieldMetadata f : metadata.getFields().values()) {
            if (fieldName.equals(f.getApiName())) {
                return f;
            }
        }
        
        return null;
    }
    
    private EvaluationContext buildEvaluationContext(Map<String, Object> data) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 添加数据到上下文
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        // 添加常用工具类
        context.registerFunction("isEmpty", getClass().getDeclaredMethod("isEmpty", Object.class));
        context.registerFunction("isNotEmpty", getClass().getDeclaredMethod("isNotEmpty", Object.class));
        context.registerFunction("contains", getClass().getDeclaredMethod("contains", Collection.class, Object.class));
        
        return context;
    }
    
    private <T> T evaluateExpression(String expressionString, EvaluationContext context, Class<T> expectedType) {
        Expression expression = getOrParseExpression(expressionString);
        return expression.getValue(context, expectedType);
    }
    
    private Object evaluateExpression(String expressionString, EvaluationContext context) {
        Expression expression = getOrParseExpression(expressionString);
        return expression.getValue(context);
    }
    
    private Expression getOrParseExpression(String expressionString) {
        String cacheKey = expressionString;
        Map<String, Expression> expressions = expressionCache.computeIfAbsent("global", k -> new ConcurrentHashMap<>());
        
        return expressions.computeIfAbsent(cacheKey, k -> expressionParser.parseExpression(k));
    }
    
    private void validateFieldLength(FieldMetadata fieldMetadata, Map<String, Object> entityData, RuleResult result) {
        // 实现长度验证逻辑
    }
    
    private void validateFieldRange(FieldMetadata fieldMetadata, Map<String, Object> entityData, RuleResult result) {
        // 实现数值范围验证逻辑
    }
    
    private void validateFieldPattern(FieldMetadata fieldMetadata, Map<String, Object> entityData, RuleResult result) {
        // 实现正则表达式验证逻辑
    }
    
    // 公共工具方法，用于表达式中调用
    
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return ((String) obj).trim().isEmpty();
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        return false;
    }
    
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
    
    public static boolean contains(Collection<?> collection, Object item) {
        return collection != null && collection.contains(item);
    }
}