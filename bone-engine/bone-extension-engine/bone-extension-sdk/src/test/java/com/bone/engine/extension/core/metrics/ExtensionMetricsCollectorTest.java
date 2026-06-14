package com.bone.engine.extension.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtensionMetricsCollectorTest {

  @Test
  @DisplayName("注册 extension_invoke_* 与 router/bulkhead 指标")
  void recordsRedMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ExtensionMetricsCollector collector = new ExtensionMetricsCollector(registry);

    collector.recordInvoke("com.bone.PricingExt", "PromoImpl", "success", 12);
    collector.recordRouterNoMatch("com.bone.PricingExt");
    collector.recordBulkheadRejected("PromoImpl");

    assertEquals(
        1.0,
        registry
            .get("extension_invoke_total")
            .tag("ext_point", "com.bone.PricingExt")
            .tag("impl", "PromoImpl")
            .tag("status", "success")
            .counter()
            .count());
    assertEquals(
        1.0,
        registry
            .get("extension_router_no_match_total")
            .tag("ext_point", "com.bone.PricingExt")
            .counter()
            .count());
    assertEquals(
        1.0,
        registry
            .get("extension_bulkhead_rejected_total")
            .tag("bulkhead", "PromoImpl")
            .counter()
            .count());
  }
}
