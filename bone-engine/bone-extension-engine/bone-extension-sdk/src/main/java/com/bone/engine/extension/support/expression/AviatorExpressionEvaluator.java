package com.bone.engine.extension.support.expression;

import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Aviator 表达式求值器 - 简洁高效实现
 */
public class AviatorExpressionEvaluator implements ExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(AviatorExpressionEvaluator.class);

    private final Cache<String, Expression> expressionCache;
    private final Cache<String, Predicate<Object>> predicateCache;

    public AviatorExpressionEvaluator() {
        this(1000, Duration.ofHours(1));
    }

    public AviatorExpressionEvaluator(int maxCacheSize, Duration cacheExpire) {
        Assert.isTrue(maxCacheSize > 0, "Cache size must be positive");
        Assert.notNull(cacheExpire, "Cache expire duration cannot be null");

        this.expressionCache = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterAccess(cacheExpire)
                .build();

        this.predicateCache = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterAccess(cacheExpire)
                .build();

        configureAviator();
        log.info("AviatorExpressionEvaluator initialized with cacheSize: {}", maxCacheSize);
    }

    @Override
    public boolean evaluate(@NonNull String expression, @NonNull Object context) {
        Assert.hasText(expression, "Expression cannot be null or empty");
        Assert.notNull(context, "Context cannot be null");

        if (isAlwaysTrue(expression)) {
            return true;
        }

        try {
            Expression compiledExpr = getOrCompile(expression);
            Map<String, Object> env = createExecutionEnv(context);
            Object result = compiledExpr.execute(env);
            return convertToBoolean(result);
        } catch (Exception e) {
            log.warn("Expression evaluation failed, return false. expression: {}", expression, e);
            return false;
        }
    }

    @Override
    @NonNull
    public Predicate<Object> compile(@NonNull String expression) {
        Assert.hasText(expression, "Expression cannot be null or empty");

        if (isAlwaysTrue(expression)) {
            return context -> true;
        }

        return predicateCache.get(expression, this::compileToPredicate);
    }

    /**
     * 配置 Aviator 引擎
     */
    private void configureAviator() {
        try {
            AviatorEvaluator.getInstance()
                    .setCachedExpressionByDefault(true);
            // 移除不存在的 useUserEnvAsTopEnvDirectly() 方法
        } catch (Exception e) {
            log.debug("Aviator configuration completed with default settings");
        }
    }

    /**
     * 检查是否为始终为 true 的表达式
     */
    private boolean isAlwaysTrue(String expression) {
        return expression.trim().isEmpty() || "true".equalsIgnoreCase(expression.trim()); // 修复：添加括号
    }

    private Expression getOrCompile(String expression) {
        return expressionCache.get(expression, this::compileExpression);
    }

    private Expression compileExpression(String expression) {
        try {
            return AviatorEvaluator.compile(expression, true);
        } catch (Exception e) {
            log.error("Failed to compile expression: {}", expression, e);
            throw new IllegalArgumentException("Invalid expression: " + expression, e);
        }
    }

    private Predicate<Object> compileToPredicate(String expression) {
        Expression compiledExpr = getOrCompile(expression);

        return context -> {
            try {
                Map<String, Object> env = createExecutionEnv(context);
                Object result = compiledExpr.execute(env);
                return convertToBoolean(result);
            } catch (Exception e) {
                log.debug("Predicate evaluation failed: {}", expression, e);
                return false;
            }
        };
    }

    private Map<String, Object> createExecutionEnv(Object context) {
        Map<String, Object> env = new HashMap<>();
        env.put("context", context);
        env.put("ctx", context);

        if (context instanceof Map) {
            env.putAll((Map<String, Object>) context);
        }

        return env;
    }

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
        return true;
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        expressionCache.invalidateAll();
        predicateCache.invalidateAll();
        log.debug("Expression cache cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public void logCacheStats() {
        log.debug("Expression cache stats: size={}, predicate cache stats: size={}",
                expressionCache.estimatedSize(), predicateCache.estimatedSize());
    }
}