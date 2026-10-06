package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.ValidationRuleMetadata;
import com.bone.metadata.engine.runtime.rule.RuleEngineAutoConfiguration;
import com.bone.metadata.engine.runtime.validation.ValidationResult;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自定义业务规则校验链路的守门测试（R0，2026-10-06）。
 *
 * <p><b>这条链路曾经 fail-open 且完全静默</b>：{@link ValidationEngine} 带 {@code @Component} 且有 3 个构造器， Spring
 * 只能选中无参构造器（无 {@code @Autowired}）⇒ {@code ruleEngine} 字段恒为 null ⇒ {@code validateCustomRules} 恒走
 * else 分支的 {@code fallbackCustomRuleValidation}，而该方法<b>曾是空实现</b>（只有一行 {@code LOGGER.debug}）⇒
 * 元数据里配置了自定义业务规则也照样放行，日志里没有任何异常线索。
 *
 * <p><b>为什么这类缺陷能长期存活</b>：它不抛异常、不影响启动、单测全绿（没有测试断言"规则没生效"）， 唯一的表征是"配了规则好像没效果"—— 而这在业务上很容易被误解成"规则写错了"。
 *
 * <p><b>本类的职责</b>：把「接线必须成立」和「接线不成立时必须 fail-closed」两件事变成可执行断言， 让它无法再靠"看起来没报错"蒙混过关。
 */
class CustomBusinessRuleWiringTest {

