package com.bone.smartmeta.engine.expression;

import com.bone.smartmeta.engine.config.EngineConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ExpressionEvaluatorTest {

    @Mock
    private EngineConfiguration config;

    @InjectMocks
    private DefaultExpressionEvaluator evaluator;

    private Map<String, Object> context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试上下文
        context = new HashMap<>();
        
        // 设置配置
        when(config.getBoolean("expression.cache.enabled", true)).thenReturn(true);
        when(config.getInt("expression.cache.size", 1000)).thenReturn(1000);
        
        // 初始化测试数据
        initTestData();
    }

    private void initTestData() {
        // 添加基本类型数据
        context.put("name", "张三");
        context.put("age", 25);
        context.put("salary", 8500.50);
        context.put("isActive", true);
        
        // 添加复杂类型数据
        Map<String, Object> address = new HashMap<>();
        address.put("city", "北京");
        address.put("district", "朝阳区");
        address.put("zipCode", "100022");
        context.put("address", address);
    }

    @Test
    void testEvaluateBooleanExpression() {
        // 测试简单布尔表达式
        assertTrue(evaluator.evaluate("age > 18", context));
        assertTrue(evaluator.evaluate("age >= 25 && salary > 8000", context));
        assertFalse(evaluator.evaluate("age < 18 || salary < 5000", context));
        assertTrue(evaluator.evaluate("isActive", context));
    }

    @Test
    void testEvaluateArithmeticExpression() {
        // 测试算术表达式
        assertEquals(30, evaluator.evaluate("age + 5", context));
        assertEquals(20, evaluator.evaluate("age - 5", context));
        assertEquals(125, evaluator.evaluate("age * 5", context));
        assertEquals(5, evaluator.evaluate("age / 5", context));
        assertEquals(2.5, evaluator.evaluate("salary / 3400", context));
    }

    @Test
    void testEvaluateStringExpression() {
        // 测试字符串表达式
        assertEquals("张三", evaluator.evaluate("name", context));
        assertEquals(2, evaluator.evaluate("name.length()", context));
        assertTrue(evaluator.evaluate("name.contains('张')", context));
        assertEquals("张三先生", evaluator.evaluate("name + '先生'", context));
    }

    @Test
    void testEvaluateObjectPathExpression() {
        // 测试对象路径表达式
        assertEquals("北京", evaluator.evaluate("address.city", context));
        assertTrue(evaluator.evaluate("address.city.equals('北京')", context));
        assertTrue(evaluator.evaluate("address.zipCode.length() == 6", context));
    }

    @Test
    void testEvaluateComplexExpression() {
        // 测试复杂表达式
        Object result1 = evaluator.evaluate("age > 18 ? '成年人' : '未成年人'", context);
        assertEquals("成年人", result1);
        
        Object result2 = evaluator.evaluate("salary * 12 + (salary * 0.3)", context);
        assertEquals(8500.50 * 12 + (8500.50 * 0.3), result2);
        
        boolean result3 = evaluator.evaluate("age > 20 && (address.city.equals('北京') || address.city.equals('上海'))", context);
        assertTrue(result3);
    }

    @Test
    void testEvaluateNullExpression() {
        // 测试空表达式
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            evaluator.evaluate(null, context);
        });
        assertTrue(exception.getMessage().contains("表达式不能为空"));
    }

    @Test
    void testEvaluateEmptyExpression() {
        // 测试空字符串表达式
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            evaluator.evaluate("", context);
        });
        assertTrue(exception.getMessage().contains("表达式不能为空"));
    }

    @Test
    void testEvaluateInvalidExpression() {
        // 测试无效表达式
        Exception exception = assertThrows(ExpressionEvaluationException.class, () -> {
            evaluator.evaluate("age + *", context); // 无效的算术表达式
        });
        assertNotNull(exception);
    }

    @Test
    void testEvaluateUnknownVariable() {
        // 测试未知变量
        Exception exception = assertThrows(ExpressionEvaluationException.class, () -> {
            evaluator.evaluate("unknownVariable > 0", context);
        });
        assertNotNull(exception);
    }

    @Test
    void testEvaluateDivisionByZero() {
        // 测试除零错误
        Exception exception = assertThrows(ExpressionEvaluationException.class, () -> {
            evaluator.evaluate("10 / 0", context);
        });
        assertNotNull(exception);
    }

    @Test
    void testEvaluateMethodCall() {
        // 测试方法调用
        assertEquals(2, evaluator.evaluate("'AB'.length()", context));
        assertTrue(evaluator.evaluate("'hello'.startsWith('h')", context));
        assertTrue(evaluator.evaluate("'hello'.endsWith('o')", context));
        assertEquals("HELLO", evaluator.evaluate("'hello'.toUpperCase()", context));
        assertEquals("hello", evaluator.evaluate("' HELLO '.trim()", context));
    }

    @Test
    void testEvaluateComparisonOperators() {
        // 测试比较运算符
        assertTrue(evaluator.evaluate("age > 18", context));
        assertTrue(evaluator.evaluate("age >= 25", context));
        assertFalse(evaluator.evaluate("age < 18", context));
        assertFalse(evaluator.evaluate("age <= 20", context));
        assertTrue(evaluator.evaluate("age == 25", context));
        assertFalse(evaluator.evaluate("age != 25", context));
    }

    @Test
    void testEvaluateLogicalOperators() {
        // 测试逻辑运算符
        assertTrue(evaluator.evaluate("age > 20 && salary > 8000", context));
        assertTrue(evaluator.evaluate("age > 30 || salary > 8000", context));
        assertFalse(evaluator.evaluate("!(age > 20)", context));
    }

    @Test
    void testEvaluateTernaryOperator() {
        // 测试三元运算符
        assertEquals("高薪资", evaluator.evaluate("salary > 8000 ? '高薪资' : '低薪资'", context));
        assertEquals("北京地区", evaluator.evaluate("address.city.equals('北京') ? '北京地区' : '其他地区'", context));
        assertEquals(2500, evaluator.evaluate("isActive ? salary * 0.3 : 0", context));
    }

    @Test
    void testEvaluateNestedExpression() {
        // 测试嵌套表达式
        Object result1 = evaluator.evaluate("(age > 20 ? (salary > 8000 ? '符合条件' : '薪资不符合') : '年龄不符合')", context);
        assertEquals("符合条件", result1);
        
        boolean result2 = evaluator.evaluate("(address.city.equals('北京') && address.district.equals('朝阳区'))", context);
        assertTrue(result2);
    }

    @Test
    void testExpressionCache() {
        // 测试表达式缓存功能
        long start1 = System.currentTimeMillis();
        evaluator.evaluate("age > 18 && salary > 8000 && address.city.equals('北京')", context);
        long end1 = System.currentTimeMillis();
        
        // 第二次执行应该更快（从缓存中获取）
        long start2 = System.currentTimeMillis();
        evaluator.evaluate("age > 18 && salary > 8000 && address.city.equals('北京')", context);
        long end2 = System.currentTimeMillis();
        
        // 由于有缓存，第二次执行应该更快（虽然实际测试中差值可能很小）
        assertTrue((end2 - start2) <= (end1 - start1));
    }

    @Test
    void testEvaluateWithDifferentContext() {
        // 创建一个新的上下文
        Map<String, Object> newContext = new HashMap<>();
        newContext.put("name", "李四");
        newContext.put("age", 30);
        newContext.put("salary", 9000.00);
        
        // 使用不同的上下文计算相同的表达式
        assertEquals("李四", evaluator.evaluate("name", newContext));
        assertTrue(evaluator.evaluate("age > 25", newContext));
        assertTrue(evaluator.evaluate("salary > 8500", newContext));
    }
}