package com.bone.system.domain.model.alert.event;

import com.bone.core.domain.DomainEvent;
import com.bone.system.domain.model.alert.vo.AlertLevel;
import java.time.LocalDateTime;

/** 告警触发事件 */
public record AlertTriggeredEvent(
    Long alertEventId,
    Long alertRuleId,
    String ruleName,
    String metricName,
    double actualValue,
    double threshold,
    AlertLevel alertLevel,
    String message,
    LocalDateTime eventTime)
    implements DomainEvent {

  public AlertTriggeredEvent(
      Long alertEventId,
      Long alertRuleId,
      String ruleName,
      String metricName,
      double actualValue,
      double threshold,
      AlertLevel alertLevel,
      String message) {
    this(
        alertEventId,
        alertRuleId,
        ruleName,
        metricName,
        actualValue,
        threshold,
        alertLevel,
        message,
        LocalDateTime.now());
  }
}
