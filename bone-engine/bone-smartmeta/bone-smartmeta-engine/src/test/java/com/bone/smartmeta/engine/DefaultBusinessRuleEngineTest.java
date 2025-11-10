package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import com.bone.smartmeta.engine.model.RuleResult;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.InjectMocks;
import com.bone.smartmeta.engine.ExpressionEngine;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DefaultBusinessRuleEngine的单元测试类
 * 遵循JUnit 5和Mockito最佳实践
 */
class DefaultBusinessRuleEngineTest {

    @Mock
    private BusinessRuleRegistry businessRuleRegistry;

    @Mock
    private ExpressionEngine expressionEngine;

    @InjectMocks
    private DefaultBusinessRuleEngine ruleEngine;

    private List<BusinessRuleMetadata> validationRules;
    private Map<String, Object> customerData;
    private Map<String, Object> orderData;

    @BeforeEach
    void setUp() {
        // 使用最新的Mockito初始化方式
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试数据
        initRules();
        initTestData();
    }

    private void initRules() {
        // 验证规则
        validationRules = new ArrayList<>();
        
        BusinessRuleMetadata ageRule = new BusinessRuleMetadata();
        ageRule.setId("age-rule");
        ageRule.setName("AgeValidation");
        ageRule.setApiName("Customer");
        ageRule.setRuleType("VALIDATION");
        ageRule.getTriggerEvents().add("CREATE");
        ageRule.setExpression("age >= 18");
        ageRule.setErrorMessage("客户年龄必须满18岁");
        ageRule.setActive(true);
        validationRules.add(ageRule);
        
        BusinessRuleMetadata emailRule = new BusinessRuleMetadata();
        emailRule.setId("email-rule");
        emailRule.setName("EmailValidation");
        emailRule.setApiName("Customer");
        emailRule.setRuleType("VALIDATION");
        emailRule.getTriggerEvents().add("CREATE");
        emailRule.setExpression("email != null && email.contains('@')");
        emailRule.setErrorMessage("请输入有效的邮箱地址");
        emailRule.setActive(true);
        validationRules.add(emailRule);
    }

    private void initTestData() {
        // 客户数据
        customerData = new HashMap<>();
        customerData.put("name", "张三");
        customerData.put("age", 25);
        customerData.put("email", "zhangsan@example.com");
        customerData.put("spendAmount", 15000.0);
        
        // 订单数据
        orderData = new HashMap<>();
        orderData.put("orderId", "ORD-2024-001");
        orderData.put("amount", 20000.0);
        orderData.put("currency", "CNY");
    }

    @Test
    void testExecuteRules_Success() {
        // 准备测试数据
        List<String> ruleNames = Arrays.asList("AgeValidation", "EmailValidation");
        
        // 模拟getRulesByName方法返回规则列表
        when(businessRuleRegistry.getRulesByName(ruleNames)).thenReturn(validationRules);
        
        // 模拟expressionEngine.eval方法返回true
        when(expressionEngine.eval(anyString(), eq(customerData))).thenReturn(true);
        
        // 执行测试
        RuleResult result = ruleEngine.executeRules("Customer", customerData, ruleNames);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
        
        // 验证mock调用
        verify(businessRuleRegistry).getRulesByName(ruleNames);
        verify(expressionEngine, times(2)).eval(anyString(), eq(customerData));
    }

    @Test
    void testExecuteRules_ValidationFailure() {
        // 准备测试数据
        List<String> ruleNames = Arrays.asList("AgeValidation");
        
        // 修改数据使验证失败
        customerData.put("age", 16); // 年龄不足18岁
        
        // 模拟getRulesByName方法返回规则列表
        when(businessRuleRegistry.getRulesByName(ruleNames)).thenReturn(Collections.singletonList(validationRules.get(0)));
        
        // 模拟expressionEngine.eval方法返回false
        when(expressionEngine.eval("age >= 18", customerData)).thenReturn(false);
        
        // 执行测试
        RuleResult result = ruleEngine.executeRules("Customer", customerData, ruleNames);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().containsKey("AgeValidation"));
        assertTrue(result.getErrors().get("AgeValidation").contains("客户年龄必须满18岁"));
    }

    @Test
    void testValidate_Success() {
        // 模拟getRulesByEventType方法返回验证规则
        when(businessRuleRegistry.getRulesByEventType("Customer", "CREATE")).thenReturn(validationRules);
        
        // 模拟expressionEngine.eval方法返回true
        when(expressionEngine.eval(anyString(), eq(customerData))).thenReturn(true);
        
        // 执行测试
        RuleResult result = ruleEngine.validate("Customer", customerData, "CREATE");
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidate_NoRules() {
        // 模拟getRulesByEventType方法返回空列表
        when(businessRuleRegistry.getRulesByEventType("Customer", "DELETE")).thenReturn(Collections.emptyList());
        
        // 执行测试
        RuleResult result = ruleEngine.validate("Customer", customerData, "DELETE");
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess()); // 没有规则时默认验证通过
    }

    @Test
    void testCalculateFields() {
        // 准备计算字段的上下文
        Map<String, Object> fieldDefinitions = new HashMap<>();
        fieldDefinitions.put("discountAmount", "amount * 0.1");
        fieldDefinitions.put("finalAmount", "amount - discountAmount");
        
        // 模拟expressionEngine.eval方法的返回值
        when(expressionEngine.eval("amount * 0.1", orderData)).thenReturn(2000.0);
        when(expressionEngine.eval("amount - discountAmount", anyMap())).thenReturn(18000.0);
        
        // 执行测试
        Map<String, Object> result = ruleEngine.calculateFields(orderData, fieldDefinitions);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(2000.0, result.get("discountAmount"));
        assertEquals(18000.0, result.get("finalAmount"));
    }

    @Test
    void testExecuteRules_WithExpressionError() {
        // 准备测试数据
        List<String> ruleNames = Arrays.asList("AgeValidation");
        
        // 模拟getRulesByName方法返回规则列表
        when(businessRuleRegistry.getRulesByName(ruleNames)).thenReturn(Collections.singletonList(validationRules.get(0)));
        
        // 模拟expressionEngine.eval方法抛出异常
        when(expressionEngine.eval("age >= 18", customerData)).thenThrow(new RuntimeException("表达式计算错误"));
        
        // 执行测试
        RuleResult result = ruleEngine.executeRules("Customer", customerData, ruleNames);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void testExecuteRules_NullRuleNames() {
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ruleEngine.executeRules("Customer", customerData, null);
        });
        
        assertNotNull(exception.getMessage());
    }

    @Test
    void testExecuteRules_NullEntityData() {
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ruleEngine.executeRules("Customer", null, Collections.singletonList("AgeValidation"));
        });
        
        assertNotNull(exception.getMessage());
    }
}