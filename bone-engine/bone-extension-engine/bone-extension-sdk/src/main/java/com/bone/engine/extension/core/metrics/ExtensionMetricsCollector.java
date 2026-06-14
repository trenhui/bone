package com.bone.engine.extension.core.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 扩展引擎 RED 指标（详设 §11.1 / API 规范 §10）。
 *
 * <p>指标名：{@code extension_invoke_total}、{@code extension_invoke_duration_seconds}、 {@code
 * extension_router_no_match_total}、{@code extension_bulkhead_rejected_total}。
 */
@Slf4j
public class ExtensionMetricsCollector {

  private static final long SLOW_THRESHOLD_MS = 100L;

  private final MeterRegistry meterRegistry;
  private final AtomicLong extensionPointCount = new AtomicLong(0);
  private final AtomicLong extensionImplCount = new AtomicLong(0);

  public ExtensionMetricsCollector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder("bone.extension.points.count", extensionPointCount, AtomicLong::get)
        .description("Number of extension points")
        .register(meterRegistry);
    Gauge.builder("bone.extension.implementations.count", extensionImplCount, AtomicLong::get)
        .description("Number of extension implementations")
        .register(meterRegistry);
    log.info("ExtensionMetricsCollector initialized (RED extension_invoke_*)");
  }

  public void recordInvoke(String extPoint, String impl, String status, long durationMs) {
    String point = sanitize(extPoint);
    String implementation = sanitize(impl);
    String outcome = sanitize(status);
    meterRegistry
        .counter(
            "extension_invoke_total", "ext_point", point, "impl", implementation, "status", outcome)
        .increment();
    meterRegistry
        .timer("extension_invoke_duration_seconds", "ext_point", point, "impl", implementation)
        .record(durationMs, TimeUnit.MILLISECONDS);
  }

  public void recordRouterNoMatch(String extPoint) {
    meterRegistry
        .counter("extension_router_no_match_total", "ext_point", sanitize(extPoint))
        .increment();
  }

  public void recordBulkheadRejected(String bulkheadKey) {
    meterRegistry
        .counter("extension_bulkhead_rejected_total", "bulkhead", sanitize(bulkheadKey))
        .increment();
  }

  public void updateExtensionPointCount(long count) {
    extensionPointCount.set(count);
  }

  public void updateExtensionImplCount(long count) {
    extensionImplCount.set(count);
  }

  public boolean isSlow(long durationMs) {
    return durationMs >= SLOW_THRESHOLD_MS;
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.length() > 120 ? value.substring(0, 120) : value;
  }
}
