package com.bone.system.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import java.time.LocalDateTime;
import java.util.UUID;

/** 配置创建事件 */
public record ConfigCreatedEvent(
    /** 幂等事件 ID（R7：投影器按此去重）。 */
    String eventId,
    Long configId,
    String configKey,
    String configValue,
    String description,
    ConfigType configType,
    boolean encrypted,
    /** 操作人（R1：由应用层从 JWT 主体解析，不再硬编码 "admin"）。 */
    String operator,
    /** 操作人所属租户（R2：事件显式携带，脱离主链路也不丢租户）。 */
    Long tenantId,
    LocalDateTime eventTime)
    implements DomainEvent {

  public ConfigCreatedEvent(SystemConfig config, String operator, Long tenantId) {
    this(
        UUID.randomUUID().toString(),
        config.getId(),
        config.getConfigKey().value(),
        config.getConfigValue().value(),
        config.getDescription(),
        config.getConfigType(),
        config.isEncrypted(),
        operator,
        tenantId,
        LocalDateTime.now());
  }
}
