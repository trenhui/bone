package com.bone.engine.extension.expression;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExpressionEvaluator的单元测试类
 * <p>
 * 测试表达式评估、缓存机制和性能优化
 */
public class ExpressionEvaluatorTest {

    private BizContext<String> context;

    @BeforeEach
    public void setUp() {
        // 清除缓存，确保测试环境干净
        ExpressionEvaluator.clearCache();
        
        // 创建测试上下文
        context = BizContext.of("tenant1", "biz1");
    }

    @Test
    public void testSimpleExpressionEvaluation() {
        // 测试简单表达式
        boolean result = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        assertTrue(result, "表达式评估应该返回true");
    }

    @Test
    public void testComplexExpressionEvaluation() {
        // 测试复杂表达式
        boolean result = ExpressionEvaluator.evaluate(
                "#tenantCode == 'tenant1' && #bizCode == 'biz1'",
                context);
        assertTrue(result, "复杂表达式评估应该返回true");
    }

    @Test
    public void testExpressionCache() {
        // 第一次评估表达式
        boolean result1 = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        
        // 检查缓存大小是否增加
        int cacheSizeAfterFirst = ExpressionEvaluator.getCacheSize();
        assertTrue(cacheSizeAfterFirst > 0, "第一次评估后缓存应该包含表达式");
        
        // 第二次评估相同表达式
        boolean result2 = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        
        // 缓存大小应该保持不变
        int cacheSizeAfterSecond = ExpressionEvaluator.getCacheSize();
        assertEquals(cacheSizeAfterFirst, cacheSizeAfterSecond, "第二次评估应该使用缓存，缓存大小不变");
        
        // 清除缓存
        ExpressionEvaluator.clearCache();
        int cacheSizeAfterClear = ExpressionEvaluator.getCacheSize();
        assertEquals(0, cacheSizeAfterClear, "清除缓存后缓存大小应为0");
        
        // 确保结果正确
        assertTrue(result1 && result2, "两次评估结果应该都为true");
    }

    @Test
    public void testDifferentContextEvaluation() {
        // 使用不同上下文评估相同表达式
        BizContext<String> differentContext = BizContext.of("tenant2", "biz1");

        boolean result1 = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        boolean result2 = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", differentContext);
        
        assertTrue(result1, "第一个上下文评估应该返回true");
        assertFalse(result2, "第二个上下文评估应该返回false");
    }

    @Test
    public void testClearCache() {
        // 评估表达式，确保缓存被创建
        ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        
        // 清除缓存
        ExpressionEvaluator.clearCache();
        
        // 再次评估，应该重新编译（稍慢）
        long startTime = System.currentTimeMillis();
        boolean result = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        long time = System.currentTimeMillis() - startTime;
        
        assertTrue(result, "表达式评估应该返回true");
        // 这里不做时间断言，因为我们已经调用了clearCache
    }

    @Test
    public void testInvalidExpression() {
        // 测试无效表达式
        Exception exception = assertThrows(RuntimeException.class, () -> {
            ExpressionEvaluator.evaluate("#tenantCode == 非法语法", context);
        });
        
        assertTrue(exception.getMessage().contains("Failed to evaluate expression"), 
                "应该抛出包含特定错误消息的异常");
    }

    @Test
    public void testNullContext() {
        // 测试空上下文
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", null);
        });
        
        assertTrue(exception.getMessage().contains("context must not be null"), 
                "应该抛出参数验证异常");
    }

    @Test
    public void testNullExpression() {
        // 测试空表达式
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ExpressionEvaluator.evaluate(null, context);
        });
        
        assertTrue(exception.getMessage().contains("expression must not be null or empty"), 
                "应该抛出参数验证异常");
    }
}