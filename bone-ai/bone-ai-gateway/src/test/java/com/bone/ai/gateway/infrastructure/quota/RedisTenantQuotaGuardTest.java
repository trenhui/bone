package com.bone.ai.gateway.infrastructure.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.core.exception.BizException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

/**
 * Redis 分布式配额：与进程内版逐条对齐的语义 + Redis 专属边界（TTL / fail-open）。
 *
 * <p>用 Mock 替身驱动（不连真实 Redis）：验证的是<b>键设计、超限判定、故障策略</b>这些本模块自身的逻辑， Redisson 客户端正确性由 Redisson
 * 自身测试与联调覆盖。
 */
class RedisTenantQuotaGuardTest {

  private final RedissonClient redisson = mock(RedissonClient.class);

  @Test
  @DisplayName("窗口内放行至上限，第 limit+1 次抛 429 QUOTA_EXCEEDED（与进程内版同契约）")
  void allows_up_to_limit_then_rejects() {
    RMap<String, Long> counter = mock(RMap.class);
    when(redisson.<String, Long>getMap(anyString())).thenReturn(counter);
    when(counter.addAndGet("count", 1)).thenReturn(1L, 2L, 3L);
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 2, 60_000);

    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    assertThatThrownBy(() -> guard.acquire("1"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.QUOTA_EXCEEDED));
  }

  @Test
  @DisplayName("首次自增才置 TTL（2 倍窗口），重复自增不续期")
  void expire_only_on_first_increment() {
    RMap<String, Long> counter = mock(RMap.class);
    when(redisson.<String, Long>getMap(anyString())).thenReturn(counter);
    when(counter.addAndGet("count", 1)).thenReturn(1L, 2L);
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 5, 60_000);

    guard.acquire("1");
    guard.acquire("1");

    verify(counter, times(1)).expire(Duration.ofMillis(120_000));
  }

  @Test
  @DisplayName("键含租户 + 窗口起点：同窗口同租户同键，跨窗口/跨租户换键")
  void key_contains_tenant_and_window_start() {
    RMap<String, Long> counter = mock(RMap.class);
    when(redisson.<String, Long>getMap(anyString())).thenReturn(counter);
    when(counter.addAndGet("count", 1)).thenReturn(1L);
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 5, 60_000);

    guard.acquire("1");
    guard.acquire("2");

    ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
    verify(redisson, times(2)).getMap(keys.capture());
    long windowStart = System.currentTimeMillis() / 60_000 * 60_000;
    assertThat(keys.getAllValues().get(0)).isEqualTo("bone:ai:quota:1:" + windowStart);
    assertThat(keys.getAllValues().get(1)).isEqualTo("bone:ai:quota:2:" + windowStart);
  }

  @Test
  @DisplayName("缺租户不拦、不触 Redis（配额是容量治理，不是可用性故障）")
  void missing_tenant_short_circuits() {
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 1, 60_000);

    assertThatCode(() -> guard.acquire(null)).doesNotThrowAnyException();
    assertThatCode(() -> guard.acquire("  ")).doesNotThrowAnyException();
    verifyNoInteractions(redisson);
  }

  @Test
  @DisplayName("配额关闭（limit<=0）：闸门装配但恒放行，与进程内版「未配置则不建」等价")
  void disabled_quota_always_allows() {
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 0, 60_000);

    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    verifyNoInteractions(redisson);
  }

  @Test
  @DisplayName("Redis 故障 fail-open：成本闸门让位业务可用性（安全联锁不经 Redis）")
  void redis_failure_fails_open() {
    when(redisson.getMap(anyString())).thenThrow(new RuntimeException("redis down"));
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 1, 60_000);

    // 不抛业务异常：AI 故障不阻断业务（详设 §4 #18 架构红线）
    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    // 读取侧同样不炸，返回 -1 表示未知
    assertThat(guard.used("1")).isEqualTo(-1L);
  }

  @Test
  @DisplayName("超限是业务语义：即便 Redis 同时故障也不被 fail-open 吞掉（BizException 原样上抛）")
  void quota_exceeded_not_swallowed_by_fail_open() {
    RMap<String, Long> counter = mock(RMap.class);
    when(redisson.<String, Long>getMap(anyString())).thenReturn(counter);
    when(counter.addAndGet(eq("count"), eq(1)))
        .thenThrow(new BizException(429, "quota", AiGatewayErrorCodes.QUOTA_EXCEEDED));
    RedisTenantQuotaGuard guard = new RedisTenantQuotaGuard(redisson, 1, 60_000);

    assertThatThrownBy(() -> guard.acquire("1")).isInstanceOf(BizException.class);
    verify(counter, times(0)).expire(Duration.ofMillis(anyLong()));
  }
}
