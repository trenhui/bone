package com.bone.metadata.catalog.domain.model.meta.event;

import com.bone.core.domain.DomainEvent;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import java.time.LocalDateTime;

/**
 * 实体归档事件（P0-2 已发布实体软下线）。
 *
 * <p>归档用例（单条 {@code archiveEntity}）在「状态落库(ARCHIVED) + 缓存失效」成功后经 {@code DomainEventPublisher} 发出。
 * 消费方：运行时经 {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅，使已发布快照随归档失效， 避免缓存 TTL
 * 内仍接受已下线实体的写入（RC-002 同源）。 当前无订阅方属预期（归档即契约）。
 */
public record MetaEntityArchivedEvent(
    Long entityId,
    Long tenantId,
    String entityCode,
    String tableName,
    Integer deliveryMode,
    Integer version,
    LocalDateTime eventTime)
    implements DomainEvent {

  public MetaEntityArchivedEvent(MetaEntity entity) {
    this(
        entity.getId(),
        entity.getTenantId(),
        entity.getCode(),
        entity.getTableName(),
        entity.getDeliveryMode(),
        entity.getVersion(),
        LocalDateTime.now());
  }
}
