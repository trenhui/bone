package com.bone.claim.domain.model.event;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.Getter;

/**
 * 领域事件发件箱（ADR-008「资金事件零丢失 100%」兜底）。
 *
 * <p>写入时机：聚合发布领域事件时，除进程内 afterCommit 即时投递外，同步落一笔 {@code PENDING} 记录作为兜底； {@code
 * OutboxRelayJob}（adapter.schedule，ADR-0030 跨租户扫描）定时把 {@code PENDING} 记录重投，成功刷 {@code SENT}， 失败按
 * {@code retry_count}/{@code next_retry_at} 退避，超上限置 {@code FAILED}（人工兜底）。
 *
 * <p>{@code version} 乐观锁保证同一记录不会被两个中继实例并发重发（集群多实例安全）。
 */
@Getter
@Table("t_domain_event_outbox")
public class DomainEventOutbox extends TenantAbstractEntity<Long> {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_SENT = "SENT";
  public static final String STATUS_FAILED = "FAILED";

  @Column(name = "aggregate", nullable = false)
  private String aggregate;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  /** 事件载荷（JSON 文本）。 */
  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "status", nullable = false)
  private String status = STATUS_PENDING;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  DomainEventOutbox() {}

  private DomainEventOutbox(
      Long tenantId, String aggregate, Long aggregateId, String eventType, String payload) {
    setTenantId(tenantId);
    this.aggregate = aggregate;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = STATUS_PENDING;
    this.retryCount = 0;
    // 审计列由 SDK 映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，append 时必须显式赋值
    java.util.Date now = new java.util.Date();
    setCreatedAt(now);
    setUpdatedAt(now);
  }

  public static DomainEventOutbox of(
      Long tenantId, String aggregate, Long aggregateId, String eventType, String payload) {
    return new DomainEventOutbox(tenantId, aggregate, aggregateId, eventType, payload);
  }

  /** 派发成功：刷为 SENT。 */
  public void markSent() {
    this.status = STATUS_SENT;
    setUpdatedAt(new java.util.Date());
  }

  /**
   * 派发失败：累加重试次数；未超上限则保持 PENDING 并设定下次重试时间，否则置 FAILED（人工兜底）。
   *
   * @param maxRetry 最大重试次数
   * @param nextRetryAt 下次重试时间（仅当未达上限时生效）
   */
  public void markFailed(int maxRetry, Instant nextRetryAt) {
    this.retryCount = this.retryCount + 1;
    if (this.retryCount >= maxRetry) {
      this.status = STATUS_FAILED;
      this.nextRetryAt = null;
    } else {
      this.status = STATUS_PENDING;
      this.nextRetryAt = nextRetryAt;
    }
    setUpdatedAt(new java.util.Date());
  }
}
