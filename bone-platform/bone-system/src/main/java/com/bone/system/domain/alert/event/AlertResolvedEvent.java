package com.bone.system.domain.alert.event;

import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;

/** 告警解决事件 */
public record AlertResolvedEvent(
    Long alertRecordId, Long alertRuleId, String ruleName, LocalDateTime eventTime)
    implements DomainEvent {

  public AlertResolvedEvent(Long alertRecordId, Long alertRuleId, String ruleName) {
    this(alertRecordId, alertRuleId, ruleName, LocalDateTime.now());
  }
}
