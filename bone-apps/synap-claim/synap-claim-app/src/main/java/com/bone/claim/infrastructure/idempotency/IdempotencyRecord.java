package com.bone.claim.infrastructure.idempotency;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 幂等快照记录——<strong>基础设施持久化模型，非业务聚合</strong>。
 *
 * <p>与 blueprint {@code IdempotencyRecord} 同构：继承 {@code AggregateRoot} 仅为复用 bone-metadata-sdk
 * 的持久化与主键机制，无领域不变量。存储实现配合 framework 的 {@code IdempotencyService} （{@code
 * IdempotencyAutoConfiguration} 以「存在 {@code IdempotencyStore} 实现 Bean」为注册条件）。
 *
 * <p>表 {@code claim_idempotency_record} 的唯一键 {@code (tenant_id, scope_key)} 保证同一幂等键只有一行； {@code
 * scope_key} 已包含用户与路径，不同用户/端点互不干扰。时间字段用 UTC 语义 {@code Instant} （依赖 SDK 读路径 {@code
 * InstantConverter}，同 blueprint 口径）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("claim_idempotency_record")
public class IdempotencyRecord extends AggregateRoot<Long> {

  private Long tenantId;
  private String scopeKey;
  private String requestFingerprint;
  private String snapshotJson;
  private Instant expiresAt;

  public static IdempotencyRecord of(
      long tenantId,
      String scopeKey,
      String requestFingerprint,
      String snapshotJson,
      Instant expiresAt) {
    IdempotencyRecord record = new IdempotencyRecord();
    record.setId(DistributedIdGenerator.generateLongId());
    record.tenantId = tenantId;
    record.scopeKey = scopeKey;
    record.requestFingerprint = requestFingerprint;
    record.snapshotJson = snapshotJson;
    record.expiresAt = expiresAt;
    return record;
  }

  /** 覆盖快照（同一 scopeKey 重复请求时刷新指纹、快照与 TTL）。 */
  public void refresh(String requestFingerprint, String snapshotJson, Instant expiresAt) {
    this.requestFingerprint = requestFingerprint;
    this.snapshotJson = snapshotJson;
    this.expiresAt = expiresAt;
  }

  public boolean expiredAt(Instant now) {
    return expiresAt != null && !expiresAt.isAfter(now);
  }
}
