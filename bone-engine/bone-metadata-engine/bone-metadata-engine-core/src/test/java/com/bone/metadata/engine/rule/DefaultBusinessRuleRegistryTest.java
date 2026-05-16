package com.bone.metadata.engine.rule;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.metadata.engine.model.BusinessRuleMetadata;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

/** DefaultBusinessRuleRegistry测试类 提供全面的测试覆盖，确保业务规则注册表功能正确 */
class DefaultBusinessRuleRegistryTest {

  @InjectMocks private DefaultBusinessRuleRegistry ruleRegistry;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testRegistryNotNull() {
    // 测试注册表对象创建成功
    assertNotNull(ruleRegistry, "业务规则注册表不应该为null");
  }

  @Test
  void testRegisterRule_BasicFunctionality() {
    // 创建一个完整规则
    BusinessRuleMetadata rule = new BusinessRuleMetadata();
    rule.setId("test-rule");
    rule.setName("TestRule");
    rule.setApiName("TestEntity");
    rule.setRuleType("VALIDATION");

    // 注册规则
    ruleRegistry.registerRule(rule);

    // 获取并验证规则
    BusinessRuleMetadata retrieved = ruleRegistry.getRuleById("test-rule");
    assertNotNull(retrieved, "应该能够获取到已注册的规则");
    assertEquals("test-rule", retrieved.getId(), "规则ID不匹配");
    assertEquals("TestRule", retrieved.getName(), "规则名称不匹配");
    assertEquals("TestEntity", retrieved.getApiName(), "实体类型不匹配");
    assertEquals("VALIDATION", retrieved.getRuleType(), "规则类型不匹配");
  }

  @Test
  void testRegisterRule_NullRule() {
    // 测试注册null规则，应该优雅处理
    assertDoesNotThrow(() -> ruleRegistry.registerRule(null), "注册null规则不应该抛出异常");
  }

  @Test
  void testGetRuleById_NonExistent() {
    // 获取不存在的规则
    BusinessRuleMetadata rule = ruleRegistry.getRuleById("non-existent-rule");
    assertNull(rule, "获取不存在的规则应该返回null");
  }

  @Test
  void testGetRulesByEntity() {
    // 创建并注册多个规则
    BusinessRuleMetadata customerRule1 = new BusinessRuleMetadata();
    customerRule1.setId("customerRule1");
    customerRule1.setApiName("Customer");
    customerRule1.setName("客户规则1");
    ruleRegistry.registerRule(customerRule1);

    BusinessRuleMetadata customerRule2 = new BusinessRuleMetadata();
    customerRule2.setId("customerRule2");
    customerRule2.setApiName("Customer");
    customerRule2.setName("客户规则2");
    ruleRegistry.registerRule(customerRule2);

    BusinessRuleMetadata productRule = new BusinessRuleMetadata();
    productRule.setId("productRule");
    productRule.setApiName("Product");
    productRule.setName("产品规则");
    ruleRegistry.registerRule(productRule);

    // 按实体类型获取规则
    List<BusinessRuleMetadata> customerRules = ruleRegistry.getRulesByEntity("Customer");
    List<BusinessRuleMetadata> productRules = ruleRegistry.getRulesByEntity("Product");
    List<BusinessRuleMetadata> unknownRules = ruleRegistry.getRulesByEntity("UnknownEntity");

    // 验证结果
    assertNotNull(customerRules, "客户规则列表不应该为null");
    assertEquals(2, customerRules.size(), "客户规则数量不匹配");

    assertNotNull(productRules, "产品规则列表不应该为null");
    assertEquals(1, productRules.size(), "产品规则数量不匹配");

    assertNotNull(unknownRules, "未知实体规则列表不应该为null");
    assertTrue(unknownRules.isEmpty(), "未知实体应该返回空列表");
  }

  @Test
  void testDeleteRule_NonExistent() {
    // 删除不存在的规则，应该优雅处理
    assertDoesNotThrow(() -> ruleRegistry.deleteRule("non-existent-rule"), "删除不存在的规则不应该抛出异常");
  }

