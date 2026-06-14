package com.bone.masterdata.domain.model.quality.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.quality.DataQualityRule;

public record DataQualityRuleCreatedEvent(Long ruleId, Long entityId, String ruleName)
    implements DomainEvent {
  public DataQualityRuleCreatedEvent(DataQualityRule rule) {
    this(rule.getId(), rule.getMasterDataEntityId(), rule.getName().value());
  }
}
