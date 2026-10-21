package com.bone.metadata.catalog.infrastructure.observability;

import com.bone.metadata.catalog.domain.gateway.DictRenderMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 字典解析指标端口实现（Micrometer）。
 *
 * <p><b>为什么用两个 Counter 而不是一个带 tag 的 Counter</b>：降级率 = fallback / total，两个独立计数器在 {@code
 * /actuator/prometheus} 上直接可算（{@code bone_dict_resolve_total{outcome="fallback"} /
 * bone_dict_resolve_total{outcome="live"}}），不需要在监控侧再猜分母；同时端点侧可无依赖地读同一份计数。
 *
 * <p>计数器是<b>进程内累计值</b>（重启归零）——用于「近期是否在降级」的信号，不做长期台账；长期趋势由 Prometheus 的 {@code rate()}
 * 承担，这是计数器该有的用法。
 */
public class MicrometerDictRenderMetrics implements DictRenderMetricsPort {

  private static final String METRIC = "bone.dict.resolve";

  private final Counter liveCounter;
  private final Counter fallbackCounter;

  public MicrometerDictRenderMetrics(MeterRegistry registry) {
    this.liveCounter =
        Counter.builder(METRIC)
            .description("字典绑定真源解析次数（按结果分）")
            .tag("outcome", "live")
            .register(registry);
    this.fallbackCounter =
        Counter.builder(METRIC)
            .description("字典绑定真源解析次数（按结果分）")
            .tag("outcome", "fallback")
            .register(registry);
  }

  @Override
  public void recordResolve(boolean fallbackToSnapshot) {
    if (fallbackToSnapshot) {
      fallbackCounter.increment();
    } else {
      liveCounter.increment();
    }
  }

  @Override
  public long totalResolves() {
    return (long) liveCounter.count() + (long) fallbackCounter.count();
  }

  @Override
  public long fallbackResolves() {
    return (long) fallbackCounter.count();
  }
}
