package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.expression.ExpressionEvaluator;
import com.bone.smartmeta.engine.util.CommonUtils;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.core.SmartBaseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
// MapAccessor导入暂时注释，因为类内部已经实现了MapPropertyAccessor
import java.text.SimpleDateFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import org.springframework.expression.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式引擎，用于计算虚拟字段和公式字段
 * 支持SpEL风格的表达式计算
 */
@Component
public class ExpressionEngine implements ExpressionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionEngine.class);
    
    // 配置参数
    private boolean cacheEnabled = true;
    private boolean strictMode = true;
    private long cacheExpirationTime = 3600000; // 默认缓存过期时间：1小时
    
    // 表达式缓存
    private final Map<String, MethodCacheEntry> propertyAccessorCache = new ConcurrentHashMap<>();
    private final Map<String, String> expressionResultCache = new ConcurrentHashMap<>();
    // SpEL表达式缓存
    private final Map<String, Expression> compiledExpressionCache = new ConcurrentHashMap<>();
    // 表达式解析器
    private final ExpressionParser expressionParser;
    // 模板解析器上下文
    private final TemplateParserContext templateParserContext;
    // 性能统计
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong expressionEvaluations = new AtomicLong(0);
    private final AtomicLong fieldCalculations = new AtomicLong(0);
    private final AtomicLong fieldCalculationFailures = new AtomicLong(0);
    
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
        private final long expirationTime;
        
        MethodCacheEntry(Method method) {
            this.method = method;
            this.creationTime = System.currentTimeMillis();
            this.expirationTime = 0; // 默认不过期
        }
        
        MethodCacheEntry(Method method, long expirationTime) {
            this.method = method;
            this.creationTime = System.currentTimeMillis();
            this.expirationTime = expirationTime;
        }
        
        boolean isExpired() {
            return expirationTime > 0 && System.currentTimeMillis() - creationTime > expirationTime;
        }
        
        boolean isExpired(long globalExpirationTime) {
            long effectiveExpiration = expirationTime > 0 ? expirationTime : globalExpirationTime;
            return System.currentTimeMillis() - creationTime > effectiveExpiration;
        }
        
        Method getMethod() {
            return method;
        }
    }
    
    /**
     * 无参构造函数
     */
    public ExpressionEngine() {
        // 初始化SpEL表达式解析器
        this.expressionParser = new SpelExpressionParser();
        // 初始化模板解析器上下文，使用${}作为表达式分隔符
        this.templateParserContext = new TemplateParserContext("${", "}");
        LOGGER.debug("ExpressionEngine initialized with SpEL and expression caching enabled");
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
     * 清理过期缓存条目
     */
    public void cleanupExpiredCache() {
        // 清理方法缓存
        long beforeMethodSize = propertyAccessorCache.size();
        propertyAccessorCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        long afterMethodSize = propertyAccessorCache.size();
        
        // 清理表达式结果缓存 (简单实现，可以根据需要添加过期逻辑)
        long beforeResultSize = expressionResultCache.size();
        // 这里可以添加表达式结果缓存的过期逻辑
        
        LOGGER.debug("Cleaned up {} expired method cache entries", (beforeMethodSize - afterMethodSize));
    }
    
    /**
     * 评估表达式并返回结果
     * 使用SpEL引擎进行表达式预编译和缓存，支持${}语法
     */
    public String evaluateExpression(String expression, Map<String, Object> context) {
        Assert.notNull(expression, "表达式不能为空");
        Assert.notNull(context, "上下文对象不能为空");
        
        // 生成缓存键
        String cacheKey = expression + ":" + context.hashCode();
        
        // 尝试从结果缓存获取
        if (cacheEnabled) {
            // 先检查本地缓存
            String cachedResult = expressionResultCache.get(cacheKey);
            if (cachedResult != null) {
                cacheHits.incrementAndGet();
                return cachedResult;
            }
            
            // 检查Spring缓存
            Cache cache = getCache();
            if (cache != null) {
                String springCachedResult = cache.get(cacheKey, String.class);
                if (springCachedResult != null) {
                    cacheHits.incrementAndGet();
                    return springCachedResult;
                }
            }
        }
        
        try {
            expressionEvaluations.incrementAndGet();
            
            // 使用SpEL引擎解析和评估表达式
            if (expression.contains("${")) {
                // 对于模板表达式，使用模板解析器
                String result = evaluateTemplateExpression(expression, context);
                
                // 缓存结果
                if (cacheEnabled) {
                    cacheResult(cacheKey, result);
                }
                
                return result;
            } else {
                // 对于简单表达式，使用普通解析器
                EvaluationContext evalContext = createEvaluationContext(context);
                Expression expr = getOrCompileExpression(expression);
                Object result = expr.getValue(evalContext);
                String stringResult = result != null ? result.toString() : "";
                
                // 缓存结果
                if (cacheEnabled) {
                    cacheResult(cacheKey, stringResult);
                }
                
                return stringResult;
            }
        } catch (Exception e) {
            String errorMsg = String.format("表达式计算失败: %s", expression);
            LOGGER.error(errorMsg, e);
            if (strictMode) {
                throw new ExpressionEvaluationException(errorMsg, e);
            }
            // 非严格模式下返回原始表达式，但先尝试使用原始实现作为回退
            try {
                return evaluateExpressionFallback(expression, context);
            } catch (Exception fallbackEx) {
                return expression;
            }
        }
    }
    
    /**
     * 评估模板表达式
     */
    private String evaluateTemplateExpression(String templateExpression, Map<String, Object> context) {
        try {
            // 预编译或从缓存获取表达式
            Expression templateExpr = getOrCompileTemplateExpression(templateExpression);
            
            // 创建评估上下文
            EvaluationContext evalContext = createEvaluationContext(context);
            
            // 评估表达式
            Object result = templateExpr.getValue(evalContext, String.class);
            return result != null ? result.toString() : "";
        } catch (ParseException e) {
            // 当SpEL解析失败时，回退到原始实现
            LOGGER.warn("SpEL template parsing failed, falling back to original implementation: {}", e.getMessage());
            return evaluateExpressionFallback(templateExpression, context);
        }
    }
    
    /**
     * 创建评估上下文
     */
    private EvaluationContext createEvaluationContext(Map<String, Object> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        // 将Map包装成一个可以通过属性名访问的对象
        if (context != null) {
            // 方法1：设置根对象为Map，并添加自定义属性访问器支持
            evalContext.setRootObject(context);
            // 方法2：确保所有键值对都可以作为变量访问（使用#前缀）
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                evalContext.setVariable(entry.getKey(), entry.getValue());
            }
            // 方法3：为SpEL提供Map键的直接访问能力
            evalContext.addPropertyAccessor(new MapPropertyAccessor());
        }
        // 注册常用的函数和变量
        evalContext.setVariable("util", new ExpressionUtils());
        return evalContext;
    }
    
    /**
     * 自定义的Map属性访问器，支持通过属性名直接访问Map中的键
     */
    private static class MapPropertyAccessor implements org.springframework.expression.PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[] { Map.class };
        }
        
        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target instanceof Map && ((Map<?, ?>) target).containsKey(name);
        }
        
        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) {
            if (target instanceof Map) {
                Object value = ((Map<?, ?>) target).get(name);
                return value != null ? new TypedValue(value) : TypedValue.NULL;
            }
            return TypedValue.NULL;
        }
        
        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return target instanceof Map;
        }
        
        @Override
        public void write(EvaluationContext context, Object target, String name, Object newValue) {
            if (target instanceof Map && name != null) {
                ((Map<Object, Object>) target).put(name, newValue);
            }
        }
    }
    
    /**
     * 预编译表达式或从缓存获取
     */
    private Expression getOrCompileExpression(String expression) {
        return compiledExpressionCache.computeIfAbsent(expression, expr -> {
            try {
                cacheMisses.incrementAndGet();
                return expressionParser.parseExpression(expr);
            } catch (ParseException e) {
                throw new ExpressionCompilationException("Failed to compile expression: " + expr, e);
            }
        });
    }
    
    /**
     * 预编译模板表达式或从缓存获取
     */
    private Expression getOrCompileTemplateExpression(String templateExpression) {
        String cacheKey = "template:" + templateExpression;
        return compiledExpressionCache.computeIfAbsent(cacheKey, expr -> {
            try {
                cacheMisses.incrementAndGet();
                return expressionParser.parseExpression(expr, templateParserContext);
            } catch (ParseException e) {
                throw new ExpressionCompilationException("Failed to compile template expression: " + expr, e);
            }
        });
    }
    
    /**
     * 缓存评估结果
     */
    private void cacheResult(String key, String result) {
        // 本地缓存
        expressionResultCache.put(key, result);
        
        // Spring缓存
        Cache cache = getCache();
        if (cache != null) {
            cache.put(key, result);
        }
    }
    
    /**
     * 原始表达式评估实现（作为回退方案）
     */
    private String evaluateExpressionFallback(String expression, Map<String, Object> context) {
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
        
        return result.toString();
    }
    
    /**
     * 表达式工具类，提供常用的函数
     */
    public static class ExpressionUtils {
        /**
         * 检查对象是否为空
         * @param value 待检查的值
         * @return 如果对象为null或空集合/空字符串则返回true
         */
        public boolean isEmpty(Object value) {
            return CommonUtils.isEmpty(value);
        }
        
        /**
         * 检查对象是否不为空
         * @param value 待检查的值
         * @return 如果对象不为null且不为空集合/空字符串则返回true
         */
        public boolean isNotEmpty(Object value) {
            return CommonUtils.isNotEmpty(value);
        }
        
        public String toString(Object obj) {
            if (obj == null) {
                return "";
            }
            return String.valueOf(obj);
        }
        
        public Number parseNumber(String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        
        // ThreadLocal cache for SimpleDateFormat instances by pattern
        private static final ThreadLocal<Map<String, SimpleDateFormat>> DATE_FORMAT_CACHE = 
                ThreadLocal.withInitial(HashMap::new);
                
        public String formatDate(Date date, String pattern) {
            if (date == null || pattern == null) {
                return "";
            }
            try {
                // Get or create SimpleDateFormat instance from ThreadLocal cache
                SimpleDateFormat sdf = DATE_FORMAT_CACHE.get().computeIfAbsent(pattern, 
                        SimpleDateFormat::new);
                return sdf.format(date);
            } catch (Exception e) {
                return date.toString();
            }
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
        
        if (expressions.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // 对于大量表达式，使用并行流提高性能
        if (expressions.size() > 10) {
            return expressions.entrySet().parallelStream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> evaluateExpression(entry.getValue(), context),
                            (v1, v2) -> v1, // 处理键冲突
                            LinkedHashMap::new // 保持插入顺序
                    ));
        } else {
            // 少量表达式使用普通迭代
            Map<String, String> results = new LinkedHashMap<>(expressions.size());
            for (Map.Entry<String, String> entry : expressions.entrySet()) {
                results.put(entry.getKey(), evaluateExpression(entry.getValue(), context));
            }
            return results;
        }
    }
    
    /**
     * 获取表达式引擎的性能统计信息
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheHits", cacheHits.get());
        stats.put("cacheMisses", cacheMisses.get());
        stats.put("expressionEvaluations", expressionEvaluations.get());
        stats.put("fieldCalculations", fieldCalculations.get());
        stats.put("fieldCalculationFailures", fieldCalculationFailures.get());
        stats.put("cacheSize", compiledExpressionCache.size());
        stats.put("propertyAccessorCacheSize", propertyAccessorCache.size());
        stats.put("expressionResultCacheSize", expressionResultCache.size());
        
        double totalOperations = expressionEvaluations.get() + fieldCalculations.get();
        double hitRate = totalOperations > 0 
            ? (double) cacheHits.get() / totalOperations 
            : 0;
        stats.put("cacheHitRate", hitRate);
        
        double failureRate = fieldCalculations.get() > 0
            ? (double) fieldCalculationFailures.get() / fieldCalculations.get()
            : 0;
        stats.put("fieldCalculationFailureRate", failureRate);
        
        return stats;
    }
    
    /**
     * 清除表达式缓存
     */
    public void clearCache() {
        propertyAccessorCache.clear();
        expressionResultCache.clear();
        compiledExpressionCache.clear();
        
        Cache cache = getCache();
        if (cache != null) {
            try {
                cache.clear();
            } catch (Exception e) {
                LOGGER.warn("Failed to clear Spring Cache", e);
            }
        }
        
        // 重置统计计数器
        cacheHits.set(0);
        cacheMisses.set(0);
        
        LOGGER.info("Expression engine cache cleared");
    }
    
    /**
     * 获取缓存实例
     */
    private Cache getCache() {
        return cacheManager != null ? cacheManager.getCache(EXPRESSION_CACHE_NAME) : null;
    }
    
    /**
     * 评估布尔表达式并返回结果
     * 使用SpEL引擎进行更强大的布尔表达式评估
     */
    public boolean evaluateBooleanExpression(String expression, Map<String, Object> context) {
        Assert.notNull(expression, "表达式不能为空");
        Assert.notNull(context, "上下文对象不能为空");
        
        try {
            expressionEvaluations.incrementAndGet();
            
            // 处理${}占位符
            String booleanExpression = expression.contains("$") ? 
                evaluateExpression(expression, context) : expression;
            
            // 处理简单的布尔字面量
            if ("true".equalsIgnoreCase(booleanExpression.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(booleanExpression.trim())) {
                return false;
            }
            
            try {
                // 使用SpEL引擎评估布尔表达式
                Expression expr = getOrCompileExpression(booleanExpression);
                EvaluationContext evalContext = createEvaluationContext(context);
                return Boolean.TRUE.equals(expr.getValue(evalContext, Boolean.class));
            } catch (Exception e) {
                // SpEL评估失败时，回退到原始实现
                LOGGER.warn("SpEL boolean expression evaluation failed, falling back to original implementation: {}", e.getMessage());
                return evaluateBooleanExpressionFallback(booleanExpression, context);
            }
        } catch (Exception e) {
            String errorMsg = String.format("布尔表达式计算失败: %s", expression);
            LOGGER.error(errorMsg, e);
            if (strictMode) {
                throw new ExpressionEvaluationException(errorMsg, e);
            }
            return false; // 非严格模式下返回false
        }
    }
    
    /**
     * 原始布尔表达式评估实现（作为回退方案）
     */
    private boolean evaluateBooleanExpressionFallback(String expression, Map<String, Object> context) {
        // 去除多余空格
        String processedExpression = expression.trim();
        
        // 处理括号嵌套 (递归处理)
        processedExpression = resolveParentheses(processedExpression, context);
        
        // 处理逻辑运算符 && 和 || (短路逻辑)
        if (processedExpression.contains("&&")) {
            String[] parts = processedExpression.split("&&");
            for (String part : parts) {
                if (!evaluateBooleanExpressionFallback(part.trim(), context)) {
                    return false; // 短路：一旦有一个为false，整个AND表达式为false
                }
            }
            return true;
        } else if (processedExpression.contains("||")) {
            String[] parts = processedExpression.split("\\|\\|");
            for (String part : parts) {
                if (evaluateBooleanExpressionFallback(part.trim(), context)) {
                    return true; // 短路：一旦有一个为true，整个OR表达式为true
                }
            }
            return false;
        }
        
        // 处理否定运算符 !
        if (processedExpression.startsWith("!")) {
            String innerExpression = processedExpression.substring(1).trim();
            return !evaluateBooleanExpressionFallback(innerExpression, context);
        }
        
        // 处理数值比较表达式
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
            Pattern stringComparePattern = Pattern.compile("'([^']+)'\\s*([=!<>]=?)\\s*'([^']+)'", Pattern.DOTALL);
            Matcher stringMatcher = stringComparePattern.matcher(processedExpression);
            if (stringMatcher.matches()) {
                String leftStr = stringMatcher.group(1);
                String operator = stringMatcher.group(2);
                String rightStr = stringMatcher.group(3);
                
                switch (operator) {
                    case "==": return leftStr.equals(rightStr);
                    case "!=": return !leftStr.equals(rightStr);
                    case ">=": return leftStr.compareTo(rightStr) >= 0;
                    case "<=": return leftStr.compareTo(rightStr) <= 0;
                    case ">": return leftStr.compareTo(rightStr) > 0;
                    case "<": return leftStr.compareTo(rightStr) < 0;
                }
            }
        }
        
        // 默认返回false
        return false;
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
        String result = sb.toString();
        if (result.contains("(")) {
            return resolveParentheses(result, context);
        }
        
        return result;
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
                LOGGER.warn("Unsupported comparison operator: {}", operator);
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
                LOGGER.warn("Failed to convert string to numeric value: {}", value);
                return BigDecimal.ZERO;
            }
        }
        
        LOGGER.warn("Failed to convert type to numeric value: {}", value.getClass().getName());
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
            LOGGER.debug("Field path evaluation failed: {}", fieldPath, e);
            return null;
        }
    }
    
    /**
     * 通过反射获取对象的属性值
     */
    private Object getPropertyValue(Object obj, String propertyName) {
        if (obj == null || propertyName == null || propertyName.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试从缓存获取访问器方法
            String cacheKey = obj.getClass().getName() + "." + propertyName;
            Method getterMethod = null;
            
            if (cacheEnabled) {
                MethodCacheEntry entry = propertyAccessorCache.get(cacheKey);
                if (entry != null && !entry.isExpired()) {
                    getterMethod = entry.getMethod();
                }
            }
            
            if (getterMethod == null) {
                getterMethod = findGetterMethod(obj, propertyName);
                
                // 缓存方法
                if (cacheEnabled && getterMethod != null) {
                    propertyAccessorCache.put(cacheKey, new MethodCacheEntry(getterMethod, cacheExpirationTime));
                }
            }
            
            // 调用getter方法
            return getterMethod != null ? getterMethod.invoke(obj) : null;
        } catch (Exception e) {
            LOGGER.debug("Failed to get property value: {}.{}", obj.getClass().getName(), propertyName, e);
            return null;
        }
    }
    
    /**
     * 查找getter方法
     */
    private Method findGetterMethod(Object obj, String propertyName) throws Exception {
        // 构造getter方法名
        String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        
        try {
            return obj.getClass().getMethod(getterName);
        } catch (NoSuchMethodException e) {
            // 尝试is方法（用于布尔类型）
            String isName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                return obj.getClass().getMethod(isName);
            } catch (NoSuchMethodException ex) {
                throw ex;
            }
        }
    }
    
    /**
     * 计算实体中的虚拟字段和公式字段
     */
    public <T extends SmartBaseEntity> T calculateFields(T entity, EntityMetadata entityMetadata) {
        Assert.notNull(entity, "实体对象不能为空");
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        
        try {
            LOGGER.debug("Calculating fields for entity {}", entityMetadata.getApiName());
            fieldCalculations.incrementAndGet();
            
            // 创建实体上下文Map用于表达式计算
            Map<String, Object> context = createEntityContext(entity);
            
            // 计算虚拟字段
            calculateVirtualFields(entity, entityMetadata, context);
            
            // 计算公式字段
            calculateFormulaFields(entity, entityMetadata, context);
            
            return entity;
        } catch (Exception e) {
            fieldCalculationFailures.incrementAndGet();
            String errorMsg = String.format("计算实体字段失败: %s", entityMetadata.getApiName());
            LOGGER.error(errorMsg, e);
            if (strictMode) {
                throw new RuntimeException(errorMsg, e);
            }
            return entity;
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
        for (SmartFieldMetadata field : entityMetadata.getFields().values()) {
            if (field.isVirtual() && field.getCalculationExpression() != null) {
                calculateFieldWithErrorHandling(entity, field, context);
            }
        }
    }
    
    /**
     * 计算字段并进行错误处理
     */
    private <T extends SmartBaseEntity> void calculateFieldWithErrorHandling(T entity, SmartFieldMetadata field, Map<String, Object> context) {
        try {
            LOGGER.debug("Calculating virtual field: {}, expression: {}", field.getApiName(), field.getCalculationExpression());
            
            // 使用表达式引擎计算字段值
            String result = evaluateExpression(field.getCalculationExpression(), context);
            
            // 设置计算结果到实体
            setFieldValue(entity, field.getApiName(), result);
            
            LOGGER.debug("Virtual field {} calculation result: {}", field.getApiName(), result);
        } catch (Exception e) {
            fieldCalculationFailures.incrementAndGet();
            LOGGER.error("Failed to calculate virtual field {}: {}", field.getApiName(), e.getMessage(), e);
        }
    }
    
    /**
     * 计算公式字段
     */
    private <T extends SmartBaseEntity> void calculateFormulaFields(T entity, EntityMetadata entityMetadata, Map<String, Object> context) {
        for (SmartFieldMetadata field : entityMetadata.getFields().values()) {
            // 暂时注释掉isFormulaField()和getFormula()调用，因为SmartFieldMetadata类中似乎没有这些方法
            // if (field.isFormulaField() && field.getFormula() != null) {
            //     try {
            //         LOGGER.debug("Calculating formula field: {}, formula: {}", field.getApiName(), field.getFormula());
            //         
            //         // 处理简单的数学公式
            //         Object result = evaluateMathExpression(field.getFormula(), context);
            //         
            //         // 设置计算结果到实体
            //         setFieldValue(entity, field.getApiName(), result);
            //     } catch (Exception e) {
            //         fieldCalculationFailures.incrementAndGet();
            //         LOGGER.error("Error calculating formula field '{}': {}", field.getApiName(), e.getMessage());
            //     }
            // }
        }
    }
    
    /**
     * 计算数学表达式
     * 使用SpEL引擎进行高性能的数学表达式计算
     */
    private Object evaluateMathExpression(String formula, Map<String, Object> context) {
        // 替换公式中的变量引用
        String processedFormula = replaceVariables(formula, context);
        
        try {
            // 使用SpEL引擎计算数学表达式
            Expression expr = getOrCompileExpression(processedFormula);
            EvaluationContext evalContext = createEvaluationContext(context);
            return expr.getValue(evalContext);
        } catch (Exception e) {
            // SpEL计算失败时，回退到原始实现
            LOGGER.warn("SpEL mathematical expression calculation failed, falling back to original implementation: {}", e.getMessage());
            try {
                return evaluateSimpleMath(processedFormula);
            } catch (Exception ex) {
                LOGGER.warn("Simple mathematical expression calculation failed, returning original value: {}", ex.getMessage());
                return processedFormula;
            }
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
                setFieldValueUsingReflection(entity, fieldName, value);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set field value: {}.{}", entity.getClass().getName(), fieldName, e);
            if (strictMode) {
                throw new RuntimeException("设置字段值失败: " + fieldName, e);
            }
        }
    }
    
    /**
     * 使用反射设置字段值
     */
    private <T extends SmartBaseEntity> void setFieldValueUsingReflection(T entity, String fieldName, Object value) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Class<?> entityClass = entity.getClass();
        
        // 尝试查找精确匹配类型的setter方法
        for (Method method : entityClass.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                // 找到匹配名称的setter方法
                // 尝试类型转换并设置值
                Class<?> paramType = method.getParameterTypes()[0];
                Object convertedValue = convertValueToType(value, paramType);
                try {
                    method.invoke(entity, convertedValue);
                } catch (Exception e) {
                    LOGGER.error("Failed to call setter method: {}.{}", entityClass.getName(), setterName, e);
                    // 不再重新抛出异常，避免IllegalAccessException未捕获问题
                }
                return;
            }
        }
        
        LOGGER.warn("Failed to set value for field {}, setter method does not exist", fieldName);
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
                LOGGER.debug("Type conversion failed: {} -> {}", value.getClass().getName(), targetType.getName());
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
    
    /**
     * 表达式编译异常
     */
    public static class ExpressionCompilationException extends RuntimeException {
        public ExpressionCompilationException(String message) {
            super(message);
        }
        
        public ExpressionCompilationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 实现ExpressionEvaluator接口的eval方法
     */
    @Override
    public Object eval(String expression, Map<String, Object> context) {
        // 参数校验
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        try {
            // 增加表达式求值计数
            expressionEvaluations.incrementAndGet();
            
            // 特殊处理简单除法表达式，确保浮点数除法
            if (context != null && expression.contains("/") && expression.trim().split("\\s*/\\s*").length == 2) {
                String[] parts = expression.trim().split("\\s*/\\s*");
                String left = parts[0];
                String right = parts[1];
                
                // 尝试解析左右两边的值
                double leftValue = 0;
                double rightValue = 0;
                
                try {
                    // 检查左边是否是数字或变量
                    if (left.matches("\\d+")) {
                        leftValue = Double.parseDouble(left);
                    } else if (context.containsKey(left)) {
                        Object leftObj = context.get(left);
                        if (leftObj instanceof Number) {
                            leftValue = ((Number) leftObj).doubleValue();
                        }
                    }
                    
                    // 检查右边是否是数字或变量
                    if (right.matches("\\d+")) {
                        rightValue = Double.parseDouble(right);
                    } else if (context.containsKey(right)) {
                        Object rightObj = context.get(right);
                        if (rightObj instanceof Number) {
                            rightValue = ((Number) rightObj).doubleValue();
                        }
                    }
                    
                    // 如果两边都是有效的数字，直接进行浮点数除法计算
                    if (rightValue != 0) {
                        return leftValue / rightValue;
                    }
                } catch (Exception e) {
                    // 如果解析失败，继续使用SpEL引擎
                }
            }
            
            // 创建评估上下文，使用createEvaluationContext方法以支持Map属性访问
            StandardEvaluationContext evaluationContext = (StandardEvaluationContext) createEvaluationContext(context);
            
            // 同时将每个键值对设置为变量，支持通过#变量名访问
            if (context != null) {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    evaluationContext.setVariable(entry.getKey(), entry.getValue());
                }
            }
            
            // 编译并缓存表达式
            Expression compiledExpression = getOrCompileExpression(expression);
            
            // 执行表达式求值
            return compiledExpression.getValue(evaluationContext);
        } catch (ExpressionCompilationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("表达式求值失败: {}", expression, e);
            // 根据strictMode决定是抛出异常还是返回null
            if (!strictMode) {
                LOGGER.warn("非严格模式下，表达式求值失败返回null: {}", expression);
                return null;
            }
            throw new ExpressionEvaluationException("表达式求值失败: " + expression, e);
        }
    }
}