package com.bone.engine.extension.support.expression;

import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * 高性能 SpEL 表达式求值器
 *
 * 设计原则：
 * 1. 简洁高效：专注核心功能，避免过度设计
 * 2. 性能优先：合理的缓存策略
 * 3. 健壮性：完善的异常处理
 * 4. 通用性：支持任意 Object 上下文
 */
public class SpELExpressionEvaluator implements ExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(SpELExpressionEvaluator.class);

    // 核心组件
    private final ExpressionParser expressionParser;
    private final Cache<String, Expression> expressionCache;
    private final Cache<String, Predicate<Object>> predicateCache;

    public SpELExpressionEvaluator() {
        this(1000, Duration.ofHours(1));
    }

    public SpELExpressionEvaluator(int maxCacheSize, Duration cacheExpire) {
        Assert.isTrue(maxCacheSize > 0, "Cache size must be positive");
        Assert.notNull(cacheExpire, "Cache expire duration cannot be null");

        this.expressionParser = new SpelExpressionParser();
        this.expressionCache = buildExpressionCache(maxCacheSize, cacheExpire);
        this.predicateCache = buildPredicateCache(maxCacheSize, cacheExpire);

        log.info("SpELExpressionEvaluator initialized with cacheSize: {}", maxCacheSize);
    }

    @Override
    public boolean evaluate(@NonNull String expression, @NonNull Object context) {
        Assert.hasText(expression, "Expression cannot be null or empty");
        Assert.notNull(context, "Context cannot be null");

        // 空表达式或 "true" 直接返回 true
        if (isAlwaysTrue(expression)) {
            return true;
        }

        try {
            Expression spelExpression = getOrCompileExpression(expression);
            StandardEvaluationContext evalContext = buildEvaluationContext(context);
            Object result = spelExpression.getValue(evalContext);
            return convertToBoolean(result);
        } catch (Exception e) {
            log.warn("SpEL evaluation failed, return false. expression: {}", expression, e);
            return false;
        }
    }

    @Override
    @NonNull
    public Predicate<Object> compile(@NonNull String expression) {
        Assert.hasText(expression, "Expression cannot be null or empty");

        // 空表达式返回始终为 true 的谓词
        if (isAlwaysTrue(expression)) {
            return context -> true;
        }

        return predicateCache.get(expression, this::compileToPredicate);
    }

    // ==================== 核心私有方法 ====================

    /**
     * 检查是否为始终为 true 的表达式
     */
    private boolean isAlwaysTrue(String expression) {
        return expression.trim().isEmpty() || "true".equalsIgnoreCase(expression.trim());
    }

    /**
     * 构建表达式缓存
     */
    private Cache<String, Expression> buildExpressionCache(int maxSize, Duration expire) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expire)
                .build();
    }

    /**
     * 构建谓词缓存
     */
    private Cache<String, Predicate<Object>> buildPredicateCache(int maxSize, Duration expire) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expire)
                .build();
    }

    /**
     * 获取或编译表达式
     */
    private Expression getOrCompileExpression(@NonNull String expression) {
        Expression cached = expressionCache.getIfPresent(expression);
        if (cached != null) {
            return cached;
        }

        // 编译新表达式
        Expression compiled = compileRawExpression(expression);
        expressionCache.put(expression, compiled);
        return compiled;
    }

    /**
     * 编译原始表达式
     */
    private Expression compileRawExpression(@NonNull String expression) {
        try {
            return expressionParser.parseExpression(expression);
        } catch (Exception e) {
            log.error("Failed to compile SpEL expression: {}", expression, e);
            throw new IllegalArgumentException("Failed to compile expression: " + expression, e);
        }
    }

    /**
     * 编译表达式为谓词
     */
    private Predicate<Object> compileToPredicate(@NonNull String expression) {
        Expression spelExpression = getOrCompileExpression(expression);

        return context -> {
            try {
                StandardEvaluationContext evalContext = buildEvaluationContext(context);
                Object result = spelExpression.getValue(evalContext);
                return convertToBoolean(result);
            } catch (Exception e) {
                log.debug("SpEL predicate evaluation failed: {}", expression, e);
                return false;
            }
        };
    }

    /**
     * 构建评估上下文
     */
    private StandardEvaluationContext buildEvaluationContext(@NonNull Object context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();

        // 设置根对象，支持直接属性访问
        evalContext.setRootObject(context);

        // 注入上下文变量，支持 #context 引用
        evalContext.setVariable("context", context);
        evalContext.setVariable("ctx", context);

        return evalContext;
    }

    /**
     * 转换为布尔值
     */
    private boolean convertToBoolean(Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        if (result instanceof String) {
            String str = (String) result;
            return !str.isEmpty() && !"false".equalsIgnoreCase(str);
        }
        return true; // 非空对象视为 true
    }

    // ==================== 工具方法 ====================

    /**
     * 清空缓存
     */
    public void clearCache() {
        expressionCache.invalidateAll();
        predicateCache.invalidateAll();
        log.debug("SpEL expression cache cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public void logCacheStats() {
        log.debug("SpEL cache stats: expressions={}, predicates={}",
                expressionCache.estimatedSize(), predicateCache.estimatedSize());
    }
}