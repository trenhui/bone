package com.bone.ai.gateway.infrastructure.quota;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.core.exception.BizException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 租户配额闸门（默认实现）：进程内固定窗口计数，零依赖。
 *
 * <p><b>局限（务必读）</b>：进程内计数——多副本部署时每个副本各算一份，全局配额为副本数的 N 倍。单副本 / 私有化单体可用； 多副本必须换 Redis 令牌桶（以
 * {@code @Bean} 覆盖）。这里不假装它是分布式限流。
 *
 * <p>为何用固定窗口而非滑动窗口：实现简单、内存恒定（每租户一个计数器），对「防失控租户刷爆共享模型通道」这个目标足够； 滑动窗口的边界突刺（窗口切换瞬间 2
 * 倍流量）在模型调用场景影响有限，不值得为它引入环形缓冲的复杂度。
 *
 * <p>线程安全：{@link ConcurrentHashMap} + {@link AtomicLong}；窗口切换用 CAS 保证每窗口只重置一次。
 */
public class InMemoryTenantQuotaGuard implements TenantQuotaGuard {

  private final long maxRequestsPerWindow;
  private final long windowMillis;
  private final LongSupplier clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public InMemoryTenantQuotaGuard(long maxRequestsPerWindow, long windowMillis) {
    this(maxRequestsPerWindow, windowMillis, System::currentTimeMillis);
  }

  /** 可注入时钟（单测用，避免 sleep 等窗口切换）。 */
  public InMemoryTenantQuotaGuard(
      long maxRequestsPerWindow, long windowMillis, LongSupplier clock) {
    if (maxRequestsPerWindow <= 0) {
      throw new IllegalArgumentException("配额必须为正: " + maxRequestsPerWindow);
    }
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.windowMillis = Math.max(windowMillis, 1000L);
    this.clock = clock;
  }

  @Override
  public void acquire(String tenantId) {
    // 租户缺失不拦（POC 单租户口径）：配额是容量治理，不该变成「没租户就不许调用」的可用性故障
    if (tenantId == null || tenantId.isBlank()) {
      return;
    }
    long now = clock.getAsLong();
    long windowStart = now - (now % windowMillis);
    Window window = windows.computeIfAbsent(tenantId, t -> new Window(windowStart));
    if (window.tryAcquire(windowStart, maxRequestsPerWindow)) {
      return;
    }
    long remainingMs = windowStart + windowMillis - now;
    throw new BizException(
        429,
        "租户配额超限：window="
            + windowMillis
            + "ms, limit="
            + maxRequestsPerWindow
            + ", retryAfter="
            + remainingMs
            + "ms, tenantId="
            + tenantId,
        AiGatewayErrorCodes.QUOTA_EXCEEDED);
  }

  /** 当前窗口已用请求数（观测 / 测试用）。 */
  public long used(String tenantId) {
    Window window = windows.get(tenantId);
    return window == null ? 0 : window.count.get();
  }

  private static final class Window {
    private volatile long start;
    private final AtomicLong count = new AtomicLong();

    Window(long start) {
      this.start = start;
    }

    /** CAS 切窗后自增；超限返回 false 且不消耗配额。 */
    boolean tryAcquire(long currentWindowStart, long limit) {
      if (start != currentWindowStart) {
        synchronized (this) {
          if (start != currentWindowStart) {
            start = currentWindowStart;
            count.set(0);
          }
        }
      }
      while (true) {
        long current = count.get();
        if (current >= limit) {
          return false;
        }
        if (count.compareAndSet(current, current + 1)) {
          return true;
        }
      }
    }
  }
}
