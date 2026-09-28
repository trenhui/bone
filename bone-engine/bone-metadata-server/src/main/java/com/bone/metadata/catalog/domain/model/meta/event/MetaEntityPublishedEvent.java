package com.bone.metadata.catalog.domain.model.meta.event;

import com.bone.core.domain.DomainEvent;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import java.time.LocalDateTime;

/**
 * 实体发布事件（doc2a §337 主流程步骤⑤「发布领域事件」落地，F2）。
 *
 * <p>发布用例（单条 {@code publishEntity} / 批量 {@code batchPublishEntities}）在「状态落库 + 缓存失效 + RUNTIME 物理
 * align」全部成功后经 {@code DomainEventPublisher} 发出；align 失败即发布失败，不产生事件。
 *
 * <p>消费方：跨模块集成事件与消费登记（G8 {@code meta_model_consumer}，P1 [Target]）落地前，本事件是 「实体已发布」事实的唯一进程内出口，下游（索引同步
 * / 采用率统计 / engine 通知）经 {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅，当前无订阅方属预期（发布即契约，不是缺陷）。
 */
public record MetaEntityPublishedEvent(
    Long entityId,
    Long tenantId,
    String entityCode,
    String tableName,
    Integer deliveryMode,
    Integer version,
    LocalDateTime eventTime)
    implements DomainEvent {

  public MetaEntityPublishedEvent(MetaEntity entity) {
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
