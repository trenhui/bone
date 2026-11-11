package com.bone.claim.infrastructure.idempotency;

import com.bone.core.exception.IdempotentException;
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
 * <p><b>为何用「先查后插入」而非「先查后覆盖写」</b>：目标语义是<strong>可重放首次成功响应</strong>（P0-3：首快不可变）；并发下的重复执行由业务侧唯一键兜底 （如
 * {@code t_claim_case.idempotency_key}），这里只负责首快存取。
 *
 * <p><b>过期待遇（P0-3）</b>：快照过期 ≠ 未命中——TTL（默认 30 天）内同键重放一律 409 拒绝重新执行， 不再放行二次执行。
 *
 * <p><b>关闭开关 {@code bone.claim.idempotency.enabled=false} 时</b>本 Bean 不装配，{@code
 * IdempotencyService} 缺构造依赖、应用拒绝启动（fail-closed）：资金写路径不允许「无幂等也能跑」的降级形态。
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
      // P0-3（2026-11-11）：过期不再按未命中放行重新执行。TTL（默认 30 天）覆盖理赔争议窗口，
      // 窗口内同一键重来 = 「已成功但响应丢失」的重试或异常重放，重新执行会造成二次出金/二次建案
      // ——按 409 冲突拒绝（GlobalExceptionHandler 将 IdempotentException 映射为 409），让调用方
      // 以新键或人工对账介入，而不是拿到第二次执行结果。
      throw new IdempotentException("幂等键已过期但仍在保留期内，拒绝重新执行: scopeKey=" + scopeKey);
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
        // 已存在的那条就是首次快照——P0-3 语义：首快不可覆盖，保留它并结束本次记录。
        log.debug("幂等快照插入遇到唯一键冲突（并发重放），保留首次快照: scopeKey={}", scopeKey);
      }
      return;
    }
    // P0-3（2026-11-11）：记录已存在时不得用本次响应覆盖首次快照——重放语义是「返回首次成功响应」，
    // 覆盖会让并发竞态下后到的执行结果顶掉先到的，客户端两次拿到不同响应。首快不可变，静默保留。
    log.debug("幂等键已有快照，保留首次响应不覆盖: scopeKey={}", scopeKey);
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
