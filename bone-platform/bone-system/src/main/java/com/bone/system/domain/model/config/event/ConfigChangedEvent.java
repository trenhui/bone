package com.bone.system.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConfigChangedEvent(
    /** 幂等事件 ID（R7：投影器按此去重）。 */
    String eventId,
    Long configId,
    String configKey,
    String oldValue,
    String newValue,
    /** 操作人（已非硬编码，由应用层从 JWT 主体解析）。 */
    String operator,
    /** 操作人所属租户（R2：事件显式携带）。 */
    Long tenantId,
    LocalDateTime eventTime)
    implements DomainEvent {

  public ConfigChangedEvent(
      Long configId,
      String configKey,
      String oldValue,
      String newValue,
      String operator,
      Long tenantId) {
    this(
        UUID.randomUUID().toString(),
        configId,
        configKey,
        oldValue,
        newValue,
        operator,
        tenantId,
        LocalDateTime.now());
  }
}
