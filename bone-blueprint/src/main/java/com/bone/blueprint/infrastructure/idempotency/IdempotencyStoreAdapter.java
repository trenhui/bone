package com.bone.blueprint.infrastructure.idempotency;

import com.bone.blueprint.application.port.out.IdempotencyStore;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link IdempotencyStore} 的落库实现（表 {@code bp_idempotency_record}）。
 *
 * <p><b>为何用库而不是进程内存</b>：幂等要跨进程与重启生效——多实例部署时，内存实现会让「同一 Idempotency-Key 打到另一实例」照旧重复执行。平台既有模块用
 * Redis（{@code RedisCatalogIdempotencyStore}）；blueprint 无 Redis 依赖，故用本模块已有的 MySQL 通道落库，语义等价（快照 +
 * TTL）。
 *
 * <p><b>为何用「先查后覆盖写」而非纯插入抢占</b>：目标语义是<strong>可重放</strong>（返回历史响应），而非「只允许 一次」。并发下的重复执行由业务侧唯一键兜底（如
 * {@code bp_payment.channel_trade_no}），这里只负责快照存取。
 *
 * <p>关闭开关 {@code bone.blueprint.idempotency.enabled=false} 时本 Bean 不装配，服务退化为「不记录快照」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "bone.blueprint.idempotency.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdempotencyStoreAdapter implements IdempotencyStore {

  private final IdempotencyRepository repository;

  @Override
  public Optional<Snapshot> find(String scopeKey) {
    List<IdempotencyRecord> records =
        repository.findByCriteria(
            Criteria.<IdempotencyRecord>create()
                .eq(IdempotencyRecord::getScopeKey, scopeKey)
                .page(1, 1));
    if (records == null || records.isEmpty()) {
      return Optional.empty();
    }
    IdempotencyRecord record = records.get(0);
    if (record.expiredAt(Instant.now())) {
      // 过期即视为不存在：TTL 24h 后同一键可重新使用（API 规范 §6.1）
      log.debug("幂等快照已过期，按未命中处理: scopeKey={}", scopeKey);
      return Optional.empty();
    }
    return Optional.of(new Snapshot(record.getRequestFingerprint(), record.getSnapshotJson()));
  }

  @Override
  public void put(String scopeKey, Snapshot snapshot, Duration ttl) {
    Instant expiresAt = Instant.now().plus(ttl);
    List<IdempotencyRecord> existing =
        repository.findByCriteria(
            Criteria.<IdempotencyRecord>create()
                .eq(IdempotencyRecord::getScopeKey, scopeKey)
                .page(1, 1));
    if (existing == null || existing.isEmpty()) {
      long tenantId = resolveTenantId(scopeKey);
      repository.insert(
          IdempotencyRecord.of(
              tenantId,
              scopeKey,
              snapshot.requestFingerprint(),
              snapshot.snapshotJson(),
              expiresAt));
      return;
    }
    IdempotencyRecord record = existing.get(0);
    record.refresh(snapshot.requestFingerprint(), snapshot.snapshotJson(), expiresAt);
    repository.update(record);
  }

  /**
   * 从 scopeKey 前缀取租户（格式 {@code tenantId|userId|key|method|path}）。
   *
   * <p>租户只用于多租户排障与隔离列，非法格式回落 0 而不是抛错——幂等本身不应因为租户解析失败而让业务请求失败。
   */
  private long resolveTenantId(String scopeKey) {
    int separator = scopeKey == null ? -1 : scopeKey.indexOf('|');
    if (separator <= 0) {
      return 0L;
    }
    try {
      return Long.parseLong(scopeKey.substring(0, separator));
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }
}
