package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbox（发件箱）记录——**基础设施持久化模型，非业务聚合**。
 *
 * <p><b>为何在 infrastructure 而非 domain</b>：本类没有业务不变量，仅含 PENDING → SENT/FAILED 的
 * <strong>技术投递状态</strong>，本质是「消息可靠投递」的中间态。放在领域层会把消息中间件这一技术设施 概念混入业务领域，并占用领域包命名空间（易被误读为订单领域模型）。原
 * {@code domain/outbox} 包已废弃。
 *
 * <p><b>为何仍继承 {@code AggregateRoot}</b>：仅为复用 bone-metadata-sdk 的持久化与主键回填机制，
 * 属框架适配要求；它<strong>不是</strong>业务意义上的聚合根——无领域行为、无领域事件。
 *
 * <p>多模块都需要 Outbox 时，应上抽为平台级公共组件，而非各模块复制本类。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_outbox")
public class OrderOutboxRecord extends AggregateRoot<Long> {

  private Long tenantId;
  private String eventId;
  private String eventType;
  private String topic;
  private String partitionKey;
  private String envelopeJson;
  private OutboxStatus status;
  private Integer retryCount;
  private Instant sentAt;

  public static OrderOutboxRecord pending(
      Long id,
      Long tenantId,
      String eventId,
      String eventType,
      String topic,
      String partitionKey,
      String envelopeJson) {
    OrderOutboxRecord record = new OrderOutboxRecord();
    record.setId(id);
    record.tenantId = tenantId != null ? tenantId : 0L;
    record.eventId = eventId;
    record.eventType = eventType;
    record.topic = topic;
    record.partitionKey = partitionKey;
    record.envelopeJson = envelopeJson;
    record.status = OutboxStatus.PENDING;
    record.retryCount = 0;
    return record;
  }

  public void markSent() {
    this.status = OutboxStatus.SENT;
    this.sentAt = Instant.now();
  }

  public void markFailed() {
    this.status = OutboxStatus.FAILED;
  }

  public void incrementRetry() {
    this.retryCount = (retryCount == null ? 0 : retryCount) + 1;
  }
}
