package com.bone.masterdata.domain.model.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.quality.event.DataQualityRuleCreatedEvent;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import org.junit.jupiter.api.Test;

/** {@link DataQualityRule} 纯单测：默认启用、更新覆盖与规则名约束（无容器）。 */
class DataQualityRuleTest {

  private DataQualityRule createRule() {
    return DataQualityRule.create(
        1L, 100L, RuleName.of("非空校验"), "NOT_NULL", "field != null", RuleSeverity.HIGH, "必填检查");
  }

  @Test
  void testCreateDefaultsToEnabledAndPublishesEvent() {
    DataQualityRule rule = createRule();

    assertTrue(rule.isEnabled());
    assertEquals(100L, rule.getMasterDataEntityId());
    assertEquals(RuleSeverity.HIGH, rule.getSeverity());
    assertEquals(1, rule.getDomainEvents().size());
    assertInstanceOf(DataQualityRuleCreatedEvent.class, rule.getDomainEvents().get(0));
  }

  @Test
  void testUpdateOverridesRuleProfile() {
    DataQualityRule rule = createRule();

    rule.update(RuleName.of("唯一性校验"), "UNIQUE", "count(field)==1", RuleSeverity.CRITICAL, "查重");

    assertEquals(RuleName.of("唯一性校验"), rule.getName());
    assertEquals("UNIQUE", rule.getType());
    assertEquals(RuleSeverity.CRITICAL, rule.getSeverity());
  }

  @Test
  void testRuleNameRejectsBlankOrOutOfRangeLength() {
    assertThrows(DomainException.class, () -> RuleName.of(" "));
    assertThrows(DomainException.class, () -> RuleName.of("x"));
    assertThrows(DomainException.class, () -> RuleName.of("x".repeat(51)));
  }
}
