package com.bone.system.infrastructure.gateway;

import com.bone.system.domain.gateway.ResourceUsageGateway;
import com.bone.system.domain.model.console.ResourceUsage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JVM 维度资源使用网关：从 Micrometer 取 {@code jvm.memory.*} gauge。
 *
 * <p>缺指标时返回 0，避免 Controller 调用面崩溃。
 */
@Component
@RequiredArgsConstructor
public class JvmResourceUsageGateway implements ResourceUsageGateway {

  private final MeterRegistry meterRegistry;

  @Override
  public ResourceUsage snapshot() {
    return ResourceUsage.builder()
        .memoryUsedBytes((long) gauge("jvm.memory.used", 0))
        .memoryMaxBytes((long) gauge("jvm.memory.max", 0))
        .cpuPercent(0)
        .diskUsedPercent(0)
        .updatedAt(Instant.now())
        .build();
  }

  private double gauge(String name, double defaultValue) {
    try {
      return meterRegistry.get(name).gauge().value();
    } catch (Exception e) {
      return defaultValue;
    }
  }
}
