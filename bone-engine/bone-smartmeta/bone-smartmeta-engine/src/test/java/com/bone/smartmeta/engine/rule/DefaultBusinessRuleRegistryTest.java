package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultBusinessRuleRegistryTest {

    @InjectMocks
    private DefaultBusinessRuleRegistry ruleRegistry;

    private BusinessRuleMetadata validationRule1;
    private BusinessRuleMetadata validationRule2;
    private BusinessRuleMetadata actionRule1;
    private BusinessRuleMetadata actionRule2;

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
        validationRule1 = new BusinessRuleMetadata();
        validationRule1.setId("rule-001");
        validationRule1.setName("CustomerAgeValidation");
        validationRule1.setApiName("Customer");
        validationRule1.setRuleType("VALIDATION");
        validationRule1.getTriggerEvents().add("CREATE");
        validationRule1.setExpression("age >= 18");
        validationRule1.setErrorMessage("客户年龄必须满18岁");
        validationRule1.setPriority(10);
        validationRule1.setActive(true);

        // 验证规则2
        validationRule2 = new BusinessRuleMetadata();
        validationRule2.setId("rule-002");
        validationRule2.setName("CustomerEmailValidation");
        validationRule2.setApiName("Customer");
        validationRule2.setRuleType("VALIDATION");
        validationRule2.getTriggerEvents().add("CREATE");
        validationRule2.setExpression("email != null && email.contains('@')");
        validationRule2.setErrorMessage("请输入有效的邮箱地址");
        validationRule2.setPriority(5);
        validationRule2.setActive(true);

        // 操作规则1
        actionRule1 = new BusinessRuleMetadata();
        actionRule1.setId("rule-003");
        actionRule1.setName("CustomerVIPCheck");
        actionRule1.setApiName("Customer");
        actionRule1.setRuleType("ACTION");
        actionRule1.getTriggerEvents().add("UPDATE");
        actionRule1.setExpression("spendAmount >= 10000");
        actionRule1.setScriptContent("setVipLevel('GOLD')");
        actionRule1.setPriority(15);
        actionRule1.setActive(true);

        // 操作规则2
        actionRule2 = new BusinessRuleMetadata();
        actionRule2.setId("rule-004");
        actionRule2.setName("ProductDiscountRule");
        actionRule2.setApiName("Product");
        actionRule2.setRuleType("ACTION");
        actionRule2.getTriggerEvents().add("CREATE");
        actionRule2.setExpression("category == 'ELECTRONICS'");
        actionRule2.setScriptContent("setDiscount(0.1)");
        actionRule2.setPriority(5);
        actionRule2.setActive(true);
    }

    @Test
    void testRegisterRule_Success() {
        // 创建一个新规则
        BusinessRuleMetadata newRule = new BusinessRuleMetadata();
        newRule.setId("rule-005");
        newRule.setName("TestRule");
        newRule.setApiName("TestEntity");
        newRule.setRuleType("VALIDATION");
        newRule.getTriggerEvents().add("CREATE");
        newRule.setExpression("field > 0");
        newRule.setActive(true);
        
        // 注册规则
        ruleRegistry.registerRule(newRule);
        
        // 验证规则是否成功注册
        BusinessRuleMetadata retrievedRule = ruleRegistry.getRuleById("rule-005");
        assertNotNull(retrievedRule);
        assertEquals("TestRule", retrievedRule.getName());
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
        BusinessRuleMetadata duplicateRule = new BusinessRuleMetadata();
        duplicateRule.setId("rule-001"); // 与已存在规则ID相同
        duplicateRule.setName("DuplicateRule");
        duplicateRule.setApiName("Customer");
        duplicateRule.setRuleType("VALIDATION");
        duplicateRule.getTriggerEvents().add("CREATE");
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
        BusinessRuleMetadata rule = ruleRegistry.getRuleById("rule-001");
        
        // 验证结果
        assertNotNull(rule);
        assertEquals("CustomerAgeValidation", rule.getName());
        assertEquals("Customer", rule.getApiName());
    }

    @Test
    void testGetRuleById_NonExistent() {
        // 获取不存在的规则
        BusinessRuleMetadata rule = ruleRegistry.getRuleById("non-existent-rule");
        
        // 验证结果
        assertNull(rule);
    }

    @Test
    void testGetRulesByEntity() {
        // 获取特定实体的所有规则
        List<BusinessRuleMetadata> customerRules = ruleRegistry.getRulesByEntity("Customer");
        List<BusinessRuleMetadata> productRules = ruleRegistry.getRulesByEntity("Product");
        List<BusinessRuleMetadata> nonExistentRules = ruleRegistry.getRulesByEntity("NonExistentEntity");
        
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
        List<BusinessRuleMetadata> customerCreateRules = ruleRegistry.getRulesByEventType("Customer", "CREATE");
        List<BusinessRuleMetadata> customerUpdateRules = ruleRegistry.getRulesByEventType("Customer", "UPDATE");
        List<BusinessRuleMetadata> nonExistentEntityRules = ruleRegistry.getRulesByEventType("NonExistentEntity", "CREATE");
        
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
        List<BusinessRuleMetadata> validationRules = ruleRegistry.getRulesByType("VALIDATION");
        List<BusinessRuleMetadata> actionRules = ruleRegistry.getRulesByType("ACTION");
        
        // 验证结果
        assertNotNull(validationRules);
        assertEquals(2, validationRules.size());
        
        assertNotNull(actionRules);
        assertEquals(2, actionRules.size());
    }

    @Test
    void testUpdateRule() {
        // 获取要更新的规则
        BusinessRuleMetadata ruleToUpdate = ruleRegistry.getRuleById("rule-001");
        assertNotNull(ruleToUpdate);
        
        // 修改规则属性
        ruleToUpdate.setExpression("age >= 21");
        ruleToUpdate.setErrorMessage("客户年龄必须满21岁");
        
        // 更新规则
        ruleRegistry.updateRule(ruleToUpdate);
        
        // 验证更新结果
        BusinessRuleMetadata updatedRule = ruleRegistry.getRuleById("rule-001");
        assertNotNull(updatedRule);
        assertEquals("age >= 21", updatedRule.getExpression());
        assertEquals("客户年龄必须满21岁", updatedRule.getErrorMessage());
    }

    @Test
    void testUpdateRule_NonExistent() {
        // 创建一个不存在的规则
        BusinessRuleMetadata nonExistentRule = new BusinessRuleMetadata();
        nonExistentRule.setId("non-existent-rule");
        nonExistentRule.setName("NonExistentRule");
        
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
        BusinessRuleMetadata deletedRule = ruleRegistry.getRuleById("rule-001");
        assertNull(deletedRule);
        
        // 验证规则列表中不再包含该规则
        List<BusinessRuleMetadata> customerRules = ruleRegistry.getRulesByEntity("Customer");
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
        BusinessRuleMetadata rule = ruleRegistry.getRuleById("rule-001");
        rule.setActive(false);
        ruleRegistry.updateRule(rule);
        
        // 激活规则
        ruleRegistry.activateRule("rule-001");
        
        // 验证规则已激活
        BusinessRuleMetadata activatedRule = ruleRegistry.getRuleById("rule-001");
        assertTrue(activatedRule.isActive());
    }

    @Test
    void testDeactivateRule() {
        // 停用规则
        ruleRegistry.deactivateRule("rule-001");
        
        // 验证规则已停用
        BusinessRuleMetadata deactivatedRule = ruleRegistry.getRuleById("rule-001");
        assertFalse(deactivatedRule.isActive());
        
        // 验证获取规则时不会返回非激活规则
        List<BusinessRuleMetadata> activeRules = ruleRegistry.getRulesByEventType("Customer", "CREATE");
        assertEquals(1, activeRules.size()); // 应该只有1个激活的规则
    }

    @Test
    void testGetRulesByEntityAndType() {
        // 获取特定实体和规则类型的规则
        List<BusinessRuleMetadata> customerValidationRules = ruleRegistry.getRulesByEntityAndType("Customer", "VALIDATION");
        List<BusinessRuleMetadata> customerActionRules = ruleRegistry.getRulesByEntityAndType("Customer", "ACTION");
        
        // 验证结果
        assertNotNull(customerValidationRules);
        assertEquals(2, customerValidationRules.size());
        
        assertNotNull(customerActionRules);
        assertEquals(1, customerActionRules.size());
    }
}