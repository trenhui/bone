package com.bone.system.domain.model.alert.event;

import com.bone.core.domain.DomainEvent;
import com.bone.system.domain.model.alert.AlertRule;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;
import java.time.LocalDateTime;
import java.util.List;

/** 告警规则创建事件 */
public record AlertRuleCreatedEvent(
    Long alertRuleId,
    String name,
    String description,
    String metricName,
    double threshold,
    AlertLevel alertLevel,
    List<String> notificationChannels,
    LocalDateTime eventTime)
    implements DomainEvent {

  public AlertRuleCreatedEvent(AlertRule rule) {
    this(
        rule.getId(),
        rule.getName(),
        rule.getDescription(),
        rule.getMetricName().value(),
        rule.getThreshold().value(),
        rule.getAlertLevel(),
        rule.getNotificationChannels(),
        LocalDateTime.now());
  }
}
