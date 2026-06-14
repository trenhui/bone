package com.bone.system.domain.model.alert.event;

import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;

/** 告警解决事件 */
public record AlertResolvedEvent(
    Long alertEventId, Long alertRuleId, String ruleName, LocalDateTime eventTime)
    implements DomainEvent {

  public AlertResolvedEvent(Long alertEventId, Long alertRuleId, String ruleName) {
    this(alertEventId, alertRuleId, ruleName, LocalDateTime.now());
  }
}
