package com.bone.engine.extension.expression;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContexts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 表达式求值器，负责评估业务规则表达式
 * <p>
 * 基于Spring Expression Language (SpEL)，支持从业务上下文中提取变量进行动态表达式求值
 * 提供高性能表达式缓存机制，避免重复解析带来的性能损耗
 *
 * @author renhui.trh 2023-11-9
 */
@Slf4j
public final class ExpressionEvaluator {
    // 表达式缓存，避免重复解析表达式
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();
    // 单例的表达式解析器
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    // 缓存读写锁，保护clearCache操作
    private static final ReentrantReadWriteLock CACHE_LOCK = new ReentrantReadWriteLock();
    // 缓存大小限制，防止内存溢出
    private static final int MAX_CACHE_SIZE = 1000;

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
            throw new IllegalArgumentException("Expression must not be null or empty");
        }
        Objects.requireNonNull(bizContext, "Business context must not be null");
        
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
            throw new RuntimeException("Failed to evaluate expression: " + expression, e);
        }
    }

    /**
     * 使用当前上下文评估表达式
     * 
     * @param expression 表达式字符串
     * @return 表达式评估结果
     */
    public static boolean evaluateWithCurrentContext(String expression) {
        BizContext context = BizContexts.getCurrent();
        return evaluate(expression, context);
    }

    /**
     * 从缓存获取或解析表达式
     */
    private static Expression getOrParseExpression(String expression) {
        return EXPRESSION_CACHE.computeIfAbsent(expression, key -> {
            // 检查缓存大小，防止内存溢出
            checkAndTrimCache();
            return EXPRESSION_PARSER.parseExpression(key);
        });
    }

    /**
     * 构建表达式评估上下文
     */
    private static EvaluationContext buildEvaluationContext(BizContext bizContext) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 添加标准维度变量
        context.setVariable("tenantCode", bizContext.getTenantCode());
        context.setVariable("bizCode", bizContext.getBizCode());
        context.setVariable("useCase", bizContext.getUseCase());
        context.setVariable("scenario", bizContext.getScenario());
        context.setVariable("data", bizContext.getData());
        
        // 添加所有扩展属性作为变量
        if (bizContext.getAttributes() != null) {
            for (Object keyObj : bizContext.getAttributes().keySet()) {
                String key = String.valueOf(keyObj);
                context.setVariable(key, bizContext.getAttributes().get(keyObj));
            }
        }
        
        return context;
    }

    /**
     * 检查并修剪缓存，防止内存溢出
     */
    private static void checkAndTrimCache() {
        if (EXPRESSION_CACHE.size() >= MAX_CACHE_SIZE) {
            try {
                CACHE_LOCK.writeLock().lock();
                // 二次检查，避免多线程重复修剪
                if (EXPRESSION_CACHE.size() >= MAX_CACHE_SIZE) {
                    // 移除一半的缓存项，使用LRU策略（简单实现：移除前一半）
                    EXPRESSION_CACHE.entrySet().stream()
                            .limit(EXPRESSION_CACHE.size() / 2)
                            .map(Map.Entry::getKey)
                            .forEach(EXPRESSION_CACHE::remove);
                    log.debug("Expression cache trimmed to size: {}", EXPRESSION_CACHE.size());
                }
            } finally {
                CACHE_LOCK.writeLock().unlock();
            }
        }
    }

    /**
     * 清除表达式缓存
     */
    public static void clearCache() {
        try {
            CACHE_LOCK.writeLock().lock();
            EXPRESSION_CACHE.clear();
            log.info("Expression cache cleared");
        } finally {
            CACHE_LOCK.writeLock().unlock();
        }
    }

    /**
     * 获取当前缓存大小
     * 
     * @return 缓存中的表达式数量
     */
    public static int getCacheSize() {
        try {
            CACHE_LOCK.readLock().lock();
            return EXPRESSION_CACHE.size();
        } finally {
            CACHE_LOCK.readLock().unlock();
        }
    }
}

