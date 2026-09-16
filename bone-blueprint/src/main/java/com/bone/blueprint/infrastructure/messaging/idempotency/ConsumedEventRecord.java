package com.bone.blueprint.infrastructure.messaging.idempotency;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 消费端幂等去重记录——<strong>基础设施持久化模型，非业务聚合</strong>。
 *
 * <p>与 {@code OrderOutboxRecord} 同构：继承 {@code AggregateRoot} 仅为复用 bone-metadata-sdk 的持久化与主键
 * 机制，本身无领域不变量（唯一约束表达的业务规则是「同一消费组对同一 eventId 只处理一次」）。
 *
 * <p>表 {@code bp_processed_event} 的 {@code (consumer_group, event_id)}
 * 唯一键是幂等的<strong>唯一执行机制</strong>： 并发重复投递时只有一个插入能成功，另一个抛唯一键冲突并被翻译为「已处理」。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_processed_event")
public class ConsumedEventRecord extends AggregateRoot<Long> {

  private Long tenantId;
  private String consumerGroup;
  private String eventId;
  private String topic;
  private Instant processedAt;

  public static ConsumedEventRecord claimed(
      String consumerGroup, String topic, String eventId, long tenantId) {
    ConsumedEventRecord record = new ConsumedEventRecord();
    record.setId(DistributedIdGenerator.generateLongId());
    record.tenantId = tenantId;
    record.consumerGroup = consumerGroup;
    record.eventId = eventId;
    record.topic = topic;
    record.processedAt = Instant.now();
    return record;
  }
}
