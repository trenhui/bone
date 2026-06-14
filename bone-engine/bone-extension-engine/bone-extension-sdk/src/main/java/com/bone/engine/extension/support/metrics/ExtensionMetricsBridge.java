package com.bone.engine.extension.support.metrics;

import com.bone.engine.extension.core.metrics.ExtensionMetricsCollector;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;

/** 为非 Spring 管理的调用链（代理/路由器）提供可选指标桥接。 */
public class ExtensionMetricsBridge {

  private static volatile ExtensionMetricsCollector collector;

  public ExtensionMetricsBridge(ObjectProvider<ExtensionMetricsCollector> provider) {
    provider.ifAvailable(c -> collector = c);
  }

  public static Optional<ExtensionMetricsCollector> optional() {
    return Optional.ofNullable(collector);
  }
}
