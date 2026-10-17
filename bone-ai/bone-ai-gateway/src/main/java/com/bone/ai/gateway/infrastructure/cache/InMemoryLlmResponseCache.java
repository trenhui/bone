package com.bone.ai.gateway.infrastructure.cache;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * 精确响应缓存（默认实现）：有界 LRU + TTL，零依赖。
 *
 * <p><b>有界是硬要求</b>：无界 Map 会把网关变成 OOM 候选者。这里用 {@link LinkedHashMap} 的 accessOrder 做 LRU，
 * 超容量自动淘汰最久未用项；TTL 保证条款/模型变更后旧答案会自然失效。
 *
 * <p>线程安全：全部方法 {@code synchronized}——网关是每请求都要读写的热路径，用单锁换实现正确性； 若成为瓶颈，换 Caffeine /
 * Redis（新依赖，L3）比在这里手写分段锁更划算。
 */
public class InMemoryLlmResponseCache implements LlmResponseCache {

  private final int maxEntries;
  private final long ttlMillis;
  private final Map<String, Entry> store;
  private final LongSupplier clock;

  public InMemoryLlmResponseCache(int maxEntries, long ttlMillis) {
    this(maxEntries, ttlMillis, System::currentTimeMillis);
  }

  /** 可注入时钟（TTL 单测用，避免 sleep）。 */
  public InMemoryLlmResponseCache(int maxEntries, long ttlMillis, LongSupplier clock) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("缓存容量必须为正: " + maxEntries);
    }
    this.maxEntries = maxEntries;
    this.ttlMillis = ttlMillis;
    this.clock = clock;
    this.store =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > InMemoryLlmResponseCache.this.maxEntries;
          }
        };
  }

  @Override
  public synchronized Optional<LlmResponse> get(String key) {
    Entry entry = store.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (ttlMillis > 0 && clock.getAsLong() - entry.storedAt() > ttlMillis) {
      store.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.response());
  }

  @Override
  public synchronized void put(String key, LlmResponse response) {
    if (key == null || response == null) {
      return;
    }
    store.put(key, new Entry(response, clock.getAsLong()));
  }

  /** 当前条目数（运维观测 / 测试用）。 */
  public synchronized int size() {
    return store.size();
  }

  private record Entry(LlmResponse response, long storedAt) {}
}
