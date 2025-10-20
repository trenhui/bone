package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.model.BusinessRule;
import com.bone.smartmeta.engine.model.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultBusinessRuleRegistryTest {

    @InjectMocks
    private DefaultBusinessRuleRegistry ruleRegistry;

    private BusinessRule validationRule1;
    private BusinessRule validationRule2;
    private BusinessRule actionRule1;
    private BusinessRule actionRule2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试规则
        initRules();
        
        // 注册规则到registry
        ruleRegistry.registerRule(validationRule1);
        ruleRegistry.registerRule(validationRule2);
        ruleRegistry.registerRule(actionRule1);
        ruleRegistry.registerRule(actionRule2);
    }

    private void initRules() {
        // 验证规则1
        validationRule1 = new BusinessRule();
        validationRule1.setRuleId("rule-001");
        validationRule1.setRuleName("CustomerAgeValidation");
        validationRule1.setEntityName("Customer");
        validationRule1.setRuleType(RuleType.VALIDATION);
        validationRule1.setEventType("CREATE");
        validationRule1.setExpression("age >= 18");
        validationRule1.setErrorMessage("客户年龄必须满18岁");
        validationRule1.setPriority(10);
        validationRule1.setActive(true);

        // 验证规则2
        validationRule2 = new BusinessRule();
        validationRule2.setRuleId("rule-002");
        validationRule2.setRuleName("CustomerEmailValidation");
        validationRule2.setEntityName("Customer");
        validationRule2.setRuleType(RuleType.VALIDATION);
        validationRule2.setEventType("CREATE");
        validationRule2.setExpression("email != null && email.contains('@')");
        validationRule2.setErrorMessage("请输入有效的邮箱地址");
        validationRule2.setPriority(5);
        validationRule2.setActive(true);

        // 操作规则1
        actionRule1 = new BusinessRule();
        actionRule1.setRuleId("rule-003");
        actionRule1.setRuleName("CustomerVIPCheck");
        actionRule1.setEntityName("Customer");
        actionRule1.setRuleType(RuleType.ACTION);
        actionRule1.setEventType("UPDATE");
        actionRule1.setExpression("spendAmount >= 10000");
        actionRule1.setAction("setVipLevel('GOLD')");
        actionRule1.setPriority(15);
        actionRule1.setActive(true);

        // 操作规则2
        actionRule2 = new BusinessRule();
        actionRule2.setRuleId("rule-004");
        actionRule2.setRuleName("ProductDiscountRule");
        actionRule2.setEntityName("Product");
        actionRule2.setRuleType(RuleType.ACTION);
        actionRule2.setEventType("CREATE");
        actionRule2.setExpression("category == 'ELECTRONICS'");
        actionRule2.setAction("setDiscount(0.1)");
        actionRule2.setPriority(5);
        actionRule2.setActive(true);
    }

    @Test
    void testRegisterRule_Success() {
        // 创建一个新规则
        BusinessRule newRule = new BusinessRule();
        newRule.setRuleId("rule-005");
        newRule.setRuleName("TestRule");
        newRule.setEntityName("TestEntity");
        newRule.setRuleType(RuleType.VALIDATION);
        newRule.setEventType("CREATE");
        newRule.setExpression("field > 0");
        newRule.setActive(true);
        
        // 注册规则
        ruleRegistry.registerRule(newRule);
        
        // 验证规则是否成功注册
        BusinessRule retrievedRule = ruleRegistry.getRuleById("rule-005");
        assertNotNull(retrievedRule);
        assertEquals("TestRule", retrievedRule.getRuleName());
    }

    @Test
    void testRegisterRule_NullRule() {
        // 验证空规则注册异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ruleRegistry.registerRule(null);
        });
        
        assertTrue(exception.getMessage().contains("规则不能为空"));
    }

    @Test
    void testRegisterRule_DuplicateRuleId() {
        // 尝试注册相同ID的规则
        BusinessRule duplicateRule = new BusinessRule();
        duplicateRule.setRuleId("rule-001"); // 与已存在规则ID相同
        duplicateRule.setRuleName("DuplicateRule");
        duplicateRule.setEntityName("Customer");
        duplicateRule.setRuleType(RuleType.VALIDATION);
        duplicateRule.setEventType("CREATE");
        duplicateRule.setExpression("field > 0");
        duplicateRule.setActive(true);
        
        // 验证重复ID异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ruleRegistry.registerRule(duplicateRule);
        });
        
        assertTrue(exception.getMessage().contains("规则ID已存在"));
    }

    @Test
    void testGetRuleById() {
        // 获取规则
        BusinessRule rule = ruleRegistry.getRuleById("rule-001");
        
        // 验证结果
        assertNotNull(rule);
        assertEquals("CustomerAgeValidation", rule.getRuleName());
        assertEquals("Customer", rule.getEntityName());
    }

    @Test
    void testGetRuleById_NonExistent() {
        // 获取不存在的规则
        BusinessRule rule = ruleRegistry.getRuleById("non-existent-rule");
        
        // 验证结果
        assertNull(rule);
    }

    @Test
    void testGetRulesByEntity() {
        // 获取特定实体的所有规则
        List<BusinessRule> customerRules = ruleRegistry.getRulesByEntity("Customer");
        List<BusinessRule> productRules = ruleRegistry.getRulesByEntity("Product");
        List<BusinessRule> nonExistentRules = ruleRegistry.getRulesByEntity("NonExistentEntity");
        
        // 验证结果
        assertNotNull(customerRules);
        assertEquals(3, customerRules.size()); // 2个验证规则 + 1个操作规则
        
        assertNotNull(productRules);
        assertEquals(1, productRules.size());
        
        assertNotNull(nonExistentRules);
        assertTrue(nonExistentRules.isEmpty());
    }

    @Test
    void testGetRulesByEventType() {
        // 获取特定实体和事件类型的规则
        List<BusinessRule> customerCreateRules = ruleRegistry.getRulesByEventType("Customer", "CREATE");
        List<BusinessRule> customerUpdateRules = ruleRegistry.getRulesByEventType("Customer", "UPDATE");
        List<BusinessRule> nonExistentEntityRules = ruleRegistry.getRulesByEventType("NonExistentEntity", "CREATE");
        
        // 验证结果
        assertNotNull(customerCreateRules);
        assertEquals(2, customerCreateRules.size()); // 2个Customer-CREATE规则
        
        assertNotNull(customerUpdateRules);
        assertEquals(1, customerUpdateRules.size()); // 1个Customer-UPDATE规则
        
        assertNotNull(nonExistentEntityRules);
        assertTrue(nonExistentEntityRules.isEmpty());
    }

    @Test
    void testGetRulesByType() {
        // 获取特定规则类型的规则
        List<BusinessRule> validationRules = ruleRegistry.getRulesByType(RuleType.VALIDATION);
        List<BusinessRule> actionRules = ruleRegistry.getRulesByType(RuleType.ACTION);
        
        // 验证结果
        assertNotNull(validationRules);
        assertEquals(2, validationRules.size());
        
        assertNotNull(actionRules);
        assertEquals(2, actionRules.size());
    }

    @Test
    void testUpdateRule() {
        // 获取要更新的规则
        BusinessRule ruleToUpdate = ruleRegistry.getRuleById("rule-001");
        assertNotNull(ruleToUpdate);
        
        // 修改规则属性
        ruleToUpdate.setExpression("age >= 21");
        ruleToUpdate.setErrorMessage("客户年龄必须满21岁");
        
        // 更新规则
        ruleRegistry.updateRule(ruleToUpdate);
        
        // 验证更新结果
        BusinessRule updatedRule = ruleRegistry.getRuleById("rule-001");
        assertNotNull(updatedRule);
        assertEquals("age >= 21", updatedRule.getExpression());
        assertEquals("客户年龄必须满21岁", updatedRule.getErrorMessage());
    }

    @Test
    void testUpdateRule_NonExistent() {
        // 创建一个不存在的规则
        BusinessRule nonExistentRule = new BusinessRule();
        nonExistentRule.setRuleId("non-existent-rule");
        nonExistentRule.setRuleName("NonExistentRule");
        
        // 验证更新不存在规则异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ruleRegistry.updateRule(nonExistentRule);
        });
        
        assertTrue(exception.getMessage().contains("规则不存在"));
    }

    @Test
    void testDeleteRule() {
        // 删除规则
        ruleRegistry.deleteRule("rule-001");
        
        // 验证规则是否被删除
        BusinessRule deletedRule = ruleRegistry.getRuleById("rule-001");
        assertNull(deletedRule);
        
        // 验证规则列表中不再包含该规则
        List<BusinessRule> customerRules = ruleRegistry.getRulesByEntity("Customer");
        assertEquals(2, customerRules.size()); // 应该是2个规则而不是3个
    }

    @Test
    void testDeleteRule_NonExistent() {
        // 删除不存在的规则，应该静默成功或不抛出异常
        assertDoesNotThrow(() -> {
            ruleRegistry.deleteRule("non-existent-rule");
        });
    }

    @Test
    void testActivateRule() {
        // 获取规则并将其设为非激活
        BusinessRule rule = ruleRegistry.getRuleById("rule-001");
        rule.setActive(false);
        ruleRegistry.updateRule(rule);
        
        // 激活规则
        ruleRegistry.activateRule("rule-001");
        
        // 验证规则已激活
        BusinessRule activatedRule = ruleRegistry.getRuleById("rule-001");
        assertTrue(activatedRule.isActive());
    }

    @Test
    void testDeactivateRule() {
        // 停用规则
        ruleRegistry.deactivateRule("rule-001");
        
        // 验证规则已停用
        BusinessRule deactivatedRule = ruleRegistry.getRuleById("rule-001");
        assertFalse(deactivatedRule.isActive());
        
        // 验证获取规则时不会返回非激活规则
        List<BusinessRule> activeRules = ruleRegistry.getRulesByEventType("Customer", "CREATE");
        assertEquals(1, activeRules.size()); // 应该只有1个激活的规则
    }

    @Test
    void testGetRulesByEntityAndType() {
        // 获取特定实体和规则类型的规则
        List<BusinessRule> customerValidationRules = ruleRegistry.getRulesByEntityAndType("Customer", RuleType.VALIDATION);
        List<BusinessRule> customerActionRules = ruleRegistry.getRulesByEntityAndType("Customer", RuleType.ACTION);
        
        // 验证结果
        assertNotNull(customerValidationRules);
        assertEquals(2, customerValidationRules.size());
        
        assertNotNull(customerActionRules);
        assertEquals(1, customerActionRules.size());
    }
}