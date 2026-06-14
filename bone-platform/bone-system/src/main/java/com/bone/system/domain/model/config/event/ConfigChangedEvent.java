package com.bone.system.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;

public record ConfigChangedEvent(
    Long configId,
    String configKey,
    String oldValue,
    String newValue,
    String operator,
    LocalDateTime eventTime)
    implements DomainEvent {

  public ConfigChangedEvent(
      Long configId, String configKey, String oldValue, String newValue, String operator) {
    this(configId, configKey, oldValue, newValue, operator, LocalDateTime.now());
  }
}
