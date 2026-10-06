package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  private Instant claimedAt;
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

  /**
   * 抢占：PENDING → PROCESSING（CAS 由调用方经 SDK updateByCriteria 的 WHERE status='PENDING' 保证）。
   *
   * <p><b>claimedAt 的用途</b>：多实例部署下，卡死自愈（reconcileStuck）必须区分「别的实例正在投递」与 「上次崩溃遗留」——判据是抢占时刻距今是否超过
   * stuckTimeoutMs。没有该字段时，自愈只能无条件回收 全部 PROCESSING，会把其他实例正在投递的记录翻回 PENDING 造成双投（ADR-0021 §卡死自愈）。
   *
   * <p>截断到微秒：与 DDL 的 DATETIME(3)（毫秒）精度对齐，避免写入端纳秒精度与读回端毫秒精度的 往返差异让阈值判定抖动。
   */
  public void markClaimed() {
    this.status = OutboxStatus.PROCESSING;
    this.claimedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  /** 可重试失败：PROCESSING → PENDING，交还下一轮 relay 抢占。 */
  public void markRetry() {
    this.status = OutboxStatus.PENDING;
    // 残留的 claimedAt 无需清空：卡死判据只看 status=PROCESSING，重新抢占时 markClaimed 会覆写。
  }

  public void incrementRetry() {
    this.retryCount = (retryCount == null ? 0 : retryCount) + 1;
  }
}
