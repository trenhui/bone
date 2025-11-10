package com.bone.smartmeta.engine.expression;

import com.bone.smartmeta.engine.ExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExpressionEvaluator测试类
 * 提供全面的测试覆盖，确保表达式引擎功能正确
 */
class ExpressionEvaluatorTest {

    private ExpressionEngine evaluator;
    private Map<String, Object> context;

    @BeforeEach
    void setUp() {
        evaluator = new ExpressionEngine();
        
        // 初始化测试上下文
        context = new HashMap<>();
        
        // 初始化测试数据
        context.put("name", "张三");
        context.put("age", 25);
        context.put("salary", 8500.50);
        context.put("isActive", true);
        context.put("department", "技术部");
        
        // 添加嵌套对象
        Map<String, Object> address = new HashMap<>();
        address.put("city", "北京");
        address.put("zipCode", "100001");
        context.put("address", address);
        
        // 添加数组
        String[] skills = {"Java", "Spring", "MySQL"};
        context.put("skills", skills);
    }

    @Test
    void testEvaluateBasicExpressions() {
        // 基本属性访问
        assertEquals("张三", evaluator.eval("name", context), "姓名属性访问失败");
        assertEquals(25, evaluator.eval("age", context), "年龄属性访问失败");
        assertEquals(true, evaluator.eval("isActive", context), "活跃状态属性访问失败");
        assertEquals(8500.50, evaluator.eval("salary", context), "薪资属性访问失败");
        
        // 简单算术运算
        assertEquals(30, evaluator.eval("age + 5", context), "加法运算失败");
        assertEquals(20, evaluator.eval("age - 5", context), "减法运算失败");
        assertEquals(50, evaluator.eval("age * 2", context), "乘法运算失败");
        assertEquals(12.5, evaluator.eval("age / 2", context), "除法运算失败");
    }

    @Test
    void testEvaluateSimpleBooleanExpression() {
        // 简单布尔表达式
        assertTrue((Boolean) evaluator.eval("age > 18", context), "年龄大于18测试失败");
        assertFalse((Boolean) evaluator.eval("age < 18", context), "年龄小于18测试失败");
        assertTrue((Boolean) evaluator.eval("isActive == true", context), "活跃状态测试失败");
    }

    @ParameterizedTest
    @CsvSource({
        "age > 20, true",
        "age < 30, true",
        "salary > 8000, true",
        "salary < 9000, true",
        "name == '张三', true",
        "department.contains('技术'), true"
    })
    void testEvaluateComparisonExpressions(String expression, boolean expected) {
        // 参数化测试各种比较表达式
        Object result = evaluator.eval(expression, context);
        assertNotNull(result, "表达式计算结果不应该为null");
        assertTrue(result instanceof Boolean, "结果应该是布尔类型");
        assertEquals(expected, result, "表达式 '" + expression + "' 计算结果不匹配");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "name",
        "age",
        "salary",
        "isActive",
        "department"
    })
    void testEvaluateSimplePropertyAccess(String property) {
        // 测试直接属性访问
        Object result = evaluator.eval(property, context);
        assertNotNull(result, "属性 '" + property + "' 访问结果不应该为null");
        assertEquals(context.get(property), result, "属性值不匹配");
    }

    @Test
    void testEvaluateNestedObjectExpression() {
        // 测试嵌套对象属性访问
        assertEquals("北京", evaluator.eval("address.city", context), "嵌套对象城市属性访问失败");
        assertEquals("100001", evaluator.eval("address.zipCode", context), "嵌套对象邮编属性访问失败");
        
        // 测试嵌套对象的条件表达式
        assertTrue((Boolean) evaluator.eval("address.city == '北京'", context), "嵌套对象条件表达式失败");
    }

    @Test
    void testEvaluateArrayExpression() {
        // 测试数组元素访问
        assertEquals("Java", evaluator.eval("skills[0]", context), "数组第一个元素访问失败");
        
        // 测试数组长度
        assertEquals(3, evaluator.eval("skills.length", context), "数组长度访问失败");
    }

    @Test
    void testEvaluateComplexExpression() {
        // 测试复杂组合表达式
        boolean complexResult = (Boolean) evaluator.eval("age > 18 && isActive && department == '技术部'", context);
        assertTrue(complexResult, "复杂条件表达式结果不匹配");
        
        // 测试三元表达式
        String ternaryResult = (String) evaluator.eval("age > 18 ? '成年人' : '未成年人'", context);
        assertEquals("成年人", ternaryResult, "三元表达式结果不匹配");
    }

    @Test
    void testEvaluateNullExpression() {
        // 测试空表达式，预期抛出异常
        Exception exception = assertThrows(Exception.class, () -> {
            evaluator.eval(null, context);
        }, "空表达式应该抛出异常");
        assertNotNull(exception, "异常对象不应该为null");
    }

    @Test
    void testEvaluateEmptyExpression() {
        // 测试空字符串表达式，预期抛出异常
        Exception exception = assertThrows(Exception.class, () -> {
            evaluator.eval("", context);
        }, "空字符串表达式应该抛出异常");
        assertNotNull(exception, "异常对象不应该为null");
    }

    @Test
    void testEvaluateWithStrictModeDisabled() {
        // 禁用严格模式
        evaluator.setStrictMode(false);
        
        // 测试未知变量，在非严格模式下不应该抛出异常
        Object unknownVarResult = evaluator.eval("unknownVariable", context);
        // 在非严格模式下，未知变量可能返回null
        assertNull(unknownVarResult, "未知变量在非严格模式下应该返回null");
    }

    @Test
    void testEvaluateMethodInvocation() {
        // 测试方法调用表达式
        assertEquals("张三", evaluator.eval("name.toString()", context), "字符串方法调用失败");
        assertTrue((Boolean) evaluator.eval("name.contains('张')", context), "contains方法调用失败");
        assertTrue((Boolean) evaluator.eval("department.startsWith('技术')", context), "startsWith方法调用失败");
    }
}