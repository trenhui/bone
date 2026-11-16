package com.bone.masterdata.domain.model.dispatch;

import com.bone.core.exception.DomainException;
import java.time.Instant;

/**
 * 主数据分发 Outbox 事件信封（版本化、幂等、PII 最小化）。
 *
 * <p>终局约束：EntitySubscription 只登记订阅授权，不负责投递；投递由平台 Outbox 中继完成， 消费侧按 idempotencyKey 去重。事件只携带
 * payloadHash，不回传字段原文， 避免供应商联系人 / 证件号等 PII 进入消息总线与日志。
 */
public final class MasterDataRecordDispatchedEvent {

  private final String eventId;
  private final long tenantId;
  private final String partyId;
  private final long recordVersion;
  private final String eventType;
  private final int schemaVersion;
  private final Instant occurredAt;
  private final String payloadHash;

  private MasterDataRecordDispatchedEvent(
      String eventId,
      long tenantId,
      String partyId,
      long recordVersion,
      String eventType,
      int schemaVersion,
      Instant occurredAt,
      String payloadHash) {
    this.eventId = eventId;
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.recordVersion = recordVersion;
    this.eventType = eventType;
    this.schemaVersion = schemaVersion;
    this.occurredAt = occurredAt;
    this.payloadHash = payloadHash;
  }

  public static MasterDataRecordDispatchedEvent of(
      String eventId,
      long tenantId,
      String partyId,
      long recordVersion,
      String eventType,
      int schemaVersion,
      Instant occurredAt,
      String payloadHash) {
    if (isBlank(eventId)
        || isBlank(partyId)
        || isBlank(eventType)
        || isBlank(payloadHash)
        || occurredAt == null) {
      throw new DomainException("分发事件缺少必填信封字段");
    }
    if (recordVersion <= 0 || schemaVersion <= 0) {
      throw new DomainException("分发事件版本必须为正数");
    }
    return new MasterDataRecordDispatchedEvent(
        eventId,
        tenantId,
        partyId,
        recordVersion,
        eventType,
        schemaVersion,
        occurredAt,
        payloadHash);
  }

  /** 消费侧幂等键：同一租户/主体/版本/事件类型只处理一次。 */
  public String idempotencyKey() {
    return tenantId + "|" + partyId + "|" + recordVersion + "|" + eventType;
  }

  /** 版本单调判定：仅同租户同主体可比较，防止跨域误用。 */
  public boolean isNewerThan(MasterDataRecordDispatchedEvent other) {
    if (other == null || other.tenantId != tenantId || !other.partyId.equals(partyId)) {
      throw new DomainException("仅同租户同主体的分发事件可比较版本");
    }
    return recordVersion > other.recordVersion;
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }

  public String getEventId() {
    return eventId;
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getPartyId() {
    return partyId;
  }

  public long getRecordVersion() {
    return recordVersion;
  }

  public String getEventType() {
    return eventType;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getPayloadHash() {
    return payloadHash;
  }
}
