package com.bone.integration.infrastructure.messaging.outbox;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbox 待发记录（int_outbox）——<strong>技术对象</strong>，不是业务聚合。
 *
 * <p>只有 PENDING/SENT/FAILED 三种投递状态，无领域不变量。放入领域层会把消息中间件概念混入业务领域， 故 v4.6 起由 {@code domain/outbox}
 * 下沉到基础设施层（与 bone-blueprint {@code OrderOutboxRecord} 同一约定）。
 *
 * <p><b>为何继承聚合根基类</b>：复用 SDK 持久化能力（{@code Repository<T,ID>} + {@code @Table} 映射），
 * 语义上是「持久化记录」而非领域聚合。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_outbox")
public class IntegrationOutboxRecord extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long tenantId;
  private String eventId;
  private String eventType;
  private String topic;
  private String partitionKey;
  private String envelopeJson;
  private OutboxStatus status;
  private Integer retryCount;
  private Instant sentAt;

  public static IntegrationOutboxRecord pending(
      Long id,
      Long tenantId,
      String eventId,
      String eventType,
      String topic,
      String partitionKey,
      String envelopeJson) {
    IntegrationOutboxRecord record = new IntegrationOutboxRecord();
    record.id = id;
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
