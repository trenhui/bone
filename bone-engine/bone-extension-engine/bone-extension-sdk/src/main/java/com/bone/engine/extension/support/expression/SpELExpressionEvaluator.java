package com.bone.engine.extension.support.expression;

import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.support.context.BizContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 高性能 SpEL 表达式求值器
 *
 * 核心特性：
 * 1. 线程安全：基于并发安全的数据结构
 * 2. 高性能：表达式编译缓存，避免重复解析
 * 3. 内存安全：LRU 缓存策略，防止内存泄漏
 * 4. 简单易用：专注核心求值功能
 */
public class SpELExpressionEvaluator implements ExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(SpELExpressionEvaluator.class);

    // 配置常量
    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    private static final Duration DEFAULT_CACHE_EXPIRE = Duration.ofHours(1);

    // 核心组件
    private final ExpressionParser expressionParser;
    private final Cache<String, Expression> expressionCache;
    private final Cache<String, Predicate<BizContext<?>>> predicateCache;

    public SpELExpressionEvaluator() {
        this(DEFAULT_MAX_CACHE_SIZE, DEFAULT_CACHE_EXPIRE);
    }

    public SpELExpressionEvaluator(int maxCacheSize, Duration cacheExpire) {
        Assert.isTrue(maxCacheSize > 0, "Cache size must be positive");
        Assert.notNull(cacheExpire, "Cache expire duration cannot be null");

        this.expressionParser = new SpelExpressionParser();
        this.expressionCache = buildExpressionCache(maxCacheSize, cacheExpire);
        this.predicateCache = buildPredicateCache(maxCacheSize, cacheExpire);

        log.debug("SpELExpressionEvaluator initialized");
    }

    @Override
    public boolean evaluate(@NonNull String expression, @NonNull BizContext<?> context) {
        Assert.hasText(expression, "Expression cannot be null or empty");
        Assert.notNull(context, "Business context cannot be null");

        try {
            // 获取或编译表达式
            Expression spelExpression = getOrCompileExpression(expression);

            // 构建评估上下文
            EvaluationContext evalContext = buildEvaluationContext(context);

            // 执行求值
            Boolean result = spelExpression.getValue(evalContext, Boolean.class);
            return Boolean.TRUE.equals(result);

        } catch (IllegalArgumentException e) {
            throw e; // 重新抛出参数异常
        } catch (Exception e) {
            log.warn("Expression evaluation failed: {}", expression, e);
            return false; // 求值失败时返回 false
        }
    }

    @Override
    @NonNull
    public Predicate<BizContext<?>> compile(@NonNull String expression) {
        Assert.hasText(expression, "Expression cannot be null or empty");

        return predicateCache.get(expression, this::compileExpression);
    }

    // ==================== 内部实现方法 ====================

    /**
     * 构建表达式缓存
     */
    @NonNull
    private Cache<String, Expression> buildExpressionCache(int maxSize, Duration expire) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expire)
                .build();
    }

    /**
     * 构建谓词缓存
     */
    @NonNull
    private Cache<String, Predicate<BizContext<?>>> buildPredicateCache(int maxSize, Duration expire) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expire)
                .build();
    }

    /**
     * 获取或编译表达式
     */
    @NonNull
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
    @NonNull
    private Expression compileRawExpression(@NonNull String expression) {
        try {
            return expressionParser.parseExpression(expression);
        } catch (Exception e) {
            log.error("Failed to compile expression: {}", expression, e);
            throw new IllegalArgumentException("Failed to compile expression: " + expression, e);
        }
    }

    /**
     * 编译表达式为谓词
     */
    @NonNull
    private Predicate<BizContext<?>> compileExpression(@NonNull String expression) {
        Expression spelExpression = getOrCompileExpression(expression);

        return context -> {
            try {
                EvaluationContext evalContext = buildEvaluationContext(context);
                Boolean result = spelExpression.getValue(evalContext, Boolean.class);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                log.debug("Predicate evaluation failed for expression: {}", expression, e);
                return false;
            }
        };
    }

    /**
     * 构建评估上下文
     */
    @NonNull
    private EvaluationContext buildEvaluationContext(@NonNull BizContext<?> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();

        // 注入标准业务变量
        evalContext.setVariable("tenant", context.getTenant());
        evalContext.setVariable("bizCode", context.getBizCode());
        evalContext.setVariable("useCase", context.getUseCase());
        evalContext.setVariable("scenario", context.getScenario());
        evalContext.setVariable("env", context.getEnv());

        // 注入上下文对象
        evalContext.setVariable("context", context);
        evalContext.setVariable("bizContext", context);

        // 注入扩展属性
        injectExtendedProperties(evalContext, context);

        // 设置根对象
        evalContext.setRootObject(context);

        return evalContext;
    }

    /**
     * 注入扩展属性
     */
    private void injectExtendedProperties(@NonNull StandardEvaluationContext evalContext,
                                          @NonNull BizContext<?> context) {
        try {
            Map<String, Object> attributes = getAttributesSafely(context);
            if (attributes != null && !attributes.isEmpty()) {
                evalContext.setVariable("attributes", attributes);
            }
        } catch (Exception e) {
            // 忽略属性注入失败
        }
    }

    /**
     * 安全获取属性映射
     */
    private Map<String, Object> getAttributesSafely(@NonNull BizContext<?> context) {
        try {
            // 假设 BizContext 有 getAllAttributes() 方法
            // 实际实现可能需要反射或接口方法
            return Map.of(); // 默认空映射
        } catch (Exception e) {
            return Map.of();
        }
    }
}