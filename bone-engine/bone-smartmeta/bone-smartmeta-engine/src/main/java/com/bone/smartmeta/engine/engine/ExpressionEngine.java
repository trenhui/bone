package com.bone.smartmeta.engine.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 表达式引擎
 * 负责计算字段、评估表达式和执行布尔表达式计算
 */
public class ExpressionEngine {

    private static final Logger logger = Logger.getLogger(ExpressionEngine.class.getName());
    
    // 用于缓存表达式计算结果的缓存机制
    private final Map<String, CachedExpressionResult> expressionCache;
    
    // 缓存大小限制
    private static final int CACHE_SIZE_LIMIT = 1000;
    
    // 简单变量引用的正则表达式
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^\\}]*)\\}");
    
    /**
     * 构造函数
     */
    public ExpressionEngine() {
        // 使用ConcurrentHashMap确保线程安全
        this.expressionCache = new ConcurrentHashMap<>();
        logger.info("ExpressionEngine initialized with caching capability");
    }

    /**
     * 计算实体的计算字段
     * @param entityName 实体名称
     * @param data 实体数据
     * @return 计算后的字段值映射
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws ExpressionEvaluationException 当表达式计算失败时抛出
     */
    public Map<String, Object> calculateFields(String entityName, Map<String, Object> data) {
        // 参数验证
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(data, "Data cannot be null");
        
        logger.log(Level.FINE, "Calculating fields for entity: {0}", entityName);
        
        try {
            // 创建结果映射
            Map<String, Object> result = new HashMap<>();
            
            // 在实际应用中，这里应该从元数据中获取计算字段的定义
            // 然后遍历计算字段，执行相应的表达式计算
            
            // 简单实现：查找并计算以"calc_"开头的字段
            data.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("calc_") && entry.getValue() instanceof String)
                .forEach(entry -> {
                    String fieldName = entry.getKey().substring(5); // 移除"calc_"前缀
                    String expression = (String) entry.getValue();
                    try {
                        Object calculatedValue = evaluateExpression(expression, data);
                        result.put(fieldName, calculatedValue);
                        logger.log(Level.FINE, "Calculated field {0} = {1} for expression: {2}", 
                                  new Object[]{fieldName, calculatedValue, expression});
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error calculating field {0} with expression {1}: {2}", 
                                  new Object[]{fieldName, expression, e.getMessage()});
                        // 计算失败时不中断，继续计算其他字段
                    }
                });
            
            logger.log(Level.FINE, "Successfully calculated {0} fields for entity: {1}", 
                      new Object[]{result.size(), entityName});
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calculating fields for entity " + entityName + ": " + e.getMessage(), e);
            throw new ExpressionEvaluationException("Failed to calculate fields for entity: " + entityName, e);
        }
    }

    /**
     * 计算单个表达式
     * @param expression 表达式字符串
     * @param variables 变量映射
     * @return 表达式计算结果
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws ExpressionEvaluationException 当表达式计算失败时抛出
     */
    public Object evaluateExpression(String expression, Map<String, Object> variables) {
        // 参数验证
        Objects.requireNonNull(expression, "Expression cannot be null");
        Objects.requireNonNull(variables, "Variables cannot be null");
        
        // 生成缓存键
        String cacheKey = generateCacheKey(expression, variables);
        
        // 尝试从缓存获取结果
        CachedExpressionResult cachedResult = expressionCache.get(cacheKey);
        if (cachedResult != null && !cachedResult.isExpired()) {
            logger.log(Level.FINE, "Cache hit for expression: {0}", expression);
            return cachedResult.getResult();
        }
        
        try {
            logger.log(Level.FINE, "Evaluating expression: {0}", expression);
            
            // 简单的表达式求值实现
            Object result = evaluateSimpleExpression(expression, variables);
            
            // 缓存结果
            cacheExpressionResult(cacheKey, result);
            
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error evaluating expression " + expression + ": " + e.getMessage(), e);
            throw new ExpressionEvaluationException("Failed to evaluate expression: " + expression, e);
        }
    }

    /**
     * 计算布尔表达式
     * @param expression 布尔表达式字符串
     * @param variables 变量映射
     * @return 布尔表达式计算结果
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws ExpressionEvaluationException 当表达式计算失败时抛出
     * @throws ClassCastException 当表达式结果不是布尔类型时抛出
     */
    public boolean evaluateBooleanExpression(String expression, Map<String, Object> variables) {
        // 参数验证
        Objects.requireNonNull(expression, "Expression cannot be null");
        Objects.requireNonNull(variables, "Variables cannot be null");
        
        logger.log(Level.FINE, "Evaluating boolean expression: {0}", expression);
        
        try {
            Object result = evaluateExpression(expression, variables);
            
            // 确保结果是布尔类型
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result instanceof String) {
                // 处理字符串形式的布尔值
                return Boolean.parseBoolean((String) result);
            } else {
                throw new ClassCastException("Expression result is not a boolean: " + result);
            }
        } catch (ExpressionEvaluationException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error evaluating boolean expression " + expression + ": " + e.getMessage(), e);
            throw new ExpressionEvaluationException("Failed to evaluate boolean expression: " + expression, e);
        }
    }
    
    /**
     * 简单表达式求值实现
     * 支持变量替换和基本的数学运算
     */
    private Object evaluateSimpleExpression(String expression, Map<String, Object> variables) {
        // 处理变量替换: ${variableName}
        String processedExpression = replaceVariables(expression, variables);
        
        // 处理简单的数学运算
        if (processedExpression.matches("^[\\d\\+\\-\\*/\\(\\)\\s.]+$")) {
            return evaluateMathExpression(processedExpression);
        }
        
        // 处理字符串字面量
        if (processedExpression.startsWith("'")) {
            return processedExpression;
        }
        
        // 处理数字字面量
        try {
            if (processedExpression.contains(".")) {
                return Double.parseDouble(processedExpression);
            } else {
                return Long.parseLong(processedExpression);
            }
        } catch (NumberFormatException e) {
            // 不是数字，继续处理
        }
        
        // 处理布尔字面量
        if ("true".equalsIgnoreCase(processedExpression)) {
            return Boolean.TRUE;
        } else if ("false".equalsIgnoreCase(processedExpression)) {
            return Boolean.FALSE;
        }
        
        // 默认返回原始表达式
        return processedExpression;
    }
    
    /**
     * 替换表达式中的变量
     */
    private String replaceVariables(String expression, Map<String, Object> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.getOrDefault(variableName, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 简单的数学表达式求值
     * 注意：这是一个非常简化的实现，仅用于演示
     */
    private Object evaluateMathExpression(String expression) {
        try {
            // 移除所有空格
            expression = expression.replaceAll("\\s+", "");
            
            // 非常简单的实现，仅支持加减法
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+", 2);
                double left = Double.parseDouble(parts[0]);
                double right = Double.parseDouble(parts[1]);
                return left + right;
            } else if (expression.contains("-")) {
                String[] parts = expression.split("-", 2);
                double left = Double.parseDouble(parts[0]);
                double right = Double.parseDouble(parts[1]);
                return left - right;
            }
            
            // 直接返回数值
            if (expression.contains(".")) {
                return Double.parseDouble(expression);
            } else {
                return Long.parseLong(expression);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not evaluate math expression: {0}", expression);
            return expression;
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String expression, Map<String, Object> variables) {
        // 排序变量键以确保一致的缓存键
        String variablesKey = variables.keySet().stream()
                .sorted()
                .map(key -> key + ":" + variables.get(key))
                .collect(Collectors.joining(","));
        
        return expression + "|" + variablesKey;
    }
    
    /**
     * 缓存表达式计算结果
     */
    private void cacheExpressionResult(String key, Object result) {
        // 检查缓存大小，防止内存泄漏
        if (expressionCache.size() >= CACHE_SIZE_LIMIT) {
            // 简单的缓存淘汰策略：移除第一个条目
            String firstKey = expressionCache.keySet().iterator().next();
            expressionCache.remove(firstKey);
            logger.log(Level.FINE, "Expression cache size limit reached, removed oldest entry");
        }
        
        expressionCache.put(key, new CachedExpressionResult(result));
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        expressionCache.clear();
        logger.info("Expression cache cleared");
    }
    
    /**
     * 获取当前缓存大小
     */
    public int getCacheSize() {
        return expressionCache.size();
    }
    
    /**
     * 缓存的表达式结果
     */
    private static class CachedExpressionResult {
        private final Object result;
        private final long timestamp;
        private static final long EXPIRATION_MS = 5 * 60 * 1000; // 5分钟过期
        
        public CachedExpressionResult(Object result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        public Object getResult() {
            return result;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > EXPIRATION_MS;
        }
    }
    
    /**
     * 表达式计算异常
     */
    public static class ExpressionEvaluationException extends RuntimeException {
        public ExpressionEvaluationException(String message) {
            super(message);
        }

        public ExpressionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}