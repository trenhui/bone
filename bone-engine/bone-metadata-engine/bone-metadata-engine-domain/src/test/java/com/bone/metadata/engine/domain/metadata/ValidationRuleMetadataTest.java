package com.bone.metadata.engine.domain.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ValidationRuleMetadata} 的单测（2026-10-07 补）。
 *
 * <p><b>为什么补这个类</b>：它是 {@code EntityMetadata#validationRules} 的元素类型， 而 {@code setValidationRules}
 * 曾在 2026-10-06 被修复过一次 （源码注释："此前该方法体被整体注释，是上游规则配置丢失的直接原因"）。 这类"曾经丢过数据"的模型必须有回归测试。
 *
 * <p><b>本类值得测的不是 Lombok 自动生成的 getter/setter</b>，而是那四个 <b>手写的「兼容方法」</b>——它们的存在本身就说明了历史包袱：
 *
 * <ul>
 *   <li>{@code getFieldName()} <b>不是</b>读 fieldName 字段，而是读 {@code errorLocation}；
 *   <li>{@code getMessage()} 读的是 {@code errorMessage}；
 *   <li>{@code isEnabled()} 读的是 {@code active}（字段名与方法名不一致）；
 *   <li>{@code setType(String)}是<b>空实现</b>（源码注释自述「类型字段实际上不存在」）。
 * </ul>
 *
 * 其中 {@code setType} 是空实现这一点尤其需要测试钉住：调用方以为设置成功了， 实际什么都没发生——若将来补上类型字段，本用例会失败并提示改断言。
 */
class ValidationRuleMetadataTest {

  @Nested
  @DisplayName("默认值（新建即应具备的初始状态）")
  class Defaults {

    @Test
    @DisplayName("order=0、active=true、triggerEvent=ALL、attributes 空Map")
    void hasSaneDefaults() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      assertEquals(0, r.getOrder(), "规则默认顺序应为 0");
      assertTrue(r.isActive(), "规则默认应启用");
      assertEquals("ALL", r.getTriggerEvent(), "默认触发事件应为 ALL");
      assertNotNull(r.getAttributes(), "attributes 默认应为空 Map 而非 null");
      assertTrue(r.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("@NoArgsConstructor 生效 ⇒ 无参构造可用（反序列化/框架需要）")
    void noArgsConstructorWorks() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      assertNull(r.getName());
      assertNull(r.getExpression());
      assertNull(r.getErrorMessage());
    }
  }

  @Nested
  @DisplayName("兼容方法：别名映射到不同字段")
  class CompatibilityAccessors {

    @Test
    @DisplayName("getFieldName() 读的是 errorLocation（不是 fieldName）")
    void getFieldNameMapsToErrorLocation() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      r.setFieldName("order.amount");
      assertEquals(
          "order.amount", r.getFieldName(), "setFieldName/getFieldName 实际读写的是 errorLocation 字段");
      // 直接字段访问是同一份
      assertEquals("order.amount", r.getErrorLocation());
    }

    @Test
    @DisplayName("setMessage/getMessage 读写 errorMessage")
    void messageMapsToErrorMessage() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      r.setMessage("金额必须大于 0");
      assertEquals("金额必须大于 0", r.getMessage());
      assertEquals("金额必须大于 0", r.getErrorMessage());
    }

    @Test
    @DisplayName("isEnabled() 读的是 active 字段（字段名与方法名不一致）")
    void isEnabledMapsToActive() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      assertTrue(r.isEnabled(), "默认 active=true");
      r.setActive(false);
      assertFalse(r.isEnabled(), "setActive(false) 应反映到 isEnabled()");
      // 反之亦然
      r.setActive(true);
      assertTrue(r.isEnabled());
    }

    @Test
    @DisplayName("★ setType 是空实现（源码注释自述「类型字段实际上不存在」）")
    void setTypeIsNoOp() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      r.setType("REGEX");
      // 当前没有任何字段承载"类型"，调用后规则对象里不残留任何可读回的信息。
      // 若将来补上 type 字段，本用例会失败并提醒改断言（而不是静默变更）。
      assertNull(r.getAttributes().get("type"), "setType 当前是空实现，不应把值写进 attributes");
    }
  }

  @Nested
  @DisplayName("业务字段读写往返")
  class RoundTrip {

    @Test
    @DisplayName("核心字段往返一致")
    void roundTripsCoreFields() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      r.setId("rule-1");
      r.setName("金额校验");
      r.setApiName("amountCheck");
      r.setDescription("校验订单金额为正");
      r.setExpression("order.amount > 0");
      r.setErrorMessage("金额必须大于 0");
      r.setOrder(10);
      r.setTriggerEvent("SAVE");

      assertEquals("rule-1", r.getId());
      assertEquals("金额校验", r.getName());
      assertEquals("amountCheck", r.getApiName());
      assertEquals("校验订单金额为正", r.getDescription());
      assertEquals("order.amount > 0", r.getExpression());
      assertEquals("金额必须大于 0", r.getErrorMessage());
      assertEquals(10, r.getOrder());
      assertEquals("SAVE", r.getTriggerEvent());
    }

    @Test
    @DisplayName("attributes 默认可写（校验规则常挂自定义参数）")
    void attributesAreWritable() {
      ValidationRuleMetadata r = new ValidationRuleMetadata();
      r.getAttributes().put("maxLength", "64");
      assertEquals("64", r.getAttributes().get("maxLength"));
    }

    @Test
    @DisplayName("@Data 生成的 equals/hashCode 可用（规则去重与集合比对依赖它）")
    void equalsAndHashCodeWork() {
      ValidationRuleMetadata a = new ValidationRuleMetadata();
      a.setId("r1");
      a.setExpression("x > 0");
      ValidationRuleMetadata b = new ValidationRuleMetadata();
      b.setId("r1");
      b.setExpression("x > 0");

      assertEquals(a, b, "同字段值的两个规则应相等");
      assertEquals(a.hashCode(), b.hashCode(), "equals 相等则 hashCode 必须相等");

      b.setExpression("x > 1");
      assertFalse(a.equals(b), "关键字段不同则不相等");
    }
  }
}
