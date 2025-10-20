package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.*;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DefaultBusinessRuleEngineTest {

    @Mock
    private BusinessRuleRegistry businessRuleRegistry;

    @InjectMocks
    private DefaultBusinessRuleEngine ruleEngine;

    private List<BusinessRule> validationRules;
    private List<BusinessRule> actionRules;
    private Map<String, Object> customerData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试数据
        initRules();
        initCustomerData();
        
        // 设置mock行为
        when(businessRuleRegistry.getRulesByEventType("Customer", "CREATE")).thenReturn(validationRules);
        when(businessRuleRegistry.getRulesByEventType("Customer", "UPDATE")).thenReturn(actionRules);
    }

    private void initRules() {
        // 验证规则
        validationRules = new ArrayList<>();
        
        BusinessRule ageRule = new BusinessRule();
        ageRule.setRuleName("AgeValidation");
        ageRule.setEntityName("Customer");
        ageRule.setRuleType(RuleType.VALIDATION);
        ageRule.setEventType("CREATE");
        ageRule.setExpression("age >= 18");
        ageRule.setErrorMessage("客户年龄必须满18岁");
        ageRule.setPriority(10);
        validationRules.add(ageRule);
        
        BusinessRule emailRule = new BusinessRule();
        emailRule.setRuleName("EmailValidation");
        emailRule.setEntityName("Customer");
        emailRule.setRuleType(RuleType.VALIDATION);
        emailRule.setEventType("CREATE");
        emailRule.setExpression("email != null && email.contains('@')");
        emailRule.setErrorMessage("请输入有效的邮箱地址");
        emailRule.setPriority(5);
        validationRules.add(emailRule);
        
        // 操作规则
        actionRules = new ArrayList<>();
        
        BusinessRule vipRule = new BusinessRule();
        vipRule.setRuleName("VIPCheckRule");
        vipRule.setEntityName("Customer");
        vipRule.setRuleType(RuleType.ACTION);
        vipRule.setEventType("UPDATE");
        vipRule.setExpression("spendAmount >= 10000");
        vipRule.setAction("setVipLevel('GOLD')");
        vipRule.setPriority(15);
        actionRules.add(vipRule);
    }

    private void initCustomerData() {
        customerData = new HashMap<>();
        customerData.put("name", "张三");
        customerData.put("age", 25);
        customerData.put("email", "zhangsan@example.com");
        customerData.put("spendAmount", 15000.0);
    }

    @Test
    void testExecuteValidationRules_Success() {
        // 执行测试
        List<ValidationResult> results = ruleEngine.executeValidationRules("Customer", customerData, "CREATE");
        
        // 验证结果
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (ValidationResult result : results) {
            assertTrue(result.isValid());
        }
    }

    @Test
    void testExecuteValidationRules_Failure() {
        // 修改数据使验证失败
        customerData.put("age", 16); // 年龄不足18岁
        
        // 执行测试
        List<ValidationResult> results = ruleEngine.executeValidationRules("Customer", customerData, "CREATE");
        
        // 验证结果
        assertNotNull(results);
        assertFalse(results.isEmpty());
        boolean hasInvalidResult = false;
        for (ValidationResult result : results) {
            if (!result.isValid()) {
                hasInvalidResult = true;
                assertTrue(result.getErrors().containsValue("客户年龄必须满18岁"));
            }
        }
        assertTrue(hasInvalidResult);
    }

    @Test
    void testExecuteActionRules() {
        // 执行测试
        List<RuleExecutionResult> results = ruleEngine.executeActionRules("Customer", customerData, "UPDATE");
        
        // 验证结果
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (RuleExecutionResult result : results) {
            assertTrue(result.isSuccess());
        }
    }

    @Test
    void testExecuteRule_ValidationRule() {
        // 获取验证规则
        BusinessRule validationRule = validationRules.get(0);
        
        // 执行测试
        RuleExecutionResult result = ruleEngine.executeRule(validationRule, "Customer", customerData);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testExecuteRule_ActionRule() {
        // 获取操作规则
        BusinessRule actionRule = actionRules.get(0);
        
        // 执行测试
        RuleExecutionResult result = ruleEngine.executeRule(actionRule, "Customer", customerData);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidateRuleExpression_Valid() {
        // 执行测试
        boolean isValid = ruleEngine.validateRuleExpression("age > 18 && status == 'ACTIVE'");
        
        // 验证结果
        assertTrue(isValid);
    }

    @Test
    void testValidateRuleExpression_Invalid() {
        // 执行测试 - 无效表达式
        boolean isValid = ruleEngine.validateRuleExpression("age > 18 &&"); // 缺少右侧操作数
        
        // 验证结果
        assertFalse(isValid);
    }

    @Test
    void testGetRuleDependencies() {
        // 执行测试
        String expression = "age > 18 && email != null && status == 'ACTIVE'";
        java.util.Set<String> dependencies = ruleEngine.getRuleDependencies(expression);
        
        // 验证结果
        assertNotNull(dependencies);
        assertEquals(3, dependencies.size());
        assertTrue(dependencies.contains("age"));
        assertTrue(dependencies.contains("email"));
        assertTrue(dependencies.contains("status"));
    }

    @Test
    void testGetRulesByEventType() {
        // 执行测试
        List<BusinessRule> rules = ruleEngine.getRulesByEventType("Customer", "CREATE");
        
        // 验证结果
        assertNotNull(rules);
        assertEquals(2, rules.size());
        assertEquals("AgeValidation", rules.get(0).getRuleName());
        assertEquals("EmailValidation", rules.get(1).getRuleName());
    }

    @Test
    void testExecuteRule_EmptyExpression() {
        // 创建空表达式规则
        BusinessRule emptyRule = new BusinessRule();
        emptyRule.setRuleName("EmptyRule");
        emptyRule.setEntityName("Customer");
        emptyRule.setRuleType(RuleType.VALIDATION);
        emptyRule.setExpression("");
        
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ruleEngine.executeRule(emptyRule, "Customer", customerData);
        });
        
        assertTrue(exception.getMessage().contains("规则表达式不能为空"));
    }

    @Test
    void testExecuteValidationRules_NoRules() {
        // 设置无规则
        when(businessRuleRegistry.getRulesByEventType("Customer", "DELETE")).thenReturn(new ArrayList<>());
        
        // 执行测试
        List<ValidationResult> results = ruleEngine.executeValidationRules("Customer", customerData, "DELETE");
        
        // 验证结果 - 应该返回空列表
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}