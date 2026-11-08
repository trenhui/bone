package com.bone.claim.domain.port.out;

import java.util.Objects;

/**
 * 发件箱派发的入参载体（ADR-008）。
 *
 * <p>刻意做成不可变 record：派发是「读出发件箱 → 投递」的单向动作，载体一旦在半路被改，重试台账里的 {@code event_type}
 * 与实际投递内容就会对不上，事后无法核对。字段与 {@code com.bone.claim.domain.model.event.DomainEventOutbox}
 * 的持久化列一一对应（{@code tenant_id} / {@code aggregate} / {@code aggregate_id} / {@code event_type} /
 * {@code payload}），由 {@code OutboxApplicationService} 从实体读出后构造。
 *
 * @param tenantId 租户 ID（跨租户中继时用于定位来源，非空）
 * @param aggregate 聚合名（如 {@code ClaimCase}），非空
 * @param aggregateId 聚合实例 ID，非空
 * @param eventType 领域事件类型（对应 {@code DomainEvent#eventType()}），非空
 * @param payload 事件载荷原始 JSON 文本（未在入站侧反序列化，保持与库内一致），非空
 */
public record OutboxRecord(
    Long tenantId, String aggregate, Long aggregateId, String eventType, String payload) {

  public OutboxRecord {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(aggregate, "aggregate");
    Objects.requireNonNull(aggregateId, "aggregateId");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(payload, "payload");
  }
}
