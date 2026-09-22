package com.bone.system.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import java.time.LocalDateTime;

/** 配置创建事件 */
public record ConfigCreatedEvent(
    Long configId,
    String configKey,
    String configValue,
    String description,
    ConfigType configType,
    boolean encrypted,
    LocalDateTime eventTime)
    implements DomainEvent {

  public ConfigCreatedEvent(SystemConfig config) {
    this(
        config.getId(),
        config.getConfigKey().value(),
        config.getConfigValue().value(),
        config.getDescription(),
        config.getConfigType(),
        config.isEncrypted(),
        LocalDateTime.now());
  }
}
