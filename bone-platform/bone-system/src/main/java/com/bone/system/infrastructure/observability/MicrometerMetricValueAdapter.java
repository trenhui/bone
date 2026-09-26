package com.bone.system.infrastructure.observability;

import com.bone.system.application.port.out.MetricValuePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.OptionalDouble;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Micrometer 指标取数适配器：把告警规则里的业务口径映射到本进程 {@link MeterRegistry} 的实际指标。
 *
 * <p><b>口径约定</b>（与详设 §9 对齐，全部换算为「百分数」或原值）：
 *
 * <ul>
 *   <li>{@code cpu.usage} / {@code system.cpu.usage}：Micrometer 的进程 CPU 使用率（0~1）× 100；
 *   <li>{@code memory.usage}：JVM 堆已用 / 堆上限 × 100（上限不可得时不可评估）；
 *   <li>{@code api.error_rate} / {@code api.error.rate}：{@code http.server.requests} 按 exception
 *       标签统计错误占比 × 100；
 *   <li>{@code db.connections.active}：HikariCP 活跃连接数；
 *   <li>其余名称直接按 Micrometer gauge 名解析（如 {@code jvm.threads.live}），支持规则自定义。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicrometerMetricValueAdapter implements MetricValuePort {

  private static final Set<String> CPU_KEYS = Set.of("cpu.usage", "system.cpu.usage");
  private static final Set<String> ERROR_RATE_KEYS = Set.of("api.error_rate", "api.error.rate");
  private static final String HTTP_REQUESTS = "http.server.requests";

  private final MeterRegistry meterRegistry;

  @Override
  public OptionalDouble resolve(String metricName) {
    if (metricName == null || metricName.isBlank()) {
      return OptionalDouble.empty();
    }
    try {
      if (CPU_KEYS.contains(metricName)) {
        return ratioPercent("system.cpu.usage", 1.0);
      }
      if (ERROR_RATE_KEYS.contains(metricName)) {
        return OptionalDouble.of(httpErrorRatePercent());
      }
      if ("memory.usage".equals(metricName)) {
        return memoryUsagePercent();
      }
      if ("db.connections.active".equals(metricName)) {
        return gaugeValue("hikaricp.connections.active");
      }
      return gaugeValue(metricName);
    } catch (RuntimeException ex) {
      log.debug("[AlertEval] 指标不可评估 name={}, reason={}", metricName, ex.getMessage());
      return OptionalDouble.empty();
    }
  }

  /** gauge 原值。 */
  private OptionalDouble gaugeValue(String name) {
    io.micrometer.core.instrument.Gauge gauge = meterRegistry.find(name).gauge();
    if (gauge == null) {
      return OptionalDouble.empty();
    }
    double value = gauge.value();
    return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
  }

  /** 单 gauge 换算百分数：value/base × 100。 */
  private OptionalDouble ratioPercent(String name, double base) {
    OptionalDouble value = gaugeValue(name);
    if (value.isEmpty()) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(value.getAsDouble() / base * 100.0);
  }

  private OptionalDouble memoryUsagePercent() {
    io.micrometer.core.instrument.Gauge used = meterRegistry.find("jvm.memory.used").gauge();
    io.micrometer.core.instrument.Gauge max = meterRegistry.find("jvm.memory.max").gauge();
    if (used == null || max == null || max.value() <= 0) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(used.value() / max.value() * 100.0);
  }

  /**
   * 错误率口径：{@code http.server.requests} 全部 timer 计数为分母，exception 标签非 None 的计数为分子。 没有任何请求样本时按 0%
   * 处理（无流量≠故障）。
   */
  private double httpErrorRatePercent() {
    Collection<Timer> timers = meterRegistry.find(HTTP_REQUESTS).timers();
    long total = 0;
    long errors = 0;
    for (Timer timer : timers) {
      long count = timer.count();
      total += count;
      String exception = timer.getId().getTag("exception");
      if (exception != null && !"None".equals(exception) && !"none".equals(exception)) {
        errors += count;
      }
    }
    return total == 0 ? 0.0 : errors * 100.0 / total;
  }
}
