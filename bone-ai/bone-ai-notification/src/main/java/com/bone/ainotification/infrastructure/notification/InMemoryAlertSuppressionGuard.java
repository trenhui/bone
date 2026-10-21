package com.bone.ainotification.infrastructure.notification;

import com.bone.ainotification.domain.gateway.AlertSuppressionGuard;
import com.bone.ainotification.domain.model.AlertEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版告警风暴抑制：按「租户 + 来源模块 + 严重度 + 标题」聚合为同类键，窗口内前 {@code maxPerWindow} 条放行，超额静默。
 *
 * <p>线程安全：每键一个计数器，{@code computeIfAbsent} 保证单实例，计数与窗口重置在键内 {@code synchronized} 原子完成。
 *
 * <p><b>进程内状态</b>：多副本各抑制各的（与配额同理）；需全局静默以 {@code @Bean} 覆盖为 Redis 等共享实现。键空间由告警标题 /
 * 规则集决定、有界，长期运行不会无限增长。
 */
public class InMemoryAlertSuppressionGuard implements AlertSuppressionGuard {

  private final Duration window;
  private final int maxPerWindow;
  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

  public InMemoryAlertSuppressionGuard(Duration window, int maxPerWindow) {
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("抑制窗口时长须为正: " + window);
    }
    if (maxPerWindow <= 0) {
      throw new IllegalArgumentException("窗口内放行条数须为正: " + maxPerWindow);
    }
    this.window = window;
    this.maxPerWindow = maxPerWindow;
  }

  @Override
  public boolean shouldSuppress(AlertEvent event, Instant now) {
    String key = keyOf(event);
    WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(now, 0));
    synchronized (counter) {
      if (!now.isBefore(counter.windowStart.plus(window))) {
        // 窗口已过：滚动到新窗口重新计数
        counter.windowStart = now;
        counter.count = 0;
      }
      counter.count++;
      // 前 maxPerWindow 条放行（让人知道在发生），超额静默
      return counter.count > maxPerWindow;
    }
  }

  /** 同类键：租户 + 来源模块 + 严重度 + 标题（不含 body / alertId，避免每条唯一导致永不聚合）。 */
  static String keyOf(AlertEvent event) {
    return event.tenantId()
        + "|"
        + nullToEmpty(event.sourceModule())
        + "|"
        + nullToEmpty(event.severity())
        + "|"
        + nullToEmpty(event.title());
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static final class WindowCounter {
    private Instant windowStart;
    private int count;

    private WindowCounter(Instant windowStart, int count) {
      this.windowStart = windowStart;
      this.count = count;
    }
  }
}
