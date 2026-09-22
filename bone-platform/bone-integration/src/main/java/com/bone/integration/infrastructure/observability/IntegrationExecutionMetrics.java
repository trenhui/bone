package com.bone.integration.infrastructure.observability;

import com.bone.integration.application.port.IntegrationExecutionRecorder;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.valueobject.ExecutionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 集成业务指标（对齐 Bone-可观测性规范 §4.2）。 */
@Component
@RequiredArgsConstructor
public class IntegrationExecutionMetrics implements IntegrationExecutionRecorder {

  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<Long, AtomicLong> deadLetterByTenant = new ConcurrentHashMap<>();

  public void record(IntegrationLog log) {
    ExecutionStatus status = log.getStatus();
    if (status == null || status == ExecutionStatus.PENDING || status == ExecutionStatus.RUNNING) {
      return;
    }
    String statusTag = status == ExecutionStatus.SUCCESS ? "success" : "failed";
    meterRegistry.counter("bone_integration_execution_total", "status", statusTag).increment();
    if (log.getStartedAt() != null && log.getEndedAt() != null) {
      Duration duration = Duration.between(log.getStartedAt(), log.getEndedAt());
      meterRegistry
          .timer(
              "bone_integration_execution_duration_seconds",
              "flow_id",
              String.valueOf(log.getFlowId()))
          .record(duration);
    }
  }

  public void recordConnectorTest(String connectorType, boolean success) {
    String type = connectorType != null ? connectorType : "UNKNOWN";
    String result = success ? "success" : "failed";
    meterRegistry
        .counter("bone_integration_connector_test_total", "connector_type", type, "result", result)
        .increment();
  }

  public void refreshDeadLetterGauges(Map<Long, Long> pendingByTenant) {
    pendingByTenant.forEach(
        (tenantId, count) ->
            deadLetterByTenant.computeIfAbsent(tenantId, this::registerDeadLetterGauge).set(count));
    deadLetterByTenant.keySet().stream()
        .filter(tenantId -> !pendingByTenant.containsKey(tenantId))
        .forEach(tenantId -> deadLetterByTenant.get(tenantId).set(0L));
    if (pendingByTenant.isEmpty() && deadLetterByTenant.isEmpty()) {
      deadLetterByTenant.computeIfAbsent(0L, this::registerDeadLetterGauge).set(0L);
    }
  }

  private AtomicLong registerDeadLetterGauge(Long tenantId) {
    AtomicLong holder = new AtomicLong(0L);
    meterRegistry.gauge(
        "bone_integration_dead_letter_gauge",
        Tags.of("tenant_id", String.valueOf(tenantId)),
        holder,
        AtomicLong::get);
    return holder;
  }
}
