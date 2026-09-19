package com.bone.core.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等快照存储抽象。
 *
 * <p><b>归属</b>：跨模块复用的技术能力契约（类似 {@code TenantContext} / {@code DistributedIdGenerator}）。存储实现由各业务模块 的
 * infrastructure 层提供——MySQL 表 / Redis / MongoDB 都可，业务模块只需要实现本接口即可接入 {@link IdempotencyService}
 * 的幂等编排。
 *
 * <p><b>与 DDD 分层的关系</b>：业务模块的 {@code application/port/out} 可以 {@code extends} 本接口保留 Port 命名（如
 * {@code IdempotencyPort extends IdempotencyStore}），也可以直接 {@code implements}——两种方式都等价。
 *
 * <p><b>配套 Bean 从哪来</b>：{@link IdempotencyService} 由 framework 自动配置注册（{@code
 * com.bone.core.web.config.IdempotencyAutoConfiguration}，条件就是「存在本接口的实现 Bean」）——模块实现本接口即可拿到编排服务，
 * <strong>不必</strong>把 {@code com.bone.core.idempotency} 加进 {@code @ComponentScan}（该包不在任何模块的扫描域内，
 * 漏配的表现是 Controller 装配失败、应用起不来）。注意该自动配置随 {@code bone-web} 分发：不依赖 {@code bone-web} 的模块拿不到该
 * Bean，届时请自行注册或把自动配置下沉到共有宿主。
 */
public interface IdempotencyStore {

  /**
   * 取历史快照；不存在或已过期返回 {@link Optional#empty()}。
   *
   * @param scopeKey 作用域键（由 {@link IdempotencyService#buildScopeKey} 拼接）
   */
  Optional<Snapshot> find(String scopeKey);

  /**
   * 写入/覆盖快照，并按 TTL 记录过期时间。
   *
   * <p>实现须保证对同一 {@code scopeKey} 是<strong>覆盖写</strong>（重复请求命中同一键时刷新快照与 TTL）。
   *
   * @param scopeKey 作用域键
   * @param snapshot 快照内容
   * @param ttl 过期时长
   */
  void put(String scopeKey, Snapshot snapshot, Duration ttl);

  /**
   * 幂等快照。
   *
   * @param requestFingerprint 请求体指纹（SHA-256）：用于判定「键相同但 body 不同」→ 409 冲突
   * @param snapshotJson 响应快照 JSON（状态码 + Location + 响应体），重放时原样返回
   */
  record Snapshot(String requestFingerprint, String snapshotJson) {}
}
