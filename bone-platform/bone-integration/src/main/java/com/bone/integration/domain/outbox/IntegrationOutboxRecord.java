package com.bone.integration.domain.outbox;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_outbox")
public class IntegrationOutboxRecord extends AggregateRoot<Long> {

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
