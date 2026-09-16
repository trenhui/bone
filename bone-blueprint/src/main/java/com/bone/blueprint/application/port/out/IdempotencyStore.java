package com.bone.blueprint.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等快照出站端口（API 规范 §8：键相同 body 不同 → 409；相同 body → 返回同一响应）。
 *
 * <p><b>放在 {@code application/port/out}</b>：E-4.3 把「幂等」与通知、时钟、身份生成并列为技术能力，由 infrastructure
 * 实现。与平台既有模块同范式（{@code StudioIdempotencyStore} / {@code CatalogIdempotencyStore}）， 差异只在位置：本模块按目标态放
 * {@code application/port/out}，而非存量 {@code domain/gateway}。
 *
 * <p>实现须保证 {@link #put} 对同一 {@code scopeKey} 是<strong>覆盖写</strong>（重复请求命中同一键时刷新快照与 TTL）。
 */
public interface IdempotencyStore {

  /** 取历史快照；不存在或已过期返回 {@link Optional#empty()}。 */
  Optional<Snapshot> find(String scopeKey);

  /** 写入/覆盖快照，并按 TTL 记录过期时间。 */
  void put(String scopeKey, Snapshot snapshot, Duration ttl);

  /**
   * 幂等快照。
   *
   * @param requestFingerprint 请求体指纹（SHA-256）：用于判定「键相同但 body 不同」→ 409
   * @param snapshotJson 响应快照 JSON（状态码 + Location + 响应体），重放时原样返回
   */
  record Snapshot(String requestFingerprint, String snapshotJson) {}
}
