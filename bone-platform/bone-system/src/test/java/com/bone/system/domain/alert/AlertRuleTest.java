package com.bone.system.domain.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import com.bone.system.domain.alert.event.AlertRuleCreatedEvent;
import com.bone.system.domain.alert.event.AlertRuleUpdatedEvent;
import com.bone.system.domain.alert.vo.AlertLevel;
import com.bone.system.domain.alert.vo.MetricName;
import com.bone.system.domain.alert.vo.Threshold;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link AlertRule} 纯单测：创建即启用并发布事件、阈值判定与启停语义、更新契约（无容器）。 */
class AlertRuleTest {

  private AlertRule createRule() {
    return AlertRule.create(
        1L,
        "CPU 高占用",
        "CPU 使用率超过 90%",
        MetricName.of("cpu.usage"),
        Threshold.of(90.0),
        AlertLevel.WARNING,
        List.of("email", "sms"));
  }

  @Test
  void testCreateEnablesRuleAndPublishesEvent() {
    AlertRule rule = createRule();

    assertTrue(rule.isEnabled());
    assertEquals("CPU 高占用", rule.getName());
    assertEquals(MetricName.of("cpu.usage"), rule.getMetricName());
    assertEquals(List.of("email", "sms"), rule.getNotificationChannels());
    assertEquals(1, rule.getDomainEvents().size());
    assertInstanceOf(AlertRuleCreatedEvent.class, rule.getDomainEvents().get(0));
  }

  @Test
  void testShouldTriggerAboveOrAtThreshold() {
    AlertRule rule = createRule();

    assertTrue(rule.shouldTrigger(95.0));
    // 等于阈值也触发
    assertTrue(rule.shouldTrigger(90.0));
    // 低于阈值不触发
    assertFalse(rule.shouldTrigger(89.9));
  }

  @Test
  void testDisabledRuleNeverTriggers() {
    AlertRule rule = createRule();
    rule.disable();
    assertFalse(rule.isEnabled());
    assertFalse(rule.shouldTrigger(100.0));

    rule.enable();
    assertTrue(rule.isEnabled());
    assertTrue(rule.shouldTrigger(100.0));
  }

  @Test
  void testUpdateOverridesRuleAndPublishesEvent() {
    AlertRule rule = createRule();
    rule.clearDomainEvents();

    rule.update(
        "CPU 高占用（新阈值）", "CPU 使用率超过 95%", Threshold.of(95.0), AlertLevel.CRITICAL, List.of("sms"));

    assertEquals("CPU 高占用（新阈值）", rule.getName());
    assertEquals(Threshold.of(95.0), rule.getThreshold());
    assertEquals(AlertLevel.CRITICAL, rule.getAlertLevel());
    assertEquals(1, rule.getDomainEvents().size());
    assertInstanceOf(AlertRuleUpdatedEvent.class, rule.getDomainEvents().get(0));
  }

  @Test
  void testBlankMetricNameRejected() {
    assertThrows(DomainException.class, () -> MetricName.of("  "));
  }
}
