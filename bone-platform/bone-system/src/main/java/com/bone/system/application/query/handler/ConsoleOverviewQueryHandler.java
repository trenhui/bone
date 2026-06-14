package com.bone.system.application.query.handler;

import com.bone.system.domain.gateway.KeyMetricsGateway;
import com.bone.system.domain.gateway.ResourceUsageGateway;
import com.bone.system.domain.gateway.ServiceHealthGateway;
import com.bone.system.domain.model.console.ConsoleOverview;
import com.bone.system.domain.model.console.KeyMetrics;
import com.bone.system.domain.model.console.ResourceUsage;
import com.bone.system.domain.model.console.ServiceStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 控制台概览聚合（{@code GET /api/v1/console/overview}）。
 *
 * <p>编排三类 Gateway —— ServiceHealth / ResourceUsage / KeyMetrics，组装为单一读侧值对象。 Micrometer 计数器 {@code
 * bone_console_overview_refresh_total} 与 PromQL 真源 {@code Bone-可观测性规范.md} §4.2.1 对齐。
 */
@Component
@RequiredArgsConstructor
public class ConsoleOverviewQueryHandler {

  private final ServiceHealthGateway serviceHealthGateway;
  private final ResourceUsageGateway resourceUsageGateway;
  private final KeyMetricsGateway keyMetricsGateway;
  private final MeterRegistry meterRegistry;

  @Transactional(readOnly = true)
  public ConsoleOverview handle() {
    meterRegistry.counter("bone_console_overview_refresh_total").increment();
    List<ServiceStatus> services = serviceHealthGateway.listServiceStatuses();
    ResourceUsage usage = resourceUsageGateway.snapshot();
    KeyMetrics metrics = keyMetricsGateway.collect();
    return ConsoleOverview.builder()
        .services(services)
        .resourceUsage(usage)
        .keyMetrics(metrics)
        .alerts(List.of())
        .updatedAt(Instant.now())
        .build();
  }

  @Transactional(readOnly = true)
  public List<ServiceStatus> handleServices() {
    return serviceHealthGateway.listServiceStatuses();
  }

  @Transactional(readOnly = true)
  public ResourceUsage handleResources() {
    return resourceUsageGateway.snapshot();
  }

  @Transactional(readOnly = true)
  public KeyMetrics handleMetrics() {
    return keyMetricsGateway.collect();
  }
}
