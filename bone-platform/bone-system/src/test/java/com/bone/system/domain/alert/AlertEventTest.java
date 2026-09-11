package com.bone.system.domain.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.system.domain.model.alert.event.AlertResolvedEvent;
import com.bone.system.domain.model.alert.vo.AlertLevel;
import com.bone.system.domain.model.alert.vo.AlertStatus;
import com.bone.system.domain.model.alert.vo.MetricName;
import org.junit.jupiter.api.Test;

/** {@link AlertEvent} 纯单测：告警事件 TRIGGERED → RESOLVED 生命周期与级别校验（无容器）。 */
class AlertEventTest {

  @Test
  void testCreateStartsAtTriggered() {
    AlertEvent event =
        AlertEvent.create(
            1L, 10L, "CPU 高占用", "cpu.usage", 95.0, 90.0, AlertLevel.WARNING, "CPU 使用率 95%");

    assertEquals(10L, event.getAlertRuleId());
    assertEquals(MetricName.of("cpu.usage").value(), event.getMetricName());
    assertEquals(95.0, event.getActualValue(), 0.0001);
    assertEquals(90.0, event.getThreshold(), 0.0001);
    assertEquals(AlertStatus.TRIGGERED, event.getStatus());
    assertNotNull(event.getCreatedAt());
  }

  @Test
  void testResolveMovesToResolved() {
    AlertEvent event =
        AlertEvent.create(
            2L, 10L, "CPU 高占用", "cpu.usage", 95.0, 90.0, AlertLevel.WARNING, "CPU 超限");
    assertEquals(0, event.getDomainEvents().size());

    event.resolve();

    assertEquals(AlertStatus.RESOLVED, event.getStatus());
    assertNotNull(event.getResolveTime());
    assertEquals(1, event.getDomainEvents().size());
    assertInstanceOf(AlertResolvedEvent.class, event.getDomainEvents().get(0));
  }

  @Test
  void testInvalidAlertLevelRejected() {
    assertThrows(DomainException.class, () -> AlertLevel.fromString("FATAL"));
  }
}
