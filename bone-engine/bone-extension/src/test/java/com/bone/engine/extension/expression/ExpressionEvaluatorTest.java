package com.bone.engine.extension.expression;

import com.bone.engine.extension.BizContext;
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

    private BizContext context;

    @BeforeEach
    public void setUp() {
        // 清除缓存，确保测试环境干净
        ExpressionEvaluator.clearCache();
        
        // 创建测试上下文
        context = BizContext.builder()
                .tenantCode("tenant1")
                .bizCode("biz1")
                .build();
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
        // 第一次评估表达式（应该编译）
        long startTime1 = System.currentTimeMillis();
        boolean result1 = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        long time1 = System.currentTimeMillis() - startTime1;
        
        // 第二次评估相同表达式（应该使用缓存）
        long startTime2 = System.currentTimeMillis();
        boolean result2 = ExpressionEvaluator.evaluate("#tenantCode == 'tenant1'", context);
        long time2 = System.currentTimeMillis() - startTime2;
        
        assertTrue(result1 && result2, "两次评估结果应该都为true");
        assertTrue(time2 < time1, "使用缓存的评估应该比第一次更快");
    }

    @Test
    public void testDifferentContextEvaluation() {
        // 使用不同上下文评估相同表达式
        BizContext differentContext = BizContext.builder()
                .tenantCode("tenant2")
                .bizCode("biz1")
                .build();

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