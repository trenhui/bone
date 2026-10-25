package com.bone.claim.infrastructure.idempotency;

import com.bone.core.idempotency.IdempotencyStore;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link IdempotencyStore} 的落库实现（表 {@code claim_idempotency_record}）。
 *
 * <p>幂等能力已上收 framework：编排在 {@code com.bone.core.idempotency.IdempotencyService}，本模块只提供存储实现 （与
 * blueprint {@code IdempotencyPortAdapter} 同构）。提交理赔申请（{@code ClaimCaseController#submit}）的 {@code
 * Idempotency-Key} 同键同参返回首次结果，依赖本 Bean 的存在——缺它则控制器装配失败、应用起不来。
 *
 * <p><b>为何用库而不是进程内存</b>：幂等要跨进程与重启生效，多实例部署时内存实现会让同一幂等键打到另一实例照旧重复执行。
 *
 * <p><b>为何用「先查后覆盖写」而非纯插入抢占</b>：目标语义是<strong>可重放</strong>（返回历史响应）；并发下的重复执行由业务侧唯一键兜底 （如 {@code
 * t_claim_case.idempotency_key}），这里只负责快照存取。
 *
 * <p>关闭开关 {@code bone.claim.idempotency.enabled=false} 时本 Bean 不装配，服务退化为「不记录快照」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "bone.claim.idempotency.enabled",
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
      // 过期即视为不存在：TTL 24h 后同一键可重新使用
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
      try {
        repository.save(
            IdempotencyRecord.of(
                tenantId,
                scopeKey,
                snapshot.requestFingerprint(),
                snapshot.snapshotJson(),
                expiresAt));
      } catch (DataIntegrityViolationException ex) {
        // 并发窗口：两请求同时命中同一 scopeKey，都查不到、都 insert，第二条触发唯一键冲突。
        // 已存在的那条就是正确的快照，重新查询后 refresh TTL + save。
        List<IdempotencyRecord> concurrentHit =
            repository.findByCriteria(
                Criteria.<IdempotencyRecord>create()
                    .eq(IdempotencyRecord::getScopeKey, scopeKey)
                    .page(1, 1));
        if (concurrentHit != null && !concurrentHit.isEmpty()) {
          IdempotencyRecord record = concurrentHit.get(0);
          record.refresh(snapshot.requestFingerprint(), snapshot.snapshotJson(), expiresAt);
          repository.save(record);
        } else {
          log.debug("幂等快照插入遇到唯一键冲突（并发重放），已存在记录即视为命中: scopeKey={}", scopeKey);
        }
      }
      return;
    }
    IdempotencyRecord record = existing.get(0);
    record.refresh(snapshot.requestFingerprint(), snapshot.snapshotJson(), expiresAt);
    repository.save(record);
  }

  /**
   * 从 scopeKey 前缀取租户（格式 {@code tenantId|userId|key|method|path}）。
   *
   * <p>租户只用于多租户排障与隔离列，非法格式回落 0 而不是抛错——幂等本身不应因租户解析失败让业务请求失败。
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
