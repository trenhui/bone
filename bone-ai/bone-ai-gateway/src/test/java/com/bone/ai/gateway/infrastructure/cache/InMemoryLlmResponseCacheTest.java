package com.bone.ai.gateway.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 精确缓存：命中、TTL 过期、LRU 有界淘汰。 */
class InMemoryLlmResponseCacheTest {

  private static LlmResponse resp(String content) {
    return new LlmResponse(content, "m", "stop", new LlmUsage(1, 1, 2));
  }

  @Test
  void returns_cached_response() {
    InMemoryLlmResponseCache cache = new InMemoryLlmResponseCache(10, 60_000);
    cache.put("k", resp("v"));
    assertThat(cache.get("k")).isPresent().get().extracting(LlmResponse::content).isEqualTo("v");
  }

  @Test
  void misses_unknown_key() {
    assertThat(new InMemoryLlmResponseCache(10, 60_000).get("nope")).isEmpty();
  }

  @Test
  void expires_after_ttl() {
    AtomicLong now = new AtomicLong(1_000_000L);
    InMemoryLlmResponseCache cache = new InMemoryLlmResponseCache(10, 1_000, now::get);
    cache.put("k", resp("v"));

    now.addAndGet(1_001);
    assertThat(cache.get("k")).isEmpty();
    assertThat(cache.size()).isZero(); // 过期项被清掉，不占容量
  }

  @Test
  void evicts_least_recently_used_when_full() {
    InMemoryLlmResponseCache cache = new InMemoryLlmResponseCache(2, 60_000);
    cache.put("a", resp("A"));
    cache.put("b", resp("B"));
    cache.get("a"); // 让 a 变成最近使用
    cache.put("c", resp("C")); // 应淘汰 b

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.get("a")).isPresent();
    assertThat(cache.get("b")).isEmpty();
    assertThat(cache.get("c")).isPresent();
  }

  @Test
  void rejects_non_positive_capacity() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new InMemoryLlmResponseCache(0, 1000))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
