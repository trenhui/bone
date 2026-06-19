package com.bone.blueprint.domain.outbox;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
