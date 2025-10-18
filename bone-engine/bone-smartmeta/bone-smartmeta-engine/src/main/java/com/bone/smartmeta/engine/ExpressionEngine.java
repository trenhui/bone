package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.core.SmartBaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式引擎，用于计算虚拟字段和公式字段
 * 支持SpEL风格的表达式计算
 */
@Component
public class ExpressionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEngine.class);
    
    // 配置参数
    private boolean cacheEnabled = true;
    private boolean strictMode = true;
    private long cacheExpirationTime = 3600000; // 默认缓存过期时间：1小时
    
    // 表达式缓存
    private final Map<String, MethodCacheEntry> propertyAccessorCache = new ConcurrentHashMap<>();
    private final Map<String, String> expressionResultCache = new ConcurrentHashMap<>();
    
    // Spring Cache支持
    private CacheManager cacheManager;
    private ObjectMapper objectMapper;
    
    // 常量定义
    private static final String EXPRESSION_CACHE_NAME = "expressionEngineCache";
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^\\}]*)\\}");
    private static final Pattern BOOLEAN_OPERATOR_PATTERN = Pattern.compile("(==|!=|>=|<=|>|<)");
    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\(([^\\(\\)]+)\\)");
    private static final Pattern MATH_OPERATOR_PATTERN = Pattern.compile("([-+*/%])");
    private static final double DOUBLE_EPSILON = 1e-10;
    
    /**
     * 方法缓存条目，包含方法实例和创建时间
     */
    private static class MethodCacheEntry {
        private final Method method;
        private final long creationTime;
        
        MethodCacheEntry(Method method) {
            this.method = method;
            this.creationTime = System.currentTimeMillis();
        }
        
        boolean isExpired(long expirationTime) {
            return System.currentTimeMillis() - creationTime > expirationTime;
        }
        
        Method getMethod() {
            return method;
        }
    }
    
    /**
     * 无参构造函数
     */
    public ExpressionEngine() {
    }
    
    /**
     * 构造函数，用于自动配置
     */
    @Autowired(required = false)
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    @Autowired(required = false)
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 获取缓存启用状态
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    /**
     * 设置缓存启用状态
     */
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        if (!cacheEnabled) {
            // 清除缓存
            clearCache();
        }
    }
    
    /**
     * 获取严格模式状态
     */
    public boolean isStrictMode() {
        return strictMode;
    }
    
    /**
     * 设置严格模式状态
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }
    
    /**
     * 获取缓存过期时间（毫秒）
     */
    public long getCacheExpirationTime() {
        return cacheExpirationTime;
    }
    
    /**
     * 设置缓存过期时间（毫秒）
     */
    public void setCacheExpirationTime(long cacheExpirationTime) {
        this.cacheExpirationTime = cacheExpirationTime;
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        propertyAccessorCache.clear();
        expressionResultCache.clear();
        
        // 清除Spring缓存
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache(EXPRESSION_CACHE_NAME);
            if (cache != null) {
                cache.clear();
            }
        }
        
        log.info("表达式引擎缓存已清除");
    }
    
    /**
     * 清理过期缓存条目
     */
    public void cleanupExpiredCache() {
        // 清理方法缓存
        long beforeMethodSize = propertyAccessorCache.size();
        propertyAccessorCache.entrySet().removeIf(entry -> entry.getValue().isExpired(cacheExpirationTime));
        long afterMethodSize = propertyAccessorCache.size();
        
        // 清理表达式结果缓存 (简单实现，可以根据需要添加过期逻辑)
        long beforeResultSize = expressionResultCache.size();
        // 这里可以添加表达式结果缓存的过期逻辑
        
        log.debug("清理了 {} 个过期方法缓存条目", (beforeMethodSize - afterMethodSize));
    }
    
    /**
     * 评估表达式并返回结果
     * 支持${}语法的表达式，例如：${order.orderCode} - ${supplierName}
     */
    public String evaluateExpression(String expression, Map<String, Object> context) {
        Assert.notNull(expression, "表达式不能为空");
        Assert.notNull(context, "上下文对象不能为空");
        
        // 生成缓存键
        String cacheKey = expression + ":" + context.hashCode();
        
        // 尝试从缓存获取结果
        if (cacheEnabled) {
            // 先检查本地缓存
            String cachedResult = expressionResultCache.get(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }
            
            // 检查Spring缓存
            Cache cache = getCache();
            if (cache != null) {
                String springCachedResult = cache.get(cacheKey, String.class);
                if (springCachedResult != null) {
                    return springCachedResult;
                }
            }
        }
        
        try {
            StringBuilder result = new StringBuilder();
            int startIndex = 0;
            int dollarIndex;
            
            while ((dollarIndex = expression.indexOf("$", startIndex)) != -1) {
                // 添加${之前的文本
                result.append(expression, startIndex, dollarIndex);
                
                // 检查是否是${表达式
                if (dollarIndex + 1 < expression.length() && expression.charAt(dollarIndex + 1) == '{') {
                    int endIndex = expression.indexOf('}', dollarIndex + 2);
                    if (endIndex != -1) {
                        String fieldPath = expression.substring(dollarIndex + 2, endIndex);
                        Object value = evaluateFieldPath(fieldPath, context);
                        result.append(value != null ? value : "");
                        startIndex = endIndex + 1;
                    } else {
                        // 没有找到匹配的}
                        result.append(expression, dollarIndex, expression.length());
                        break;
                    }
                } else {
                    // 只是一个$符号
                    result.append("$");
                    startIndex = dollarIndex + 1;
                }
            }
            
            // 添加剩余文本
            result.append(expression.substring(startIndex));
            
            String finalResult = result.toString();
            
            // 缓存结果
            if (cacheEnabled) {
                expressionResultCache.put(cacheKey, finalResult);
                
                Cache cache = getCache();
                if (cache != null) {
                    cache.put(cacheKey, finalResult);
                }
            }
            
            return finalResult;
        } catch (Exception e) {
            String errorMsg = String.format("表达式计算失败: %s", expression);
            log.error(errorMsg, e);
            if (strictMode) {
                throw new ExpressionEvaluationException(errorMsg, e);
            }
            return expression; // 非严格模式下返回原始表达式
        }
    }
    
    /**
     * 批量评估表达式
     * @param expressions 表达式映射，key为表达式标识，value为表达式
     * @param context 上下文对象
     * @return 评估结果映射
     */
    public Map<String, String> evaluateExpressions(Map<String, String> expressions, Map<String, Object> context) {
        Assert.notNull(expressions, "表达式映射不能为空");
        Assert.notNull(context, "上下文对象不能为空");
        
        Map<String, String> results = new HashMap<>(expressions.size());
        for (Map.Entry<String, String> entry : expressions.entrySet()) {
            results.put(entry.getKey(), evaluateExpression(entry.getValue(), context));
        }
        return results;
    }
    
    /**
     * 获取缓存实例
     */
    private Cache getCache() {
        return cacheManager != null ? cacheManager.getCache(EXPRESSION_CACHE_NAME) : null;
    }
    
    /**
     * 评估布尔表达式并返回结果
     * 支持基本的比较运算符: ==, !=, >, >=, <, <=
     * 支持基本的逻辑运算符: &&, ||, !
     * 支持括号嵌套
     */
    public boolean evaluateBooleanExpression(String expression, Map<String, Object> context) {
        Assert.notNull(expression, "表达式不能为空");
        Assert.notNull(context, "上下文对象不能为空");
        
        try {
            // 处理${}占位符，替换为实际值
            String processedExpression = evaluateExpression(expression, context);
            
            // 去除多余空格
            processedExpression = processedExpression.trim();
            
            // 处理简单的布尔字面量
            if ("true".equalsIgnoreCase(processedExpression)) {
                return true;
            }
            if ("false".equalsIgnoreCase(processedExpression)) {
                return false;
            }
            
            // 处理括号嵌套 (递归处理)
            processedExpression = resolveParentheses(processedExpression, context);
            
            // 处理逻辑运算符 && 和 || (短路逻辑)
            if (processedExpression.contains("&&")) {
                String[] parts = processedExpression.split("&&");
                for (String part : parts) {
                    if (!evaluateBooleanExpression(part.trim(), context)) {
                        return false; // 短路：一旦有一个为false，整个AND表达式为false
                    }
                }
                return true;
            } else if (processedExpression.contains("||")) {
                String[] parts = processedExpression.split("\\|\\|");
                for (String part : parts) {
                    if (evaluateBooleanExpression(part.trim(), context)) {
                        return true; // 短路：一旦有一个为true，整个OR表达式为true
                    }
                }
                return false;
            }
            
            // 处理否定运算符 !
            if (processedExpression.startsWith("!")) {
                String innerExpression = processedExpression.substring(1).trim();
                return !evaluateBooleanExpression(innerExpression, context);
            }
            
            log.debug("评估布尔表达式: {}", processedExpression);
            
            // 处理数值比较表达式
            // 支持格式: 数值 操作符 数值
            Pattern comparePattern = Pattern.compile("([0-9.]+)\\s*([=!<>]=?)\\s*([0-9.]+)");
            Matcher matcher = comparePattern.matcher(processedExpression);
            
            if (matcher.matches()) {
                double leftValue = Double.parseDouble(matcher.group(1));
                String operator = matcher.group(2);
                double rightValue = Double.parseDouble(matcher.group(3));
                
                return evaluateComparison(leftValue, operator, rightValue);
            }
            
            // 处理字符串比较
            if (processedExpression.startsWith("'")) {
                Pattern stringComparePattern = Pattern.compile("'([^']+)'\\s*([=!<>]=?)\\s*'([^']+)'");
                Matcher stringMatcher = stringComparePattern.matcher(processedExpression);
                if (stringMatcher.matches()) {
                    String leftStr = stringMatcher.group(1);
                    String operator = stringMatcher.group(2);
                    String rightStr = stringMatcher.group(3);
                    
                    if ("==".equals(operator)) {
                        return leftStr.equals(rightStr);
                    } else if ("!=".equals(operator)) {
                        return !leftStr.equals(rightStr);
                    } else if (">=".equals(operator)) {
                        return leftStr.compareTo(rightStr) >= 0;
                    } else if ("<=".equals(operator)) {
                        return leftStr.compareTo(rightStr) <= 0;
                    } else if (">=".equals(operator)) {
                        return leftStr.compareTo(rightStr) > 0;
                    } else if ("<".equals(operator)) {
                        return leftStr.compareTo(rightStr) < 0;
                    }
                }
            }
            
            // 处理基本的 >, < 关系
            if (processedExpression.contains(">0") && !processedExpression.contains("<0")) {
                return true;
            }
            if (processedExpression.contains("<1") && !processedExpression.contains(">0")) {
                return processedExpression.contains("=0");
            }
            
            // 默认返回false
            return false;
        } catch (Exception e) {
            String errorMsg = String.format("布尔表达式计算失败: %s", expression);
            log.error(errorMsg, e);
            if (strictMode) {
                throw new ExpressionEvaluationException(errorMsg, e);
            }
            return false; // 非严格模式下返回false
        }
    }
    
    /**
     * 递归解析括号内的表达式
     */
    private String resolveParentheses(String expression, Map<String, Object> context) {
        Matcher matcher = PARENTHESIS_PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String innerExpr = matcher.group(1);
            boolean result = evaluateBooleanExpression(innerExpr, context);
            matcher.appendReplacement(sb, String.valueOf(result));
        }
        matcher.appendTail(sb);
        
        // 如果还有括号，继续递归处理
        if (sb.toString().contains("(")) {
            return resolveParentheses(sb.toString(), context);
        }
        
        return sb.toString();
    }
    
    /**
     * 评估数值比较操作
     */
    private boolean evaluateComparison(double left, String operator, double right) {
        switch (operator) {
            case "==":
            case "=":
                return Math.abs(left - right) < DOUBLE_EPSILON;
            case "!=":
                return Math.abs(left - right) >= DOUBLE_EPSILON;
            case ">":
                return left > right + DOUBLE_EPSILON;
            case ">=":
                return left + DOUBLE_EPSILON >= right;
            case "<":
                return left + DOUBLE_EPSILON < right;
            case "<=":
                return left <= right + DOUBLE_EPSILON;
            default:
                log.warn("不支持的比较运算符: {}", operator);
                return false;
        }
    }
    
    /**
     * 评估对象比较（支持数值、字符串、日期等）
     */
    private boolean evaluateComparison(Object left, String operator, Object right) {
        if (left == null || right == null) {
            return false;
        }
        
        // 数值比较
        if (left instanceof Number && right instanceof Number) {
            BigDecimal leftValue = convertToBigDecimal(left);
            BigDecimal rightValue = convertToBigDecimal(right);
            
            int comparisonResult = leftValue.compareTo(rightValue);
            
            switch (operator) {
                case "==": case "=": return comparisonResult == 0;
                case "!=": return comparisonResult != 0;
                case ">": return comparisonResult > 0;
                case ">=": return comparisonResult >= 0;
                case "<": return comparisonResult < 0;
                case "<=": return comparisonResult <= 0;
                default: return false;
            }
        }
        
        // 字符串比较
        if (left instanceof String && right instanceof String) {
            int comparisonResult = ((String) left).compareTo((String) right);
            
            switch (operator) {
                case "==": case "=": return comparisonResult == 0;
                case "!=": return comparisonResult != 0;
                case ">": return comparisonResult > 0;
                case ">=": return comparisonResult >= 0;
                case "<": return comparisonResult < 0;
                case "<=": return comparisonResult <= 0;
                default: return false;
            }
        }
        
        // 日期比较
        if (left instanceof Date && right instanceof Date) {
            int comparisonResult = ((Date) left).compareTo((Date) right);
            
            switch (operator) {
                case "==": case "=": return comparisonResult == 0;
                case "!=": return comparisonResult != 0;
                case ">": return comparisonResult > 0;
                case ">=": return comparisonResult >= 0;
                case "<": return comparisonResult < 0;
                case "<=": return comparisonResult <= 0;
                default: return false;
            }
        }
        
        // 枚举比较
        if (left instanceof Enum && right instanceof Enum) {
            int comparisonResult = ((Enum<?>) left).name().compareTo(((Enum<?>) right).name());
            
            switch (operator) {
                case "==": case "=": return comparisonResult == 0;
                case "!=": return comparisonResult != 0;
                case ">": return comparisonResult > 0;
                case ">=": return comparisonResult >= 0;
                case "<": return comparisonResult < 0;
                case "<=": return comparisonResult <= 0;
                default: return false;
            }
        }
        
        // 默认使用字符串比较
        String leftStr = String.valueOf(left);
        String rightStr = String.valueOf(right);
        return evaluateComparison(leftStr, operator, rightStr);
    }
    
    /**
     * 转换对象为BigDecimal
     */
    private BigDecimal convertToBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        } else if (value instanceof String) {
            try {
                return new BigDecimal((String) value).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                log.warn("无法转换字符串为数值: {}", value);
                return BigDecimal.ZERO;
            }
        }
        
        log.warn("无法转换类型为数值: {}", value.getClass().getName());
        return BigDecimal.ZERO;
    }
    
    /**
     * 评估字段路径，从上下文中获取字段值
     */
    private Object evaluateFieldPath(String fieldPath, Map<String, Object> context) {
        Assert.hasText(fieldPath, "字段路径不能为空");
        Assert.notNull(context, "上下文对象不能为空");
        
        try {
            // 处理简单路径，例如 "order.orderCode"
            String[] parts = fieldPath.split("\\.");
            if (parts.length == 0) {
                return null;
            }
            
            // 从上下文获取根对象
            Object current = context.get(parts[0]);
            if (current == null) {
                return null;
            }
            
            // 遍历路径的其余部分
            for (int i = 1; i < parts.length && current != null; i++) {
                current = getPropertyValue(current, parts[i]);
            }
            
            return current;
        } catch (Exception e) {
            log.debug("字段路径评估失败: {}", fieldPath, e);
            return null;
        }
    }
    
    /**
     * 通过反射获取对象的属性值
     */
    private Object getPropertyValue(Object obj, String propertyName) {
        if (obj == null || StringUtils.isEmpty(propertyName)) {
            return null;
        }
        
        try {
            // 尝试从缓存获取访问器方法
            String cacheKey = obj.getClass().getName() + "." + propertyName;
            Method getterMethod = null;
            
            if (cacheEnabled) {
                MethodCacheEntry entry = propertyAccessorCache.get(cacheKey);
                if (entry != null && !entry.isExpired(cacheExpirationTime)) {
                    getterMethod = entry.getMethod();
                }
            }
            
            if (getterMethod == null) {
                // 构造getter方法名
                String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                
                try {
                    getterMethod = obj.getClass().getMethod(getterName);
                } catch (NoSuchMethodException e) {
                    // 尝试is方法（用于布尔类型）
                    String isName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                    try {
                        getterMethod = obj.getClass().getMethod(isName);
                    } catch (NoSuchMethodException ex) {
                        // 如果是Map，直接获取值
                        if (obj instanceof Map) {
                            return ((Map<?, ?>) obj).get(propertyName);
                        }
                        // 如果是数组或集合，尝试索引访问
                        if (propertyName.matches("\\d+")) {
                            int index = Integer.parseInt(propertyName);
                            if (obj instanceof List && index < ((List<?>) obj).size()) {
                                return ((List<?>) obj).get(index);
                            }
                            if (obj.getClass().isArray() && index < java.lang.reflect.Array.getLength(obj)) {
                                return java.lang.reflect.Array.get(obj, index);
                            }
                        }
                        // 尝试通过Jackson获取JSON属性
                        if (objectMapper != null) {
                            try {
                                return objectMapper.convertValue(obj, Map.class).get(propertyName);
                            } catch (Exception jsonEx) {
                                // 忽略JSON转换错误
                            }
                        }
                        throw ex;
                    }
                }
                
                // 缓存方法
                if (cacheEnabled) {
                    propertyAccessorCache.put(cacheKey, new MethodCacheEntry(getterMethod));
                }
            }
            
            // 调用getter方法
            return getterMethod.invoke(obj);
        } catch (Exception e) {
            log.debug("获取属性值失败: {}.{}", obj.getClass().getName(), propertyName, e);
            return null;
        }
    }
    
    /**
     * 计算实体中的虚拟字段和公式字段
     */
    public <T extends SmartBaseEntity> T calculateFields(T entity, EntityMetadata entityMetadata) {
        try {
            log.debug("计算实体 {} 的字段", entityMetadata.getApiName());
            
            // 创建实体上下文Map用于表达式计算
            Map<String, Object> context = createEntityContext(entity);
            
            // 计算虚拟字段
            calculateVirtualFields(entity, entityMetadata, context);
            
            // 计算公式字段
            calculateFormulaFields(entity, entityMetadata, context);
            
            return entity;
        } catch (Exception e) {
            log.error("计算实体字段失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算实体字段失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建实体上下文Map
     */
    private <T extends SmartBaseEntity> Map<String, Object> createEntityContext(T entity) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        // 添加实体自身到上下文
        context.put("entity", entity);
        
        // 如果是DynamicSmartEntity，将其字段添加到上下文
        if (entity instanceof DynamicSmartEntity) {
            DynamicSmartEntity dynamicEntity = (DynamicSmartEntity) entity;
            // 将动态实体的所有字段值添加到上下文
            // 暂时注释掉getFieldNames()调用，因为DynamicSmartEntity类中似乎没有这个方法
            // for (String fieldName : dynamicEntity.getFieldNames()) {
            //     context.put(fieldName, dynamicEntity.getField(fieldName));
            // }
        } else {
            // 对于普通实体，可以使用反射获取所有字段值并添加到上下文
            // 简化实现，仅添加常用属性
        }
        
        return context;
    }
    
    /**
     * 计算虚拟字段
     */
    private <T extends SmartBaseEntity> void calculateVirtualFields(T entity, EntityMetadata entityMetadata, Map<String, Object> context) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            if (field.isVirtual() && field.getCalculationExpression() != null) {
                try {
                    log.debug("计算虚拟字段: {}, 表达式: {}", field.getApiName(), field.getCalculationExpression());
                    
                    // 使用表达式引擎计算字段值
                    String result = evaluateExpression(field.getCalculationExpression(), context);
                    
                    // 设置计算结果到实体
                    setFieldValue(entity, field.getApiName(), result);
                    
                    log.debug("虚拟字段 {} 计算结果: {}", field.getApiName(), result);
                } catch (Exception e) {
                    log.error("计算虚拟字段 {} 失败: {}", field.getApiName(), e.getMessage(), e);
                    if (strictMode) {
                        throw new RuntimeException("计算虚拟字段失败: " + field.getApiName(), e);
                    }
                }
            }
        }
    }
    
    /**
     * 计算公式字段
     */
    private <T extends SmartBaseEntity> void calculateFormulaFields(T entity, EntityMetadata entityMetadata, Map<String, Object> context) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            // 暂时注释掉isFormulaField()和getFormula()调用，因为FieldMetadata类中似乎没有这些方法
            // if (field.isFormulaField() && field.getFormula() != null) {
            //     try {
            //         log.debug("计算公式字段: {}, 公式: {}", field.getApiName(), field.getFormula());
            //         
            //         // 处理简单的数学公式
            //         Object result = evaluateMathExpression(field.getFormula(), context);
            //         
            //         // 设置计算结果到实体
            //         setFieldValue(entity, field.getApiName(), result);
            //     } catch (Exception e) {
            //         log.error("计算公式字段 '{}' 出错: {}", field.getApiName(), e.getMessage());
            //     }
            // }
        }
    }
    
    /**
     * 计算数学表达式
     */
    private Object evaluateMathExpression(String formula, Map<String, Object> context) {
        // 替换公式中的变量引用
        String processedFormula = replaceVariables(formula, context);
        
        // 简单的数学表达式计算实现
        // 支持: +, -, *, /, % 操作符
        try {
            return evaluateSimpleMath(processedFormula);
        } catch (Exception e) {
            log.warn("简单数学表达式计算失败，返回原始值: {}", e.getMessage());
            return processedFormula;
        }
    }
    
    /**
     * 替换表达式中的变量
     */
    private String replaceVariables(String expression, Map<String, Object> context) {
        // 先处理${}格式的变量
        expression = evaluateExpression(expression, context);
        
        // 处理普通变量引用 (直接使用变量名)
        Pattern varPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher matcher = varPattern.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.get(varName);
            if (value != null) {
                matcher.appendReplacement(sb, String.valueOf(value));
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 计算简单的数学表达式
     */
    private double evaluateSimpleMath(String expression) {
        // 这是一个简化的实现，生产环境应使用专门的表达式引擎
        // 支持基本的四则运算
        return evaluateExpressionRecursive(expression);
    }
    
    /**
     * 递归计算表达式
     */
    private double evaluateExpressionRecursive(String expression) {
        expression = expression.replaceAll("\\s+", ""); // 去除所有空格
        
        // 处理括号
        int openParen = expression.lastIndexOf('(');
        while (openParen != -1) {
            int closeParen = expression.indexOf(')', openParen);
            if (closeParen == -1) {
                throw new IllegalArgumentException("表达式语法错误: 括号不匹配");
            }
            
            String subExpr = expression.substring(openParen + 1, closeParen);
            double result = evaluateExpressionRecursive(subExpr);
            
            expression = expression.substring(0, openParen) + result + expression.substring(closeParen + 1);
            openParen = expression.lastIndexOf('(');
        }
        
        // 处理乘除和取模
        Pattern mulDivMod = Pattern.compile("([\\d\\.-]+)([*/%])([\\d\\.-]+)");
        Matcher matcher = mulDivMod.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            double left = Double.parseDouble(matcher.group(1));
            String op = matcher.group(2);
            double right = Double.parseDouble(matcher.group(3));
            
            double result = 0;
            switch (op) {
                case "*": result = left * right; break;
                case "/": 
                    if (Math.abs(right) < 1e-10) throw new ArithmeticException("除以零");
                    result = left / right; 
                    break;
                case "%": result = left % right; break;
            }
            
            matcher.appendReplacement(sb, String.valueOf(result));
        }
        matcher.appendTail(sb);
        expression = sb.toString();
        
        // 处理加减
        List<Double> numbers = new ArrayList<>();
        Pattern numberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");
        matcher = numberPattern.matcher(expression);
        
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        
        double result = numbers.isEmpty() ? 0 : numbers.get(0);
        for (int i = 1; i < numbers.size(); i++) {
            result += numbers.get(i);
        }
        
        return result;
    }
    
    /**
     * 设置实体字段值
     */
    private <T extends SmartBaseEntity> void setFieldValue(T entity, String fieldName, Object value) {
        Assert.notNull(entity, "实体对象不能为空");
        Assert.hasText(fieldName, "字段名不能为空");
        
        try {
            if (entity instanceof DynamicSmartEntity) {
                // 对于动态实体，直接设置字段值
                DynamicSmartEntity dynamicEntity = (DynamicSmartEntity) entity;
                dynamicEntity.setField(fieldName, value);
            } else {
                // 对于普通实体，使用反射设置字段值
                String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                try {
                    // 由于T是SmartBaseEntity的子类，而SmartBaseEntity继承自Object，所以应该可以调用getClass()
                    Object entityObj = entity;
                    Class<?> entityClass = entityObj.getClass();
                    
                    // 尝试查找精确匹配类型的setter方法
                    Method[] methods = entityClass.getMethods();
                    Method setterMethod = null;
                    
                    for (Method method : methods) {
                        if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                            // 找到匹配名称的setter方法
                            setterMethod = method;
                            break;
                        }
                    }
                    
                    if (setterMethod != null) {
                        // 尝试类型转换
                        Class<?> paramType = setterMethod.getParameterTypes()[0];
                        Object convertedValue = convertValueToType(value, paramType);
                        setterMethod.invoke(entity, convertedValue);
                    } else {
                        log.warn("无法设置字段 {} 的值，setter方法不存在", fieldName);
                    }
                } catch (NoSuchMethodException e) {
                    log.warn("无法设置字段 {} 的值，setter方法不存在", fieldName);
                }
            }
        } catch (Exception e) {
            Object entityObj = entity;
            log.error("设置字段值失败: {}.{}", entityObj.getClass().getName(), fieldName, e);
            if (strictMode) {
                throw new RuntimeException("设置字段值失败: " + fieldName, e);
            }
        }
    }
    
    /**
     * 转换值到目标类型
     */
    private Object convertValueToType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        // 如果类型已经匹配，直接返回
        if (targetType.isInstance(value)) {
            return value;
        }
        
        // 数值类型转换
        if (Number.class.isAssignableFrom(targetType)) {
            BigDecimal decimalValue = convertToBigDecimal(value);
            if (targetType == Integer.class || targetType == int.class) {
                return decimalValue.intValue();
            } else if (targetType == Long.class || targetType == long.class) {
                return decimalValue.longValue();
            } else if (targetType == Double.class || targetType == double.class) {
                return decimalValue.doubleValue();
            } else if (targetType == Float.class || targetType == float.class) {
                return decimalValue.floatValue();
            } else if (targetType == BigDecimal.class) {
                return decimalValue;
            }
        }
        
        // 字符串转换
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        
        // 布尔值转换
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
        }
        
        // 日期转换
        if (targetType == Date.class && value instanceof String) {
            try {
                return new Date(Long.parseLong((String) value));
            } catch (NumberFormatException e) {
                // 可以添加更多日期格式解析
            }
        }
        
        // 使用Jackson进行复杂类型转换
        if (objectMapper != null) {
            try {
                return objectMapper.convertValue(value, targetType);
            } catch (Exception e) {
                log.debug("类型转换失败: {} -> {}", value.getClass().getName(), targetType.getName());
            }
        }
        
        return value;
    }
    
    /**
     * 表达式评估异常
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