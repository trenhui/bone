package com.bone.system.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Micrometer 取数适配器的口径测试：别名映射、百分数换算、错误率统计、不可评估即 empty。 */
class MicrometerMetricValueAdapterTest {

  private SimpleMeterRegistry registry;
  private MicrometerMetricValueAdapter adapter;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    adapter = new MicrometerMetricValueAdapter(registry);
  }

  @Test
  void cpuUsageAliasConvertedToPercent() {
    double[] cpu = {0.37};
    registry.gauge("system.cpu.usage", cpu, c -> c[0]);

    assertThat(adapter.resolve("cpu.usage").getAsDouble()).isEqualTo(37.0);
    assertThat(adapter.resolve("system.cpu.usage").getAsDouble()).isEqualTo(37.0);
  }

  @Test
  void directGaugeNameResolvedAsIs() {
    double[] threads = {42};
    registry.gauge("jvm.threads.live", threads, t -> t[0]);

    assertThat(adapter.resolve("jvm.threads.live").getAsDouble()).isEqualTo(42.0);
  }

  @Test
  void memoryUsagePercentComputedFromUsedAndMax() {
    double[] used = {30};
    double[] max = {100};
    registry.gauge("jvm.memory.used", used, u -> u[0]);
    registry.gauge("jvm.memory.max", max, m -> m[0]);

    assertThat(adapter.resolve("memory.usage").getAsDouble()).isEqualTo(30.0);
  }

  @Test
  void errorRatePercentComputedFromHttpTimers() {
    Timer.builder("http.server.requests").tag("exception", "None").register(registry);
    Timer.builder("http.server.requests")
        .tag("exception", "None")
        .register(registry)
        .record(10, TimeUnit.MILLISECONDS);
    Timer.builder("http.server.requests")
        .tag("exception", "NullPointerException")
        .register(registry)
        .record(10, TimeUnit.MILLISECONDS);

    assertThat(adapter.resolve("api.error_rate").getAsDouble()).isEqualTo(50.0);
  }

  /** 无流量（0 样本）按 0% 错误率：无流量不等于故障。 */
  @Test
  void errorRateWithoutSamplesIsZero() {
    assertThat(adapter.resolve("api.error_rate").getAsDouble()).isZero();
  }

  /** 指标不存在是正常分支：评估器跳过该规则，而不是报错。 */
  @Test
  void missingMetricYieldsEmpty() {
    assertThat(adapter.resolve("not.registered.metric")).isEmpty();
    assertThat(adapter.resolve("")).isEmpty();
    assertThat(adapter.resolve(null)).isEmpty();
  }
}