  /** 反射读取私有字段：这些字段没有 getter，测试需要直接验证接线结果。 */
  private static Object readField(Object target, String name) throws Exception {
    Field f = ValidationEngine.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  @Test
  @DisplayName("接线 bean 在构造时就注入 ruleEngine（而非仅持有引用）")
  void wiringBeanActuallyInjectsRuleEngine() throws Exception {
    ValidationEngine validationEngine = new ValidationEngine();
    RuleEngine ruleEngine = new RuleEngine(new ExpressionEngine(), new MetadataEngine());

    assertNull(readField(validationEngine, "ruleEngine"), "前置：接线前 ruleEngine 应为 null");

    // 模拟 RuleEngineAutoConfiguration#ruleEngineValidationEngineWiring 的行为
    new RuleEngineAutoConfiguration.RuleEngineValidationEngineWiring(validationEngine, ruleEngine);

    assertSame(ruleEngine, readField(validationEngine, "ruleEngine"), "接线 bean 必须真正调用 setter");
  }

  @Test
  @DisplayName("接线 bean 在宿主无 ValidationEngine bean 时不抛异常（不阻断启动）")
  void wiringBeanToleratesMissingValidationEngine() {
    // ObjectProvider.getIfAvailable() 返回 null 的路径：接线 bean 必须静默跳过而不是炸掉上下文
    RuleEngine ruleEngine = new RuleEngine(new ExpressionEngine(), new MetadataEngine());
    RuleEngineAutoConfiguration.RuleEngineValidationEngineWiring wiring =
        new RuleEngineAutoConfiguration.RuleEngineValidationEngineWiring(null, ruleEngine);

    assertNotNull(wiring, "宿主未装 starter 时也应返回接线记录，只是 validationEngine 为 null");
    assertNull(wiring.validationEngine());
    assertNotNull(wiring.ruleEngine());
  }

  @Test
  @DisplayName("fail-closed：ruleEngine 为 null 且实体声明了自定义规则时必须产生错误而非静默放行")
  void fallbackFailsClosedWhenRuleEngineUnavailable() throws Exception {
    ValidationEngine engine = new ValidationEngine();
    assertNull(readField(engine, "ruleEngine"), "本用例针对的正是接线断裂场景");

    EntityMetadata metadata = entityWithRule("Order", "amount > 0");
    Map<String, Object> entityData = new HashMap<>();
    entityData.put("amount", -1);

    ValidationResult result = invokeValidateCustomRules(engine, metadata, entityData);

    assertFalse(result.isValid(), "接线断裂时不得判定为通过（这是本缺陷存在的原始表征）");
    assertTrue(hasGeneralError(result), "必须写入一条 error 让调用方能拒绝：fail-closed 的关键在报错而非抛异常");
  }

  @Test
  @DisplayName("fail-closed 不误伤：实体未声明自定义规则时应正常放行")
  void fallbackPassesWhenNoCustomRulesDeclared() throws Exception {
    ValidationEngine engine = new ValidationEngine();
    EntityMetadata metadata = new EntityMetadata();
    metadata.setApiName("Order");

    ValidationResult result = invokeValidateCustomRules(engine, metadata, new HashMap<>());

    assertTrue(result.isValid(), "没有配置规则就不该报错，否则上线即全量拒绝");
    assertFalse(hasGeneralError(result));
  }

  /** fail-open 的判据是"配了规则却静默通过"。本用例把判据钉死成可执行断言：<b>存在规则 + 校验不通过</b> 的组合 在接线断裂时必须暴露为错误。 */
  @Test
  @DisplayName("回归守卫：接线断裂 + 配了规则 ⇒ 绝不能返回 valid（防止有人把 fail-closed 改回空实现）")
  void fallbackNeverSilentlyPassesWithDeclaredRules() throws Exception {
    ValidationEngine engine = new ValidationEngine();
    EntityMetadata metadata = entityWithRule("Order", "amount > 0");

    ValidationResult result = invokeValidateCustomRules(engine, metadata, new HashMap<>());

    assertFalse(result.isValid(), "这条断言就是防止 fallbackCustomRuleValidation 再次退化为空实现（fail-open）的哨兵");
  }

  /**
   * 数据层守卫：{@code EntityMetadata} 曾把 {@code validationRules} 声明为 {@code Map} 而 getter 契约返回 {@code
   * List}，导致 getter 被写成"恒返回空列表"、setter 整体注释 —— 规则<b>存不进去</b>。 没有这条断言，接线修好了也依然恒走 {@code
   * rules.isEmpty()} 的早退分支。
   */
  @Test
  @DisplayName("数据层：setValidationRules/getValidationRules 必须真正往返（规则曾存不进去）")
  void validationRulesRoundTripOnEntityMetadata() {
    EntityMetadata metadata = new EntityMetadata();
    metadata.setApiName("Order");
    assertTrue(metadata.getValidationRules().isEmpty(), "新实体不应自带规则");

    ValidationRuleMetadata rule = new ValidationRuleMetadata();
    rule.setName("amountPositive");
    rule.setFieldName("amount");
    rule.setExpression("amount > 0");
    rule.setMessage("金额必须大于 0");
    metadata.setValidationRules(new ArrayList<>(List.of(rule)));

    List<Object> stored = metadata.getValidationRules();
    assertEquals(1, stored.size(), "规则必须真的存下来（这正是原缺陷：setter 是空实现）");
    assertSame(rule, stored.get(0));
    assertEquals("amount > 0", ((ValidationRuleMetadata) stored.get(0)).getExpression());
  }

  /** 造一个带一条自定义规则的实体元数据。 */
  private static EntityMetadata entityWithRule(String apiName, String expression) {
    EntityMetadata metadata = new EntityMetadata();
    metadata.setApiName(apiName);
    ValidationRuleMetadata rule = new ValidationRuleMetadata();
    rule.setName("rule1");
    rule.setFieldName("amount");
    rule.setExpression(expression);
    rule.setMessage("校验不通过");
    metadata.setValidationRules(new ArrayList<>(List.of(rule)));
    return metadata;
  }

  private static ValidationResult invokeValidateCustomRules(
      ValidationEngine engine, EntityMetadata metadata, Map<String, Object> data) throws Exception {
    java.lang.reflect.Method m =
        ValidationEngine.class.getDeclaredMethod(
            "validateCustomRules", EntityMetadata.class, Map.class, ValidationResult.class);
    m.setAccessible(true);
    ValidationResult result = ValidationResult.success();
    m.invoke(engine, metadata, data, result);
    return result;
  }

  private static boolean hasGeneralError(ValidationResult result) {
    return result.getErrors() != null
        && result.getErrors().stream().anyMatch(e -> "general".equals(e.getFieldPath()));
  }
}