  @Test
  void testDeleteRule() {
    // 创建并注册规则
    BusinessRuleMetadata rule = new BusinessRuleMetadata();
    rule.setId("ruleToDelete");
    rule.setApiName("TestEntity");
    ruleRegistry.registerRule(rule);

    // 验证规则已注册
    assertNotNull(ruleRegistry.getRuleById("ruleToDelete"), "规则应该已成功注册");

    // 删除规则
    ruleRegistry.deleteRule("ruleToDelete");

    // 验证规则已移除
    assertNull(ruleRegistry.getRuleById("ruleToDelete"), "规则应该已成功移除");
  }

  @ParameterizedTest
  @CsvSource({
    "rule1, Customer, VALIDATION",
    "rule2, Order, TRANSFORMATION",
    "rule3, Product, ENRICHMENT"
  })
  void testRegisterAndRetrieveRulesWithDifferentParams(
      String ruleId, String entityType, String ruleType) {
    // 参数化测试，使用不同参数组合测试规则注册和检索
    BusinessRuleMetadata rule = new BusinessRuleMetadata();
    rule.setId(ruleId);
    rule.setApiName(entityType);
    rule.setRuleType(ruleType);

    ruleRegistry.registerRule(rule);

    BusinessRuleMetadata retrievedRule = ruleRegistry.getRuleById(ruleId);
    assertNotNull(retrievedRule, "规则应该成功注册和检索");
    assertEquals(ruleId, retrievedRule.getId(), "规则ID不匹配");
    assertEquals(entityType, retrievedRule.getApiName(), "实体类型不匹配");
    assertEquals(ruleType, retrievedRule.getRuleType(), "规则类型不匹配");
  }

  @Test
  void testRuleActivationDeactivation() {
    // 创建并注册规则
    BusinessRuleMetadata rule = new BusinessRuleMetadata();
    rule.setId("activationRule");
    rule.setApiName("TestEntity");
    rule.setActive(false); // 初始状态为非激活
    ruleRegistry.registerRule(rule);

    // 激活规则
    ruleRegistry.activateRule("activationRule");
    BusinessRuleMetadata activatedRule = ruleRegistry.getRuleById("activationRule");
    assertTrue(activatedRule.isActive(), "规则应该已被激活");

    // 停用规则
    ruleRegistry.deactivateRule("activationRule");
    BusinessRuleMetadata deactivatedRule = ruleRegistry.getRuleById("activationRule");
    assertFalse(deactivatedRule.isActive(), "规则应该已被停用");
  }

  @Test
  void testGetRulesByTypeAndEntityAndType() {
    // 创建并注册多种类型的规则
    BusinessRuleMetadata validationRule = new BusinessRuleMetadata();
    validationRule.setId("validationRule");
    validationRule.setApiName("Customer");
    validationRule.setRuleType("VALIDATION");
    ruleRegistry.registerRule(validationRule);

    BusinessRuleMetadata transformationRule = new BusinessRuleMetadata();
    transformationRule.setId("transformationRule");
    transformationRule.setApiName("Customer");
    transformationRule.setRuleType("TRANSFORMATION");
    ruleRegistry.registerRule(transformationRule);

    // 按类型获取规则
    List<BusinessRuleMetadata> validationRules = ruleRegistry.getRulesByType("VALIDATION");
    assertNotNull(validationRules, "验证规则列表不应该为null");
    assertEquals(1, validationRules.size(), "验证规则数量不匹配");

    // 按实体和类型获取规则
    List<BusinessRuleMetadata> customerValidationRules =
        ruleRegistry.getRulesByEntityAndType("Customer", "VALIDATION");
    assertNotNull(customerValidationRules, "客户验证规则列表不应该为null");
    assertEquals(1, customerValidationRules.size(), "客户验证规则数量不匹配");
    assertEquals("validationRule", customerValidationRules.get(0).getId(), "规则ID不匹配");
  }
}
