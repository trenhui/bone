package com.bone.smartmeta.engine.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * ExpressionEngine测试类
 * 验证表达式引擎的主要功能
 */
public class ExpressionEngineTest {

    public static void main(String[] args) {
        System.out.println("=== Testing ExpressionEngine ===");
        
        // 创建表达式引擎实例
        ExpressionEngine engine = new ExpressionEngine();
        
        // 测试1: 基本变量替换
        testVariableReplacement(engine);
        
        // 测试2: 简单数学运算
        testMathOperations(engine);
        
        // 测试3: 计算字段
        testCalculateFields(engine);
        
        // 测试4: 布尔表达式求值
        testBooleanExpression(engine);
        
        // 测试5: 缓存功能
        testCacheFunctionality(engine);
        
        // 测试6: 异常处理
        testExceptionHandling(engine);
        
        System.out.println("\n=== All tests completed! ===");
    }
    
    /**
     * 测试基本变量替换功能
     */
    private static void testVariableReplacement(ExpressionEngine engine) {
        System.out.println("\n1. Testing Variable Replacement:");
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "SmartMeta");
        variables.put("version", "1.0");
        
        String expression1 = "Hello, ${name}!";
        String expression2 = "Version: ${version}";
        
        Object result1 = engine.evaluateExpression(expression1, variables);
        Object result2 = engine.evaluateExpression(expression2, variables);
        
        System.out.println("  Expression: " + expression1 + " => " + result1);
        System.out.println("  Expression: " + expression2 + " => " + result2);
    }
    
    /**
     * 测试数学运算功能
     */
    private static void testMathOperations(ExpressionEngine engine) {
        System.out.println("\n2. Testing Math Operations:");
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("x", "10");
        variables.put("y", "5");
        
        String expression1 = "${x} + ${y}";
        String expression2 = "${x} - ${y}";
        String expression3 = "42.5";
        
        Object result1 = engine.evaluateExpression(expression1, variables);
        Object result2 = engine.evaluateExpression(expression2, variables);
        Object result3 = engine.evaluateExpression(expression3, variables);
        
        System.out.println("  Expression: " + expression1 + " => " + result1);
        System.out.println("  Expression: " + expression2 + " => " + result2);
        System.out.println("  Expression: " + expression3 + " => " + result3);
    }
    
    /**
     * 测试计算字段功能
     */
    private static void testCalculateFields(ExpressionEngine engine) {
        System.out.println("\n3. Testing Calculate Fields:");
        
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Product A");
        data.put("price", "100");
        data.put("discount", "20");
        data.put("calc_finalPrice", "${price} - ${discount}");
        data.put("calc_description", "Product: ${name}");
        
        Map<String, Object> results = engine.calculateFields("Product", data);
        
        System.out.println("  Calculated Fields:");
        results.forEach((key, value) -> System.out.println("    " + key + " = " + value));
    }
    
    /**
     * 测试布尔表达式功能
     */
    private static void testBooleanExpression(ExpressionEngine engine) {
        System.out.println("\n4. Testing Boolean Expressions:");
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("isActive", "true");
        variables.put("isActiveFalse", "false");
        
        boolean result1 = engine.evaluateBooleanExpression("true", variables);
        boolean result2 = engine.evaluateBooleanExpression("false", variables);
        boolean result3 = engine.evaluateBooleanExpression("${isActive}", variables);
        boolean result4 = engine.evaluateBooleanExpression("${isActiveFalse}", variables);
        
        System.out.println("  Expression: true => " + result1);
        System.out.println("  Expression: false => " + result2);
        System.out.println("  Expression: ${isActive} => " + result3);
        System.out.println("  Expression: ${isActiveFalse} => " + result4);
    }
    
    /**
     * 测试缓存功能
     */
    private static void testCacheFunctionality(ExpressionEngine engine) {
        System.out.println("\n5. Testing Cache Functionality:");
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", "100");
        
        String expression = "${value}";
        
        System.out.println("  Initial cache size: " + engine.getCacheSize());
        
        // 第一次执行 - 不使用缓存
        Object result1 = engine.evaluateExpression(expression, variables);
        System.out.println("  After first execution, cache size: " + engine.getCacheSize());
        
        // 第二次执行 - 应使用缓存
        Object result2 = engine.evaluateExpression(expression, variables);
        System.out.println("  After second execution, cache size: " + engine.getCacheSize());
        
        // 清除缓存
        engine.clearCache();
        System.out.println("  After clearing cache, cache size: " + engine.getCacheSize());
    }
    
    /**
     * 测试异常处理功能
     */
    private static void testExceptionHandling(ExpressionEngine engine) {
        System.out.println("\n6. Testing Exception Handling:");
        
        try {
            // 测试null表达式
            engine.evaluateExpression(null, new HashMap<>());
        } catch (NullPointerException e) {
            System.out.println("  ✓ Correctly caught NullPointerException for null expression");
        }
        
        try {
            // 测试null变量
            engine.evaluateExpression("test", null);
        } catch (NullPointerException e) {
            System.out.println("  ✓ Correctly caught NullPointerException for null variables");
        }
        
        try {
            // 测试无效的布尔表达式结果类型
            Map<String, Object> variables = new HashMap<>();
            variables.put("number", "123");
            engine.evaluateBooleanExpression("${number}", variables);
        } catch (ExpressionEngine.ExpressionEvaluationException e) {
            // 检查是否由ClassCastException引起
            if (e.getCause() instanceof ClassCastException) {
                System.out.println("  ✓ Correctly caught ExpressionEvaluationException caused by ClassCastException");
            } else {
                throw e; // 如果不是由ClassCastException引起，则重新抛出
            }
        }
    }
}