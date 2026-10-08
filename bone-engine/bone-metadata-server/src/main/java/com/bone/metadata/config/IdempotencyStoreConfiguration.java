package com.bone.metadata.config;

import com.bone.core.idempotency.IdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 幂等快照存储的<b>默认（内存）实现</b>（2026-10-07 新增）。
 *
 * <p><b>为什么需要它</b>：接入 {@code bone-metadata-engine-starter} 后，引擎的 {@code GenericOperationService}
 * 会依赖 framework 的 {@code IdempotencyService}， 后者又要求容器里有 {@link IdempotencyStore} 的实现。而 {@code
 * IdempotencyAutoConfiguration}（唯一注册该服务的地方）随 {@code bone-web} 分发，条件是
 * {@code @ConditionalOnBean(IdempotencyStore.class)}—— <b>即"存储实现由业务模块提供，framework 只提供编排"</b>。
 *
 * <p>★ {@code IdempotencyStore} 的 javadoc 明确写了这一点： 「注意该自动配置随 {@code bone-web} 分发：<b>不依赖 {@code
 * bone-web} 的模块拿不到该 Bean， 届时请自行注册</b>或把自动配置下沉到共有宿主」。
 *
 * <p>本类就是那个"自行注册"：{@code bone-metadata-server} 不依赖 {@code bone-web} （实测其 com.bone 依赖里没有
 * bone-web），所以引擎一旦接线就会缺这个 bean。
 *
 * <p><b>为什么默认用内存实现而不是持久化</b>：内存版语义上"不承诺跨实例/重启的幂等"，
 * 对元数据服务这种以写路径为主的场景已能挡住<b>同一实例内的重复提交</b>（最常见的重复来源）。 真正需要强幂等时（跨实例重试、消息重投），业务方应自行提供一个持久化实现—— 带 {@link
 * ConditionalOnMissingBean}，一旦宿主提供了自己的实现，本类自动退让。
 */
@Configuration(proxyBeanMethods = false)
public class IdempotencyStoreConfiguration {

  /**
   * 内存版幂等存储。
   *
   * <p><b>容量保护</b>：用 Caffeine 风格的过期清理代价高，这里用「惰性过期 + 容量上限」： 写入时顺带清理已过期项；超过上限时拒绝写入并返回"无快照" ⇒
   * 内存不会因为键无限增长而 OOM，且降级行为是安全的（退化为不记录）。
   */
  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  public IdempotencyStore inMemoryIdempotencyStore() {
    return new InMemoryIdempotencyStore();
  }

  /** 内存实现：{@link ConcurrentHashMap} + 惰性过期。 */
  static class InMemoryIdempotencyStore implements IdempotencyStore {

    /** 容量上限：超过则不再写入（降级为"无快照"），避免内存无限增长。 */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<Snapshot> find(String scopeKey) {
      purgeExpired();
      Entry entry = entries.get(scopeKey);
      if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
        return Optional.empty();
      }
      return Optional.of(entry.snapshot());
    }

    @Override
    public void put(String scopeKey, Snapshot snapshot, Duration ttl) {
      if (entries.size() >= MAX_ENTRIES) {
        purgeExpired();
        // 清理后仍满 ⇒ 放弃写入（返回"无快照"比驱逐已有条目更安全：
        // 驱逐会让先前请求的幂等承诺失效）
        if (entries.size() >= MAX_ENTRIES) {
          return;
        }
      }
      Instant expiresAt =
          (ttl == null || ttl.isNegative() || ttl.isZero())
              ? Instant.now().plusSeconds(60)
              : Instant.now().plus(ttl);
      // 覆盖写：同一 scopeKey 重复请求时刷新快照与 TTL
      entries.put(scopeKey, new Entry(snapshot, expiresAt));
    }

    /** 清理已过期条目。 */
    private void purgeExpired() {
      Instant now = Instant.now();
      entries.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    /** 快照 + 过期时间。 */
    private record Entry(Snapshot snapshot, Instant expiresAt) {}
  }
}
