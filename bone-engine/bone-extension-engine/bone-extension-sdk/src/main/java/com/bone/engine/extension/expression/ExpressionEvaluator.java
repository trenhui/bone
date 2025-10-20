package com.bone.engine.extension.expression;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtensionContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import com.bone.engine.extension.exception.ExpressionEvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

/**
 * 表达式求值器，负责评估业务规则表达式
 * <p>
 * 基于Spring Expression Language (SpEL)，支持从业务上下文中提取变量进行动态表达式求值
 * 提供高性能表达式缓存机制，避免重复解析带来的性能损耗
 *
 * @author renhui.trh 2023-11-9
 */
public final class ExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ExpressionEvaluator.class);
    // 最大缓存表达式数量，避免内存溢出
    private static final int MAX_CACHE_SIZE = 1000;
    // 表达式缓存，使用Caffeine提供高性能LRU缓存
    private static final Cache<String, Expression> EXPRESSION_CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(1, TimeUnit.HOURS) // 缓存1小时后过期
            .recordStats() // 开启统计
            .evictionListener((key, value, cause) -> {
                if (log.isTraceEnabled()) {
                    log.trace("Expression cache entry evicted: {}, cause: {}", key, cause);
                }
            })
            .build();
    // 单例的表达式解析器
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /**
     * 私有构造函数，防止实例化
     */
    private ExpressionEvaluator() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    /**
     * 评估表达式是否匹配
     * 
     * @param expression 表达式字符串
     * @param bizContext 业务上下文
     * @return 表达式评估结果
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当表达式求值失败时抛出
     */
    public static boolean evaluate(String expression, BizContext bizContext) {
        // 参数验证
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("expression must not be null or empty");
        }
        if (bizContext == null) {
            throw new IllegalArgumentException("Business context must not be null");
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 从缓存获取或解析表达式
            Expression spelExpression = getOrParseExpression(expression);
            
            // 构建评估上下文，注入所有上下文变量
            EvaluationContext context = buildEvaluationContext(bizContext);
            
            // 执行表达式求值
            Boolean result = spelExpression.getValue(context, Boolean.class);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Expression evaluated: {} = {} (took {}ms)", expression, result, executionTime);
            
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            log.error("Failed to evaluate expression: {}", expression, e);
            throw new ExpressionEvaluationException(expression, "Failed to evaluate expression", e);
        }
    }

    /**
     * 使用当前上下文评估表达式
     * 
     * @param expression 表达式字符串
     * @return 表达式评估结果
     */
    public static boolean evaluateWithCurrentContext(String expression) {
        BizContext context = (BizContext) ExtensionContextManager.getCurrent();
        return evaluate(expression, context);
    }

    /**
     * 从缓存获取或解析表达式
     * 使用Caffeine缓存提供高性能LRU缓存和线程安全
     * 
     * @param expression 表达式字符串
     * @return 解析后的Expression对象
     */
    private static Expression getOrParseExpression(String expression) {
        // 确保表达式不为null或空
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("expression must not be null or empty");
        }
        
        try {
            // 使用Caffeine的get方法，自动处理缓存命中和未命中情况
            return EXPRESSION_CACHE.get(expression, expr -> {
                // 解析表达式
                log.debug("Parsing new expression: {}", expr);
                return EXPRESSION_PARSER.parseExpression(expr);
            });
        } catch (Exception e) {
            log.error("Failed to parse expression: {}", expression, e);
            throw new ExpressionEvaluationException(expression, "Failed to parse expression", e);
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return EXPRESSION_CACHE.stats().toString();
    }

    /**
     * 构建表达式评估上下文
     */
    private static EvaluationContext buildEvaluationContext(BizContext bizContext) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 注入标准业务维度变量，方便直接在表达式中使用
        context.setVariable("tenantCode", bizContext.getTenantCode());
        context.setVariable("bizCode", bizContext.getBizCode());
        context.setVariable("useCase", bizContext.getUseCase());
        context.setVariable("scenario", bizContext.getScenario());
        
        // 注入data对象，方便直接访问业务数据
        context.setVariable("data", bizContext.getData());
        
        // 注入扩展属性map，方便访问自定义属性
        context.setVariable("attributes", bizContext.getAttributes());
        
        // 设置根对象为业务上下文，支持直接访问其属性
        context.setRootObject(bizContext);
        
        // 注入完整的上下文对象，方便在表达式中访问
        context.setVariable("context", bizContext);
        context.setVariable("bizContext", bizContext);
        
        return context;
    }



    /**
     * 清除表达式缓存
     */
    public static void clearCache() {
        EXPRESSION_CACHE.invalidateAll();
        log.info("Expression cache cleared");
    }

    /**
     * 获取当前缓存大小
     * 
     * @return 缓存中的表达式数量
     */
    public static int getCacheSize() {
        // 注意：由于Caffeine不提供精确的size()方法，这里返回估计值
        // 在实际使用中，通常不需要精确知道缓存大小
        return (int)EXPRESSION_CACHE.estimatedSize();
    }
    
    /**
     * 预加载表达式到缓存
     * 
     * @param expressions 表达式字符串列表
     */
    public static void preloadExpressions(List<String> expressions) {
        if (expressions == null || expressions.isEmpty()) {
            return;
        }
        
        expressions.forEach(expr -> {
            try {
                EXPRESSION_CACHE.put(expr, EXPRESSION_PARSER.parseExpression(expr));
            } catch (Exception e) {
                log.warn("Failed to preload expression: {}", expr, e);
            }
        });
        
        log.info("Preloaded {} expressions into cache", expressions.size());
    }
    
    // 移除重复的方法定义
}

